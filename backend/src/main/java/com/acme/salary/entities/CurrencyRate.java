package com.acme.salary.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "currency_rates")
@Getter
@Setter
public class CurrencyRate {

    @Id
    @Column(length = 3)
    private String code;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(name = "usd_rate", nullable = false, precision = 12, scale = 6)
    private BigDecimal usdRate;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
