package com.mmorrell.serumdata.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.mmorrell.serumdata.util.PublicKeySerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.p2p.solanaj.core.PublicKey;

@Data
@AllArgsConstructor
public class MarketListing {
    private String name;
    @JsonSerialize(using = PublicKeySerializer.class)
    private PublicKey id;
    private long quoteDepositsTotal;
    private float quoteNotional;
    private int baseDecimals;
    private int quoteDecimals;
    @JsonSerialize(using = PublicKeySerializer.class)
    private PublicKey baseMint;
}
