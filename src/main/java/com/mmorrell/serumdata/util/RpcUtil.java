package com.mmorrell.serumdata.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RpcUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(RpcUtil.class);
    private static final PublicCluster DEFAULT_CLUSTER = PublicCluster.GENESYSGO;
    private static final String CUSTOM_ENDPOINT = System.getenv("OPENSERUM_ENDPOINT");

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
}
