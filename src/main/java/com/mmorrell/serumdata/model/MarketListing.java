package com.mmorrell.serumdata.model;

import org.p2p.solanaj.core.PublicKey;

public class MarketListing {

    private String name;
    private PublicKey id;
    private long quoteDepositsTotal;
    private float quoteNotional;
    private int baseDecimals;
    private int quoteDecimals;

    public MarketListing(
            String name,
            PublicKey id,
            long quoteDepositsTotal,
            float quoteNotional,
            int baseDecimals,
            int quoteDecimals
    ) {
        this.name = name;
        this.id = id;
        this.quoteDepositsTotal = quoteDepositsTotal;
        this.quoteNotional = quoteNotional;
        this.baseDecimals = baseDecimals;
        this.quoteDecimals = quoteDecimals;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public PublicKey getId() {
        return id;
    }

    public void setId(PublicKey id) {
        this.id = id;
    }

    public long getQuoteDepositsTotal() {
        return quoteDepositsTotal;
    }

    public void setQuoteDepositsTotal(long quoteDepositsTotal) {
        this.quoteDepositsTotal = quoteDepositsTotal;
    }

    public float getQuoteNotional() {
        return quoteNotional;
    }

    public void setQuoteNotional(float quoteNotional) {
        this.quoteNotional = quoteNotional;
    }

    public int getQuoteDecimals() {
        return quoteDecimals;
    }

    public void setQuoteDecimals(int quoteDecimals) {
        this.quoteDecimals = quoteDecimals;
    }

    public int getBaseDecimals() {
        return baseDecimals;
    }

    public void setBaseDecimals(int baseDecimals) {
        this.baseDecimals = baseDecimals;
    }
}
