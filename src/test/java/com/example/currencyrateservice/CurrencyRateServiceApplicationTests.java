package com.example.currencyrateservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.r2dbc.url=r2dbc:h2:mem:///testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.r2dbc.username=sa",
        "spring.r2dbc.password=",
        "spring.flyway.enabled=false",
        "external.currency-api.url=http://localhost:8080"
})
class CurrencyRateServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
