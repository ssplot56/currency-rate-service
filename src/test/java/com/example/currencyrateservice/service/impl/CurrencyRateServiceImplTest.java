package com.example.currencyrateservice.service.impl;

import com.example.currencyrateservice.data.dto.CryptoCurrencyRateDTO;
import com.example.currencyrateservice.data.dto.CurrencyRateItem;
import com.example.currencyrateservice.data.dto.CurrencyRateResponse;
import com.example.currencyrateservice.data.mapper.CryptoRateMapper;
import com.example.currencyrateservice.data.mapper.FiatRateMapper;
import com.example.currencyrateservice.data.model.CryptoRate;
import com.example.currencyrateservice.data.model.FiatRate;
import com.example.currencyrateservice.repository.CryptoRateRepository;
import com.example.currencyrateservice.repository.FiatRateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrencyRateServiceImplTest {

    private static final String USD = "USD";
    private static final String BTC = "BTC";
    private static final BigDecimal USD_VALUE = BigDecimal.valueOf(123.33);
    private static final BigDecimal BTC_VALUE = BigDecimal.valueOf(1232.22);

    @Mock
    private FiatRateRepository fiatRepository;

    @Mock
    private CryptoRateRepository cryptoRepository;

    @Mock
    private ExternalCurrencyClientImpl externalClient;

    @Mock
    private FiatRateMapper fiatMapper;

    @Mock
    private CryptoRateMapper cryptoMapper;

    private CurrencyRateServiceImpl service;

    private CurrencyRateItem fiatRateItem;
    private CurrencyRateItem cryptoRateItem;
    private CryptoCurrencyRateDTO cryptoDTO;

    private FiatRate fiatRate;
    private CryptoRate cryptoRate;

    @BeforeEach
    void setUp() {
        service = new CurrencyRateServiceImpl(
                fiatRepository, cryptoRepository, externalClient, fiatMapper, cryptoMapper
        );

        fiatRateItem = new CurrencyRateItem(USD, USD_VALUE);
        cryptoRateItem = new CurrencyRateItem(BTC, BTC_VALUE);
        cryptoDTO = new CryptoCurrencyRateDTO(BTC, BTC_VALUE);

        fiatRate = new FiatRate();
        cryptoRate = new CryptoRate();
    }

    @Test
    void getCurrencyRates_whenFiatAndCryptoRatesAreAvailable_shouldReturnFiatAndCrypto() {
        when(externalClient.getExternalFiatRates()).thenReturn(Mono.just(List.of(fiatRateItem)));
        when(externalClient.getExternalCryptoRates()).thenReturn(Mono.just(List.of(cryptoDTO)));

        when(fiatMapper.toModel(fiatRateItem)).thenReturn(fiatRate);
        when(cryptoMapper.toModel(cryptoDTO)).thenReturn(cryptoRate);

        when(fiatRepository.save(fiatRate)).thenReturn(Mono.just(fiatRate));
        when(cryptoRepository.save(cryptoRate)).thenReturn(Mono.just(cryptoRate));

        Mono<CurrencyRateResponse> responseMono = service.getCurrencyRates();

        StepVerifier.create(responseMono)
                .assertNext(response -> {
                    assertEquals(1, response.fiat().size());
                    assertEquals(USD, response.fiat().getFirst().currency());

                    assertEquals(1, response.crypto().size());
                    assertEquals(BTC, response.crypto().getFirst().currency());
                })
                .verifyComplete();
    }

    @Test
    void getCurrencyRates_whenOnlyFiatRatesAreAvailable_shouldReturnFiatOnlyAndEmptyCrypto() {
        when(externalClient.getExternalFiatRates()).thenReturn(Mono.just(List.of(fiatRateItem)));
        when(externalClient.getExternalCryptoRates()).thenReturn(Mono.error(new RuntimeException("Crypto API failure")));

        when(fiatMapper.toModel(fiatRateItem)).thenReturn(fiatRate);
        when(fiatRepository.save(fiatRate)).thenReturn(Mono.just(fiatRate));

        when(cryptoRepository.findLatestUnique()).thenReturn(Flux.empty());

        Mono<CurrencyRateResponse> responseMono = service.getCurrencyRates();

        StepVerifier.create(responseMono)
                .assertNext(response -> {
                    assertEquals(1, response.fiat().size());
                    assertEquals(USD, response.fiat().getFirst().currency());

                    assertNotNull(response.crypto());
                    assertTrue(response.crypto().isEmpty());
                })
                .verifyComplete();
    }

    @Test
    void getCurrencyRates_whenOnlyCryptoRatesAreAvailable_shouldReturnCryptoOnlyAndEmptyFiat() {
        when(externalClient.getExternalFiatRates()).thenReturn(Mono.error(new RuntimeException("Fiat API failure")));
        when(externalClient.getExternalCryptoRates()).thenReturn(Mono.just(List.of(cryptoDTO)));

        when(cryptoMapper.toModel(cryptoDTO)).thenReturn(cryptoRate);
        when(cryptoRepository.save(cryptoRate)).thenReturn(Mono.just(cryptoRate));

        when(fiatRepository.findLatestUnique()).thenReturn(Flux.empty());

        Mono<CurrencyRateResponse> responseMono = service.getCurrencyRates();

        StepVerifier.create(responseMono)
                .assertNext(response -> {
                    assertNotNull(response.fiat());
                    assertTrue(response.fiat().isEmpty());

                    assertEquals(1, response.crypto().size());
                    assertEquals(BTC, response.crypto().getFirst().currency());
                })
                .verifyComplete();
    }

    @Test
    void getCurrencyRates_whenBothFiatAndCryptoRatesAreNotAvailable_shouldReturnEmptyFiatAndCrypto() {
        when(externalClient.getExternalFiatRates()).thenReturn(Mono.error(new RuntimeException("Fiat API failure")));
        when(externalClient.getExternalCryptoRates()).thenReturn(Mono.error(new RuntimeException("Crypto API failure")));

        when(fiatRepository.findLatestUnique()).thenReturn(Flux.empty());
        when(cryptoRepository.findLatestUnique()).thenReturn(Flux.empty());

        Mono<CurrencyRateResponse> responseMono = service.getCurrencyRates();

        StepVerifier.create(responseMono)
                .assertNext(response -> {
                    assertNotNull(response.fiat());
                    assertTrue(response.fiat().isEmpty());

                    assertNotNull(response.crypto());
                    assertTrue(response.crypto().isEmpty());
                })
                .verifyComplete();
    }

    @Test
    void getCurrencyRates_whenFiatAndCryptoRatesAreNotAvailableAndDBWithData_shouldReturnDataFromDB() {
        when(externalClient.getExternalFiatRates()).thenReturn(Mono.error(new RuntimeException("Fiat API failure")));
        when(externalClient.getExternalCryptoRates()).thenReturn(Mono.error(new RuntimeException("Crypto API failure")));

        when(fiatRepository.findLatestUnique()).thenReturn(Flux.just(fiatRate));
        when(cryptoRepository.findLatestUnique()).thenReturn(Flux.just(cryptoRate));

        when(fiatMapper.toDto(fiatRate)).thenReturn(fiatRateItem);
        when(cryptoMapper.toDto(cryptoRate)).thenReturn(cryptoRateItem);

        Mono<CurrencyRateResponse> responseMono = service.getCurrencyRates();

        StepVerifier.create(responseMono)
                .assertNext(response -> {
                    assertEquals(1, response.fiat().size());
                    assertEquals(USD, response.fiat().getFirst().currency());

                    assertEquals(1, response.crypto().size());
                    assertEquals(BTC, response.crypto().getFirst().currency());
                })
                .verifyComplete();
    }

}