package com.example.currencyrateservice.data.dto;

import java.math.BigDecimal;

public record CryptoCurrencyRateDTO(String name, BigDecimal value) {
}
