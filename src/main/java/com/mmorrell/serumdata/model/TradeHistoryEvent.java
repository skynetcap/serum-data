package com.mmorrell.serumdata.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.mmorrell.serumdata.util.PublicKeySerializer;
import lombok.Builder;
import lombok.Data;
import org.p2p.solanaj.core.PublicKey;

@Data
@Builder
public class TradeHistoryEvent {
    private int index;
    private float price;
    private float quantity;

    @JsonSerialize(using = PublicKeySerializer.class)
    private PublicKey owner;

    // Known entities
    private String entityName;
    private String entityIcon;

    // Flags
    private boolean fill;
    private boolean out;
    private boolean bid;
    private boolean maker;

    // TX metadata/forensics
    private String jupiterTx;
}
