package com.cortezromeo.taixiu.storage;

public record InsuranceSettings(boolean enabled, int lossesRequired, double refundPercent,
                                long maxRefundPer24Hours) { }
