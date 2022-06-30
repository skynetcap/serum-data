package com.mmorrell.serumdata.model;

import org.p2p.solanaj.core.PublicKey;

import java.io.Serializable;

public class Token implements Serializable {

    public Token(PublicKey publicKey) {
        this.address = publicKey.toBase58();
        this.publicKey = publicKey;
    }

    public Token(String address) {
        this.address = address;
        this.publicKey = PublicKey.valueOf(address);
    }

    public Token(String name, String address, String symbol, String logoURI, int chainId, int decimals) {
        this.name = name;
        this.address = address;
        this.publicKey = PublicKey.valueOf(address);
        this.symbol = symbol;
        this.logoURI = logoURI;
        this.chainId = chainId;
        this.decimals = decimals;
    }

    private String name;
    private String address;
    private String symbol;
    private String logoURI;
    private int chainId;
    private int decimals;
    private PublicKey publicKey;

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(PublicKey publicKey) {
        this.publicKey = publicKey;
    }

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

    public int getChainId() {
        return chainId;
    }

    public void setChainId(int chainId) {
        this.chainId = chainId;
    }

    public int getDecimals() {
        return decimals;
    }

    public void setDecimals(int decimals) {
        this.decimals = decimals;
    }
}
