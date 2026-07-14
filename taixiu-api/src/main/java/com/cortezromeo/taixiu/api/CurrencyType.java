package com.cortezromeo.taixiu.api;

public enum CurrencyType {
    VAULT,
    PLAYERPOINTS;

    @SuppressWarnings("deprecation")
    public CurrencyTyppe toLegacyType() {
        return CurrencyTyppe.valueOf(name());
    }
}
