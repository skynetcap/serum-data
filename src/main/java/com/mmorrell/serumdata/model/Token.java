package com.mmorrell.serumdata.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.mmorrell.serumdata.util.PublicKeySerializer;
import lombok.*;
import org.p2p.solanaj.core.PublicKey;

@Data
@AllArgsConstructor
@Builder
public class Token {
    private String name;
    private String address;
    private String symbol;
    private String logoURI;
    private int chainId;
    private int decimals;
    @JsonSerialize(using = PublicKeySerializer.class)
    private PublicKey publicKey;

    // jpg, png, svg
    private String imageFormat;
}
