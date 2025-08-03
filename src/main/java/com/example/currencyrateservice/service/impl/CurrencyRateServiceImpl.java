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
import com.example.currencyrateservice.service.CurrencyRateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

import java.util.List;

@Slf4j
@Service
public class CurrencyRateServiceImpl implements CurrencyRateService {

    private final ExternalCurrencyClientImpl externalClient;
    private final FiatRateRepository fiatRepository;
    private final CryptoRateRepository cryptoRepository;
    private final FiatRateMapper fiatMapper;
    private final CryptoRateMapper cryptoMapper;

    public CurrencyRateServiceImpl(FiatRateRepository fiatRepository,
                                   CryptoRateRepository cryptoRepository,
                                   ExternalCurrencyClientImpl externalClient,
                                   FiatRateMapper fiatMapper,
                                   CryptoRateMapper cryptoMapper) {
        this.fiatRepository = fiatRepository;
        this.cryptoRepository = cryptoRepository;
        this.externalClient = externalClient;
        this.fiatMapper = fiatMapper;
        this.cryptoMapper = cryptoMapper;
    }

    @Override
    public Mono<CurrencyRateResponse> getCurrencyRates() {
        return Mono.zip(getAndSaveFiatRates(), getAndSaveCryptoRates())
                .doFirst(() -> log.info("Starting to fetch and save rates"))
                .doOnSuccess(tuple -> log.info("Finished fetching and saving rates"))
                .map(this::buildCurrencyRateResponse);
    }

    private CurrencyRateResponse buildCurrencyRateResponse(
            Tuple2<List<CurrencyRateItem>, List<CurrencyRateItem>> tuple
    ) {
        return new CurrencyRateResponse(tuple.getT1(), tuple.getT2());
    }

    private Mono<List<CurrencyRateItem>> getAndSaveFiatRates() {
        return externalClient.getExternalFiatRates()
                .flatMapMany(this::saveFiatRates)
                .collectList()
                .onErrorResume(e -> {
                    log.warn("Error fetching fiat rates, falling back to DB: {}", e.getMessage());
                    return fallbackFiatRates();
                });
    }

    private Mono<List<CurrencyRateItem>> getAndSaveCryptoRates() {
        return externalClient.getExternalCryptoRates()
                .flatMapMany(this::saveCryptoRates)
                .collectList()
                .onErrorResume(e -> {
                    log.warn("Error fetching crypto rates, falling back to DB: {}", e.getMessage());
                    return fallbackCryptoRates();
                });
    }

    private Flux<CurrencyRateItem> saveFiatRates(List<CurrencyRateItem> rates) {
        return Flux.fromIterable(rates)
                .doOnSubscribe(s -> log.info("Saving {} fiat rates", rates.size()))
                .flatMap(rate -> {
                    FiatRate fiatRate = fiatMapper.toModel(rate);
                    return fiatRepository.save(fiatRate).thenReturn(rate);
                });
    }

    private Flux<CurrencyRateItem> saveCryptoRates(List<CryptoCurrencyRateDTO> rates) {
        return Flux.fromIterable(rates)
                .doOnSubscribe(s -> log.info("Saving {} crypto rates", rates.size()))
                .flatMap(rate -> {
                    CryptoRate cryptoRate = cryptoMapper.toModel(rate);
                    return cryptoRepository.save(cryptoRate)
                            .thenReturn(convertToCurrencyRateItem(rate));
                });
    }

    private Mono<List<CurrencyRateItem>> fallbackFiatRates() {
        return fiatRepository.findLatestUnique()
                .doOnSubscribe(s -> log.info("Using fallback fiat rates from DB"))
                .map(fiatMapper::toDto)
                .collectList()
                .doOnSuccess((list) -> log.info("Fallback fiat rates: {}", list));
    }

    private Mono<List<CurrencyRateItem>> fallbackCryptoRates() {
        return cryptoRepository.findLatestUnique()
                .doOnSubscribe(s -> log.info("Using fallback crypto rates from DB"))
                .map(cryptoMapper::toDto)
                .collectList()
                .doOnSuccess((list) -> log.info("Fallback crypto rates: {}", list));
    }

    private CurrencyRateItem convertToCurrencyRateItem(CryptoCurrencyRateDTO dto) {
        return new CurrencyRateItem(dto.name(), dto.value());
    }

}
