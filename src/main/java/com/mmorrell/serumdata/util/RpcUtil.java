package com.mmorrell.serumdata.util;

public class RpcUtil {

    private static final PublicCluster DEFAULT_CLUSTER = PublicCluster.GENESYSGO;

    private enum PublicCluster {
        GENESYSGO ("https://ssc-dao.genesysgo.net/");

        private final String endpoint;

        PublicCluster(String endpoint) {
            this.endpoint = endpoint;
        }

        String getEndpoint() {
            return endpoint;
        }
    }

    public static String getPublicEndpoint() {
        return DEFAULT_CLUSTER.getEndpoint();
    }
}
