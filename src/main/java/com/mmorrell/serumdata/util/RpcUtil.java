package com.mmorrell.serumdata.util;

import okhttp3.Request;
import org.jetbrains.annotations.NotNull;
import org.p2p.solanaj.core.PublicKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RpcUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(RpcUtil.class);
    private static final PublicCluster DEFAULT_CLUSTER = PublicCluster.PROJECT_SERUM;
    private static final String CUSTOM_ENDPOINT = System.getenv("OPENSERUM_ENDPOINT");
    public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/103.0.0.0 Safari/537.36";

    private enum PublicCluster {
        GENESYSGO ("https://ssc-dao.genesysgo.net/"),
        PROJECT_SERUM("https://solana-api.projectserum.com/");

        private final String endpoint;

        PublicCluster(String endpoint) {
            this.endpoint = endpoint;
        }

        String getEndpoint() {
            return endpoint;
        }
    }

    public static String getPublicEndpoint() {
        if (CUSTOM_ENDPOINT != null) {
            try {
                PublicCluster cluster = PublicCluster.valueOf(CUSTOM_ENDPOINT);
                LOGGER.info("Using known endpoint: " + cluster.name() + " (" + cluster.getEndpoint() + ")");
                return cluster.getEndpoint();
            } catch (IllegalArgumentException ex) {
                LOGGER.info("Using custom endpoint: " + CUSTOM_ENDPOINT);
                return CUSTOM_ENDPOINT;
            }
        }

        LOGGER.info("Using fallback endpoint: " + DEFAULT_CLUSTER.getEndpoint());
        return DEFAULT_CLUSTER.getEndpoint();
    }


    @NotNull
    public static Request buildGetAccountInfoSerumDbRequest(PublicKey publicKey) {
        return new Request.Builder()
                .url("http://host.docker.internal:8082/serum/account/" + publicKey.toBase58())
                .build();
    }
}
