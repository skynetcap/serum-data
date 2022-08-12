package com.mmorrell.serumdata.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.p2p.solanaj.core.PublicKey;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@Slf4j
public class MangoClient {

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    public MangoClient(final OkHttpClient httpClient, final ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public Optional<PublicKey> getMangoAccountFromOoa(PublicKey ooa) {
        String ooaString = ooa.toBase58();
        RequestBody body;
        try {
            body = RequestBody.create(
                    objectMapper.writeValueAsString(Map.of("open_orders_accounts", List.of(ooaString))),
                    JSON
            );
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }

        Request request = new Request.Builder()
                .url("https://mango-transaction-log.herokuapp.com/v3/user-data/mango-accounts-from-open-orders")
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            byte[] data = responseBody.bytes();
            Map<String, String> resultMap = objectMapper.readValue(data, new TypeReference<>() {
            });

            if (resultMap.containsKey(ooaString)) {
                return Optional.of(new PublicKey(resultMap.get(ooaString)));
            }
        } catch (Exception ex) {
            // Case: HTTP exception
            log.error(ex.getMessage());
        }

        return Optional.empty();
    }
}
