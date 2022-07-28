package com.mmorrell.serumdata.manager;

import ch.openserum.serum.model.Market;
import ch.openserum.serum.model.SerumUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.mmorrell.serumdata.model.Token;
import com.mmorrell.serumdata.util.MarketUtil;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.p2p.solanaj.core.PublicKey;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;


/**
 * Caches the Solana token registry in-memory
 */
@Component
@Slf4j
public class TokenManager {
    private static final int CHAIN_ID_MAINNET = 101;

    private final OkHttpClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // <tokenMint string, token>
    private final Map<PublicKey, Token> tokenCache = new HashMap<>();
    private final Map<PublicKey, ByteBuffer> tokenImageCache = new ConcurrentHashMap<>();
    private byte[] placeHolderImage;

    // Loads tokens from github repo into memory when this constructor is called. (e.g. during Bean creation)
    public TokenManager(final OkHttpClient client) {
        this.client = client;
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        cachePlaceHolderImage();
        updateRegistry();
    }

    public void updateRegistry() {
        log.info("Caching tokens from solana.tokenlist.json");
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
            PublicKey tokenMint = PublicKey.valueOf(tokenNode.get("address").textValue());
            String logoURI = tokenNode.get("logoURI").textValue();
            Token token = Token.builder()
                    .name(tokenNode.get("name").textValue())
                    .publicKey(tokenMint)
                    .address(tokenNode.get("address").textValue())
                    .symbol(tokenNode.get("symbol").textValue())
                    .logoURI(logoURI)
                    .chainId(tokenNode.get("chainId").intValue())
                    .decimals(tokenNode.get("decimals").intValue())
                    .imageFormat(getImageFormat(logoURI))
                    .build();

            // update cache, only mainnet tokens
            if (token.getChainId() == CHAIN_ID_MAINNET) {
                cacheTokenImage(tokenMint, logoURI);

                tokenCache.put(
                        token.getPublicKey(),
                        token
                );
            }
        }

        log.info("Tokens cached.");
    }

    private void cacheTokenImage(PublicKey tokenMint, String logoURI) {
        Request request = new Request.Builder()
                .url(logoURI)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                // e.printStackTrace();
            }

            @Override public void onResponse(Call call, Response response) throws IOException {
                try (ResponseBody responseBody = response.body()) {
                    if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

                    Headers responseHeaders = response.headers();
                    for (int i = 0, size = responseHeaders.size(); i < size; i++) {
                        System.out.println(responseHeaders.name(i) + ": " + responseHeaders.value(i));
                    }

                    tokenImageCache.put(tokenMint, ByteBuffer.wrap(responseBody.bytes()));
                }
            }
        });
    }

    // Used for formatting media type
    private String getImageFormat(String logoURI) {
        if (logoURI.endsWith(".jpg")) {
            return "jpg";
        } else if (logoURI.endsWith(".png")) {
            return "png";
        } else if (logoURI.endsWith(".svg")) {
            return "svg";
        } else if (logoURI.endsWith(".gif")) {
            return "gif";
        } else {
            return "png";
        }
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
                "%s - %s",
                getTokenSymbolByMint(market.getBaseMint()),
                getTokenSymbolByMint(market.getQuoteMint())
        );
    }

    /**
     * Finds all tokens with the given symbol.
     * In case of duplicate symbol entries as with tokenlist.json, although that is being deprecated.
     *
     * @param tokenSymbol symbol e.g. SRM
     * @return list of possible tokens
     */
    public List<Token> getTokensBySymbol(String tokenSymbol) {
        String symbol = tokenSymbol.toUpperCase();
        if (symbol.equalsIgnoreCase("USDC")) {
            return List.of(getTokenByMint(MarketUtil.USDC_MINT).get());
        } else if (symbol.equalsIgnoreCase("USDT")) {
            return List.of(getTokenByMint(MarketUtil.USDT_MINT).get());
        } else if (symbol.equalsIgnoreCase("SOL")) {
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

    // On startup
    private void cachePlaceHolderImage() {
        try {
            this.placeHolderImage = Resources.toByteArray(Resources.getResource("static/entities/unknown.jpg"));
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    // Additional fallback for JSP front-end
    public byte[] getPlaceHolderImage() {
        return placeHolderImage;
    }

    public InputStreamResource getTokenImageInputStream(Token token) {
        return new InputStreamResource(new ByteArrayInputStream(token.getIconImage()));
    }
}
