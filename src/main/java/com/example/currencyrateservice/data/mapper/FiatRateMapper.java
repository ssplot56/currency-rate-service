package com.example.currencyrateservice.data.mapper;

import com.example.currencyrateservice.data.dto.CurrencyRateItem;
import com.example.currencyrateservice.data.model.FiatRate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class FiatRateMapper {

    public FiatRate toModel(CurrencyRateItem item) {
        return new FiatRate(null, item.currency(), item.rate(), LocalDateTime.now());
    }

    public CurrencyRateItem toDto(FiatRate fiatRate) {
        return new CurrencyRateItem(fiatRate.getCurrency(), fiatRate.getRate());
    }

}
