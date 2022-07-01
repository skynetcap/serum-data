package com.mmorrell.serumdata.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.mmorrell.serumdata.util.PublicKeySerializer;
import org.p2p.solanaj.core.PublicKey;

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

    public TradeHistoryEvent(int index, float price, float quantity, PublicKey owner) {
        this.index = index;
        this.price = price;
        this.quantity = quantity;
        this.owner = owner;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public float getPrice() {
        return price;
    }

    public void setPrice(float price) {
        this.price = price;
    }

    public float getQuantity() {
        return quantity;
    }

    public void setQuantity(float quantity) {
        this.quantity = quantity;
    }

    public PublicKey getOwner() {
        return owner;
    }

    public void setOwner(PublicKey owner) {
        this.owner = owner;
    }

    public String getEntityName() {
        return entityName;
    }

    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }

    public String getEntityIcon() {
        return entityIcon;
    }

    public void setEntityIcon(String entityIcon) {
        this.entityIcon = entityIcon;
    }

    public boolean isFill() {
        return fill;
    }

    public void setFill(boolean fill) {
        this.fill = fill;
    }

    public boolean isOut() {
        return out;
    }

    public void setOut(boolean out) {
        this.out = out;
    }

    public boolean isBid() {
        return bid;
    }

    public void setBid(boolean bid) {
        this.bid = bid;
    }

    public boolean isMaker() {
        return maker;
    }

    public void setMaker(boolean maker) {
        this.maker = maker;
    }

    public String getJupiterTx() {
        return jupiterTx;
    }

    public void setJupiterTx(String jupiterTx) {
        this.jupiterTx = jupiterTx;
    }
}
