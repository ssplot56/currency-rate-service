package com.example.currencyrateservice.service;

import com.example.currencyrateservice.data.dto.CryptoCurrencyRateDTO;
import com.example.currencyrateservice.data.dto.CurrencyRateItem;
import reactor.core.publisher.Mono;

import java.util.List;

public interface ExternalCurrencyClient {

    Mono<List<CurrencyRateItem>> getExternalFiatRates();
    Mono<List<CryptoCurrencyRateDTO>> getExternalCryptoRates();

}
