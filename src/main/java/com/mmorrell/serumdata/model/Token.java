package com.mmorrell.serumdata.model;

import java.io.Serializable;

public class Token implements Serializable {

    public Token(String name, String address, String symbol, String logoURI) {
        this.name = name;
        this.address = address;
        this.symbol = symbol;
        this.logoURI = logoURI;
    }

    private String name;
    private String address;
    private String symbol;
    private String logoURI;

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

    public String getLogoURI() {
        return logoURI;
    }

    public void setLogoURI(String logoURI) {
        this.logoURI = logoURI;
    }

    @Override
    public String toString() {
        return "Token{" +
                "name='" + name + '\'' +
                ", address='" + address + '\'' +
                ", symbol='" + symbol + '\'' +
                ", logoURI='" + logoURI + '\'' +
                '}';
    }
}
