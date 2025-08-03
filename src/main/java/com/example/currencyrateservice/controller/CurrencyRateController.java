package com.example.currencyrateservice.controller;

import com.example.currencyrateservice.data.dto.CurrencyRateResponse;
import com.example.currencyrateservice.service.CurrencyRateService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/currency-rates")
public class CurrencyRateController {

    private final CurrencyRateService currencyRateService;

    public CurrencyRateController(CurrencyRateService currencyRateService) {
        this.currencyRateService = currencyRateService;
    }

    @GetMapping
    public Mono<CurrencyRateResponse> getCurrencyRates() {
        return currencyRateService.getCurrencyRates();
    }

}
