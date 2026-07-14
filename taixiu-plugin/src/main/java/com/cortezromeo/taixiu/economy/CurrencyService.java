package com.cortezromeo.taixiu.economy;

import com.cortezromeo.taixiu.api.CurrencyTyppe;

import java.util.EnumMap;
import java.util.Map;

@SuppressWarnings("deprecation")
public final class CurrencyService {
    private final Map<CurrencyTyppe, CurrencyGateway> gateways = new EnumMap<>(CurrencyTyppe.class);

    public void register(CurrencyTyppe type, CurrencyGateway gateway) { gateways.put(type, gateway); }
    public void unregister(CurrencyTyppe type) { gateways.remove(type); }
    public boolean supports(CurrencyTyppe type) { return gateways.containsKey(type); }
    public CurrencyGateway gateway(CurrencyTyppe type) {
        CurrencyGateway gateway = gateways.get(type);
        if (gateway == null) throw new IllegalStateException("Currency provider is unavailable: " + type);
        return gateway;
    }
}
