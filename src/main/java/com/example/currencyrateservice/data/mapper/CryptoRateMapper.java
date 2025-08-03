package com.example.currencyrateservice.data.mapper;

import com.example.currencyrateservice.data.dto.CryptoCurrencyRateDTO;
import com.example.currencyrateservice.data.dto.CurrencyRateItem;
import com.example.currencyrateservice.data.model.CryptoRate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class CryptoRateMapper {

    public CryptoRate toModel(CryptoCurrencyRateDTO item) {
        return new CryptoRate(null, item.name(), item.value(), LocalDateTime.now());
    }

    public CurrencyRateItem toDto(CryptoRate cryptoRate) {
        return new CurrencyRateItem(cryptoRate.getCurrency(), cryptoRate.getRate());
    }

}
