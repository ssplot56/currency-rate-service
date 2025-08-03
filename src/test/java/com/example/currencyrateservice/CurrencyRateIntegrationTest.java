package com.example.currencyrateservice;

import com.example.currencyrateservice.data.dto.CurrencyRateItem;
import com.example.currencyrateservice.data.dto.CurrencyRateResponse;
import com.example.currencyrateservice.data.model.CryptoRate;
import com.example.currencyrateservice.data.model.FiatRate;
import com.example.currencyrateservice.repository.CryptoRateRepository;
import com.example.currencyrateservice.repository.FiatRateRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Testcontainers
class CurrencyRateIntegrationTest {

    @Autowired
    private CryptoRateRepository cryptoRateRepository;

    @Autowired
    private FiatRateRepository fiatRateRepository;

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    static String getJdbcUrl() {
        return "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(5432) + "/testdb";
    }

    static String getR2dbcUrl() {
        return "r2dbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(5432) + "/testdb";
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", CurrencyRateIntegrationTest::getR2dbcUrl);
        registry.add("spring.r2dbc.username", () -> "test");
        registry.add("spring.r2dbc.password", () -> "test");

        registry.add("spring.datasource.url", CurrencyRateIntegrationTest::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "test");
        registry.add("spring.datasource.password", () -> "test");

        registry.add("spring.flyway.enabled", () -> "true");

        registry.add("external.currency-api.url", () -> "http://localhost:9561");
    }

    private static WireMockServer wireMockServer;

    @Autowired
    private WebTestClient webTestClient;

    @BeforeAll
    static void migrateFlyway() {
        Flyway flyway = Flyway.configure()
                .dataSource(getJdbcUrl(), "test", "test")
                .locations("classpath:db/migration")
                .load();

        flyway.migrate();
    }

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(9561);
        wireMockServer.start();
        WireMock.configureFor("localhost", 9561);
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    @AfterEach
    void cleanDB() {
        cryptoRateRepository.deleteAll().block();
        fiatRateRepository.deleteAll().block();
    }

    @Test
    void whenBothExternalApisRespondSuccessfullyAndDbEmpty_shouldStoreAndReturnRates() {
        stubForGetFiatRates();
        stubForGetCryptoRates();
        webTestClient.get()
                .uri("/currency-rates")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.fiat[0].currency").isEqualTo("USD")
                .jsonPath("$.crypto[0].currency").isEqualTo("BTC");
    }

    @Test
    void whenBothExternalApisFailAndDbEmpty_shouldReturnEmptyLists() {
        stubForGetFiatRatesError();
        stubForGetCryptoRatesError();

        webTestClient.get()
                .uri("/currency-rates")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.fiat").isArray()
                .jsonPath("$.fiat").isEmpty()
                .jsonPath("$.crypto").isArray()
                .jsonPath("$.crypto").isEmpty();
    }

    @Test
    void whenBothExternalApisFailAndDbNotEmpty_shouldReturnLatestFromDb() {
        insertTestCryptoBases();
        insertTestFiatBases();

        stubForGetFiatRatesError();
        stubForGetCryptoRatesError();

        webTestClient.get()
                .uri("/currency-rates")
                .exchange()
                .expectStatus().isOk()
                .expectBody(CurrencyRateResponse.class)
                .value(response -> {
                    Assertions.assertNotNull(response);
                    assertThat(response.fiat())
                            .usingComparatorForType(Comparator.comparing(BigDecimal::doubleValue), BigDecimal.class)
                            .extracting(CurrencyRateItem::rate)
                            .containsExactlyInAnyOrder(
                                    BigDecimal.valueOf(120.0),
                                    BigDecimal.valueOf(130.0)
                            );

                    assertThat(response.crypto())
                            .usingComparatorForType(Comparator.comparing(BigDecimal::doubleValue), BigDecimal.class)
                            .extracting(CurrencyRateItem::rate)
                            .containsExactlyInAnyOrder(
                                    BigDecimal.valueOf(50000.0),
                                    BigDecimal.valueOf(2000.0)
                            );
                });
    }

    @Test
    void whenFiatApiFailsAndDbNotEmpty_shouldReturnLatestFiatFromDbAndCryptoFromExternalApi() {
        insertTestCryptoBases();
        insertTestFiatBases();

        stubForGetFiatRatesError();
        stubForGetCryptoRates();

        webTestClient.get()
                .uri("/currency-rates")
                .exchange()
                .expectStatus().isOk()
                .expectBody(CurrencyRateResponse.class)
                .value(response -> {
                    Assertions.assertNotNull(response);
                    assertThat(response.fiat())
                            .usingComparatorForType(Comparator.comparing(BigDecimal::doubleValue), BigDecimal.class)
                            .extracting(CurrencyRateItem::rate)
                            .containsExactlyInAnyOrder(
                                    BigDecimal.valueOf(120.0),
                                    BigDecimal.valueOf(130.0)
                            );

                    assertThat(response.crypto())
                            .usingComparatorForType(Comparator.comparing(BigDecimal::doubleValue), BigDecimal.class)
                            .extracting(CurrencyRateItem::rate)
                            .containsExactlyInAnyOrder(
                                    BigDecimal.valueOf(54321.0),
                                    BigDecimal.valueOf(2345.67)
                            );
                });
    }

    @Test
    void whenCryptoApiFailsAndDbNotEmpty_shouldReturnLatestCryptoFromDbAndFiatFromExternalApi() {
        insertTestCryptoBases();
        insertTestFiatBases();

        stubForGetFiatRates();
        stubForGetCryptoRatesError();

        webTestClient.get()
                .uri("/currency-rates")
                .exchange()
                .expectStatus().isOk()
                .expectBody(CurrencyRateResponse.class)
                .value(response -> {
                    Assertions.assertNotNull(response);
                    assertThat(response.fiat())
                            .usingComparatorForType(Comparator.comparing(BigDecimal::doubleValue), BigDecimal.class)
                            .extracting(CurrencyRateItem::rate)
                            .containsExactlyInAnyOrder(
                                    BigDecimal.valueOf(123.45),
                                    BigDecimal.valueOf(234.56)
                            );

                    assertThat(response.crypto())
                            .usingComparatorForType(Comparator.comparing(BigDecimal::doubleValue), BigDecimal.class)
                            .extracting(CurrencyRateItem::rate)
                            .containsExactlyInAnyOrder(
                                    BigDecimal.valueOf(50000.0),
                                    BigDecimal.valueOf(2000.0)
                            );
                });
    }

    @Test
    void whenExternalApisTimeout_shouldReturnEmptyLists() {
        stubForGetFiatRatesTimeout();
        stubForGetCryptoRatesTimeout();

        webTestClient.get()
                .uri("/currency-rates")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.fiat").isArray()
                .jsonPath("$.fiat").isEmpty()
                .jsonPath("$.crypto").isArray()
                .jsonPath("$.crypto").isEmpty();
    }

    @Test
    void whenExternalApisTimeout_shouldReturnLatestFromDb() {
        insertTestCryptoBases();
        insertTestFiatBases();

        stubForGetFiatRatesTimeout();
        stubForGetCryptoRatesTimeout();

        webTestClient.get()
                .uri("/currency-rates")
                .exchange()
                .expectStatus().isOk()
                .expectBody(CurrencyRateResponse.class)
                .value(response -> {
                    Assertions.assertNotNull(response);

                    assertThat(response.fiat())
                            .usingComparatorForType(Comparator.comparing(BigDecimal::doubleValue), BigDecimal.class)
                            .extracting(CurrencyRateItem::rate)
                            .containsExactlyInAnyOrder(
                                    BigDecimal.valueOf(120.0),
                                    BigDecimal.valueOf(130.0)
                            );

                    assertThat(response.crypto())
                            .usingComparatorForType(Comparator.comparing(BigDecimal::doubleValue), BigDecimal.class)
                            .extracting(CurrencyRateItem::rate)
                            .containsExactlyInAnyOrder(
                                    BigDecimal.valueOf(50000.0),
                                    BigDecimal.valueOf(2000.0)
                            );
                });
    }

    private void stubForGetFiatRates() {
        stubFor(get(urlEqualTo("/fiat-currency-rates"))
                .withHeader("X-API-KEY", equalTo("secret-key"))
                .willReturn(okJson("""
                            [
                                {"currency": "USD", "rate": 123.45},
                                {"currency": "EUR", "rate": 234.56}
                            ]
                        """)));
    }

    private void stubForGetCryptoRates() {
        stubFor(get(urlEqualTo("/crypto-currency-rates"))
                .willReturn(okJson("""
                            [
                                {"name": "BTC", "value": 54321.00},
                                {"name": "ETH", "value": 2345.67}
                            ]
                        """)));
    }

    private void stubForGetFiatRatesError() {
        stubFor(get(urlEqualTo("/fiat-currency-rates"))
                .withHeader("X-API-KEY", equalTo("secret-key"))
                .willReturn(serverError()));
    }

    private void stubForGetCryptoRatesError() {
        stubFor(get(urlEqualTo("/crypto-currency-rates"))
                .willReturn(serverError()));
    }

    private void stubForGetFiatRatesTimeout() {
        stubFor(get(urlEqualTo("/fiat-currency-rates"))
                .withHeader("X-API-KEY", equalTo("secret-key"))
                .willReturn(aResponse()
                        .withFixedDelay(5000)));
    }

    private void stubForGetCryptoRatesTimeout() {
        stubFor(get(urlEqualTo("/crypto-currency-rates"))
                .willReturn(aResponse()
                        .withFixedDelay(5000)));
    }

    private void insertTestCryptoBases() {
        LocalDateTime now = LocalDateTime.now();
        cryptoRateRepository.saveAll(List.of(
                new CryptoRate(null, "BTC", BigDecimal.valueOf(50000.0), now),
                new CryptoRate(null, "ETH", BigDecimal.valueOf(2000.0), now)
        )).collectList().block();
    }

    private void insertTestFiatBases() {
        LocalDateTime now = LocalDateTime.now();
        fiatRateRepository.saveAll(List.of(
                new FiatRate(null, "USD", BigDecimal.valueOf(120.0), now),
                new FiatRate(null, "EUR", BigDecimal.valueOf(130.0), now)
        )).collectList().block();
    }

}
