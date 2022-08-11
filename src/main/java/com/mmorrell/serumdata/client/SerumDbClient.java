package com.mmorrell.serumdata.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.p2p.solanaj.core.PublicKey;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.util.*;

@Component
@Slf4j
public class SerumDbClient {

    private final OkHttpClient httpClient;
    private static final String SERUM_DB_ENDPOINT = "http://host.docker.internal:8082";

    private final ObjectMapper objectMapper;
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    public SerumDbClient(final OkHttpClient httpClient, final ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public Map<PublicKey, ByteBuffer> getMultipleAccounts(final List<PublicKey> publicKeys) {
        // convert pubkeys to strings, for objectmapper serialization w/o custom handling
        List<String> publicKeyStrings = publicKeys.stream()
                .map(PublicKey::toBase58)
                .toList();

        RequestBody body;
        try {
            body = RequestBody.create(
                    objectMapper.writeValueAsString(publicKeyStrings),
                    JSON
            );
        } catch (JsonProcessingException e) {
            return Collections.emptyMap();
        }

        Request request = new Request.Builder()
                .url(String.format("%s/serum/accounts", SERUM_DB_ENDPOINT))
                .post(body)
                .build();

        final Map<PublicKey, ByteBuffer> result = new HashMap<>(publicKeys.size());
        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            byte[] data = responseBody.bytes();

            List<AccountInfoRow> rows = objectMapper.readValue(data, new TypeReference<>() {
            });

            for (AccountInfoRow accountInfoRow : rows) {
                byte[] accountData = accountInfoRow.getDecodedData();
                if (accountData.length > 0) {
                    // store it
                    result.put(
                            accountInfoRow.getDecodedPublicKey(),
                            ByteBuffer.wrap(accountData)
                    );
                }
            }

            return result;
        } catch (Exception ex) {
            // Case: HTTP exception
            log.error(ex.getMessage());
        }

        return Collections.emptyMap();
    }

    public List<AccountInfoRow> getAllMarkets() {
        Request request = new Request.Builder()
                .url(String.format("%s/serum/markets", SERUM_DB_ENDPOINT))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            byte[] data = responseBody.bytes();

            return objectMapper.readValue(data, new TypeReference<>() {
            });
        } catch (Exception ex) {
            // Case: HTTP exception
            log.error(ex.getMessage());
        }

        return Collections.emptyList();
    }

    @NotNull
    public static Request buildGetAccountInfoSerumDbRequest(PublicKey publicKey) {
        return new Request.Builder()
                .url(String.format("%s/serum/account/%s", SERUM_DB_ENDPOINT, publicKey.toBase58()))
                .build();
    }

}
