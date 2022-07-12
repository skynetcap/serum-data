package com.mmorrell.serumdata.manager;

import ch.openserum.serum.model.Market;
import ch.openserum.serum.model.SerumUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmorrell.serumdata.model.Token;
import com.mmorrell.serumdata.util.MarketUtil;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.p2p.solanaj.core.PublicKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;


/**
 * Caches the Solana token registry in-memory
 */
@Component
public class TokenManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(TokenManager.class);

    private static final int CHAIN_ID_MAINNET = 101;

    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // <tokenMint string, token>
    private final Map<PublicKey, Token> tokenCache = new HashMap<>();

    // Loads tokens from github repo into memory when this constructor is called. (e.g. during Bean creation)
    public TokenManager() {
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,false);
        updateRegistry();
    }

    public void updateRegistry() {
        LOGGER.info("Caching tokens from solana.tokenlist.json");
        String json = httpGet("https://raw.githubusercontent.com/solana-labs/token-list/main/src/tokens/solana.tokenlist.json");

        JsonNode rootNode;
        try {
            rootNode = objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        JsonNode tokensNode = rootNode.path("tokens");
        Iterator<JsonNode> elements = tokensNode.elements();
        while (elements.hasNext()) {
            JsonNode tokenNode = elements.next();
            Token token = Token.builder()
                    .name(tokenNode.get("name").textValue())
                    .publicKey(PublicKey.valueOf(tokenNode.get("address").textValue()))
                    .address(tokenNode.get("address").textValue())
                    .symbol(tokenNode.get("symbol").textValue())
                    .logoURI(tokenNode.get("logoURI").textValue())
                    .chainId(tokenNode.get("chainId").intValue())
                    .decimals(tokenNode.get("decimals").intValue())
                    .build();

            // update cache, only mainnet tokens
            if (token.getChainId() == CHAIN_ID_MAINNET) {
                tokenCache.put(
                        token.getPublicKey(),
                        token
                );
            }
        }

        LOGGER.info("Tokens cached.");
    }

    public Map<PublicKey, Token> getRegistry() {
        return tokenCache;
    }

    private String httpGet(String url) {
        Request request = new Request.Builder().url(url).build();

        try (Response response = client.newCall(request).execute()) {
            return response.body().string();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Optional<Token> getTokenByMint(PublicKey tokenMint) {
        return Optional.ofNullable(tokenCache.get(tokenMint));
    }

    public String getTokenNameByMint(PublicKey tokenMint) {
        Token token = tokenCache.get(tokenMint);
        if (token != null) {
            return token.getName();
        } else {
            return "";
        }
    }

    public String getTokenSymbolByMint(PublicKey tokenMint) {
        Token token = tokenCache.get(tokenMint);
        if (token != null) {
            return token.getSymbol();
        } else {
            return "";
        }
    }

    public String getTokenLogoByMint(PublicKey tokenMint) {
        Token token = tokenCache.get(tokenMint);
        if (token != null) {
            return token.getLogoURI();
        } else {
            return "";
        }
    }

    public String getMarketNameByMarket(Market market) {
        // result = "SOL / USDC"
        return String.format(
                "%s / %s",
                getTokenSymbolByMint(market.getBaseMint()),
                getTokenSymbolByMint(market.getQuoteMint())
        );
    }

    /**
     * Finds all tokens with the given symbol.
     * In case of duplicate symbol entries as with tokenlist.json, although that is being deprecated.
     * @param tokenSymbol symbol e.g. SRM
     * @return list of possible tokens
     */
    public List<Token> getTokensBySymbol(String tokenSymbol) {
        String symbol = tokenSymbol.toUpperCase();
        if (symbol.equalsIgnoreCase("USDC")) {
            return List.of(getTokenByMint(MarketUtil.USDC_MINT).get());
        } else if (symbol.equalsIgnoreCase("USDT")) {
            return List.of(getTokenByMint(MarketUtil.USDT_MINT).get());
        }  else if (symbol.equalsIgnoreCase("SOL")) {
            return List.of(getTokenByMint(SerumUtils.WRAPPED_SOL_MINT).get());
        } else {
            // return symbol if we have it
            return tokenCache.values().stream()
                    .filter(token -> token.getSymbol().equalsIgnoreCase(tokenSymbol))
                    .collect(Collectors.toList());
        }
    }

    public int getDecimals(PublicKey tokenMint) {
        final Optional<Token> token = Optional.ofNullable(tokenCache.get(tokenMint));
        return token.map(Token::getDecimals).orElse(9);
    }
}
