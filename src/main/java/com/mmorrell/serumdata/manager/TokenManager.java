package com.mmorrell.serumdata.manager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmorrell.serumdata.model.Token;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;


/**
 * Caches the Solana token registry in-memory
 */
@Component
public class TokenManager {

    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // <tokenMint string, token>
    private final Map<String, Token> tokenCache = new HashMap<>();

    // Loads tokens from github repo into memory when this constructor is called. (e.g. during Bean creation)
    public TokenManager() {
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,false);
        updateRegistry();
    }

    public void updateRegistry() {
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
            Token token = new Token(
                    tokenNode.get("name").textValue(),
                    tokenNode.get("address").textValue(),
                    tokenNode.get("symbol").textValue(),
                    tokenNode.get("logoURI").textValue()
            );

            // update cache
            tokenCache.put(token.getAddress(), token);
        }
        System.out.println("Cached tokens.");
    }

    public Map<String, Token> getRegistry() {
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

    public String getTokenByMint(String tokenMint) {
        Token token = tokenCache.get(tokenMint);
        if (token != null) {
            return token.getName();
        } else {
            return "";
        }
    }

    public String getTokenSymbolByMint(String tokenMint) {
        Token token = tokenCache.get(tokenMint);
        if (token != null) {
            return token.getSymbol();
        } else {
            return "";
        }
    }

    public String getTokenLogoByMint(String tokenMint) {
        Token token = tokenCache.get(tokenMint);
        if (token != null) {
            return token.getLogoURI();
        } else {
            return "";
        }
    }
}
