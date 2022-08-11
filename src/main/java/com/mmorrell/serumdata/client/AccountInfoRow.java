package com.mmorrell.serumdata.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Base64;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AccountInfoRow {

    private String publicKey;
    private String data;
    private long slot;

    public byte[] getDecodedData() {
        return Base64.getDecoder().decode(data);
    }

}
