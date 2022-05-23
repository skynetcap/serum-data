package com.mmorrell.serumdata.model;

// For easy serialization of Order class, without baggage
public class SerumOrder {

    private float price;
    private float quantity;
    private String owner;

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
}
