package com.mmorrell.serumdata.util;

import okhttp3.Request;
import org.jetbrains.annotations.NotNull;
import org.p2p.solanaj.core.PublicKey;

public class ClientUtil {

    @NotNull
    public static Request buildGetAccountInfoSerumDbRequest(PublicKey publicKey) {
        return new Request.Builder()
                .url("http://host.docker.internal:8082/serum/account/" + publicKey.toBase58())
                .build();
    }

}
