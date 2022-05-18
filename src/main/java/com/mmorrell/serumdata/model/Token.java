package com.mmorrell.serumdata.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

public class Token {

    public Token(String name, String address) {
        this.name = name;
        this.address = address;
    }

    private String name;
    private String address;

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
                '}';
    }
}
