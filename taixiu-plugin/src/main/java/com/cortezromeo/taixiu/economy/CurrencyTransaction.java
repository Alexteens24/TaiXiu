package com.cortezromeo.taixiu.economy;

public record CurrencyTransaction(boolean successful, String error) {
    public static CurrencyTransaction success() { return new CurrencyTransaction(true, ""); }
    public static CurrencyTransaction failure(String error) { return new CurrencyTransaction(false, error == null ? "unknown error" : error); }
}
