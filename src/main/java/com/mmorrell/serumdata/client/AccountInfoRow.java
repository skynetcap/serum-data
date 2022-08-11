package com.mmorrell.serumdata.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AccountInfoRow {

    private String publicKey;
    private String data;
    private long slot;

}
