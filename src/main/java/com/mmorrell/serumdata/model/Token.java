package com.mmorrell.serumdata.model;

import java.io.Serializable;

public class Token implements Serializable {

    public Token(String name, String address, String symbol) {
        this.name = name;
        this.address = address;
        this.symbol = symbol;
    }

    private String name;
    private String address;
    private String symbol;

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    @Override
    public String toString() {
        return "Token{" +
                "name='" + name + '\'' +
                ", address='" + address + '\'' +
                ", symbol='" + symbol + '\'' +
                '}';
    }
}
