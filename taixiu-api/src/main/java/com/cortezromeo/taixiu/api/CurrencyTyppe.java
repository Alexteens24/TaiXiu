package com.cortezromeo.taixiu.api;

@Deprecated(forRemoval = false)
public enum CurrencyTyppe {
    VAULT, PLAYERPOINTS;

    public CurrencyType toCurrencyType() {
        return CurrencyType.valueOf(name());
    }
}
