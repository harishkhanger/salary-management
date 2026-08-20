package com.acme.salary.dto.response;

import com.acme.salary.entities.CurrencyRate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CurrencyRateResponse(String code, String name, BigDecimal usdRate,
                                   LocalDateTime updatedAt) {

    public static CurrencyRateResponse from(CurrencyRate rate) {
        return new CurrencyRateResponse(rate.getCode(), rate.getName(), rate.getUsdRate(),
                rate.getUpdatedAt());
    }
}
