package com.example.currencyrateservice.service.impl;

import com.example.currencyrateservice.data.dto.CryptoCurrencyRateDTO;
import com.example.currencyrateservice.data.dto.CurrencyRateItem;
import com.example.currencyrateservice.service.ExternalCurrencyClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalCurrencyClientImpl implements ExternalCurrencyClient {

    private final WebClient currencyWebClient;

    @Override
    public Mono<List<CurrencyRateItem>> getExternalFiatRates() {
        return currencyWebClient.get()
                .uri("/fiat-currency-rates")
                .header("X-API-KEY", "secret-key")
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse -> clientResponse
                        .bodyToMono(String.class)
                        .then(Mono.error(new RuntimeException("Fiat error occurred"))))
                .bodyToFlux(CurrencyRateItem.class)
                .collectList()
                .timeout(Duration.ofSeconds(4))
                .doOnSuccess(list -> log.info("Received {} fiat rates from external API", list.size()))
                .doOnError(e -> log.error(e.getMessage()));
    }

    @Override
    public Mono<List<CryptoCurrencyRateDTO>> getExternalCryptoRates() {
        return currencyWebClient.get()
                .uri("/crypto-currency-rates")
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse -> clientResponse
                        .bodyToMono(String.class)
                        .then(Mono.error(new RuntimeException("Crypto error occurred"))))
                .bodyToFlux(CryptoCurrencyRateDTO.class)
                .collectList()
                .timeout(Duration.ofSeconds(4))
                .doOnSuccess(list -> log.info("Received {} crypto rates from external API", list.size()))
                .doOnError(e -> log.error(e.getMessage()));
    }

}

