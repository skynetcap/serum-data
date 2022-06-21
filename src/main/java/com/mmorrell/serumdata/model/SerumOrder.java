package com.mmorrell.serumdata.model;

import java.util.HashMap;
import java.util.Map;

// For easy serialization of Order class, without baggage
public class SerumOrder {

    private float price;
    private float quantity;
    private String owner;
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

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
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
