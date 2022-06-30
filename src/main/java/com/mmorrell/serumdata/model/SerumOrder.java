package com.mmorrell.serumdata.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.mmorrell.serumdata.util.PublicKeySerializer;
import org.p2p.solanaj.core.PublicKey;

import java.util.HashMap;
import java.util.Map;

// For easy serialization of Order class, without baggage
public class SerumOrder {

    private float price;
    private float quantity;

    @JsonSerialize(using = PublicKeySerializer.class)
    private PublicKey owner;

    // Possible keys: "entityName", "entityIcon" (for the known entity)
    private final Map<String, String> metadata = new HashMap<>();

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

    public void addMetadata(String key, String value) {
        metadata.put(key, value);
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public String getMetadataValue(String key) {
        return metadata.get(key);
    }
}
