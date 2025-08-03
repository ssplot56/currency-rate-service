package com.example.currencyrateservice.service;

import com.example.currencyrateservice.data.dto.CurrencyRateResponse;
import reactor.core.publisher.Mono;

public interface CurrencyRateService {

    Mono<CurrencyRateResponse> getCurrencyRates();

}
