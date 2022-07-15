package com.mmorrell.serumdata.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MarketDepth {

    private float[][] bids, asks;
    private float midpoint;
}
