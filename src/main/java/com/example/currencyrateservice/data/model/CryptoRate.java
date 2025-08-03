package com.example.currencyrateservice.data.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "crypto_rate")
public class CryptoRate {

    @Id
    private Long id;
    private String currency;
    private BigDecimal rate;
    private LocalDateTime createdAt;

}
