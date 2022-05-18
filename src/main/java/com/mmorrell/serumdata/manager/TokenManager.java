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

@Component
public class TokenManager {

    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // <tokenMint string, token>
    private final Map<String, Token> tokenCache = new HashMap<>();

    public TokenManager() {
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,false);
    }

    public List<Token> getRegistry() {
        String json = httpGet("https://raw.githubusercontent.com/solana-labs/token-list/main/src/tokens/solana.tokenlist.json");

        JsonNode rootNode;
        try {
            rootNode = objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        JsonNode tokensNode = rootNode.path("tokens");
        Iterator<JsonNode> elements = tokensNode.elements();
        List<Token> tokens = new ArrayList<>();
        while (elements.hasNext()) {
            JsonNode tokenNode = elements.next();
            Token token = new Token(
                    tokenNode.get("name").textValue(),
                    tokenNode.get("address").textValue()
            );
            tokens.add(token);

            // update cache
            tokenCache.put(token.getAddress(), token);
        }

        return tokens;
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

}
