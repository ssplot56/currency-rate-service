package com.example.currencyrateservice.repository;

import com.example.currencyrateservice.data.model.FiatRate;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface FiatRateRepository extends ReactiveCrudRepository<FiatRate, Long> {

    @Query("SELECT DISTINCT ON (currency) id, currency, rate, created_at " +
            "FROM fiat_rate " +
            "ORDER BY currency, created_at DESC")
    Flux<FiatRate> findLatestUnique();

}
