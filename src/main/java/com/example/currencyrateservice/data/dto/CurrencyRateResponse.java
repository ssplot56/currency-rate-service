package com.example.currencyrateservice.data.dto;

import java.util.List;

public record CurrencyRateResponse(List<CurrencyRateItem> fiat, List<CurrencyRateItem> crypto) {
}
