package com.mmorrell.serumdata.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.mmorrell.serumdata.util.PublicKeySerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.p2p.solanaj.core.PublicKey;

import java.io.Serializable;

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
}
