package com.example.currencyrateservice.service.impl;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.notMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

@ExtendWith(SpringExtension.class)
class ExternalCurrencyClientImplTest {

    private static final String BASE_URL = "http://localhost:9561";
    private static final String FIAT_ENDPOINT = "/fiat-currency-rates";
    private static final String CRYPTO_ENDPOINT = "/crypto-currency-rates";

    private static WireMockServer wireMockServer;

    private ExternalCurrencyClientImpl externalClient;

    @BeforeAll
    static void beforeAll() {
        wireMockServer = new WireMockServer(9561);
        wireMockServer.start();
        WireMock.configureFor("localhost", 9561);
    }

    @AfterAll
    static void afterAll() {
        wireMockServer.stop();
    }

    @BeforeEach
    void setUp() {
        WebClient webClient = WebClient.builder()
                .baseUrl(BASE_URL)
                .build();

        externalClient = new ExternalCurrencyClientImpl(webClient);
    }

    @Test
    void getExternalFiatRates_shouldReturnRates() {
        String jsonResponse = """
                    [
                        {"currency": "USD", "rate": 123.45},
                        {"currency": "EUR", "rate": 234.56}
                    ]
                """;

        stubFor(get(urlEqualTo(FIAT_ENDPOINT))
                .withHeader("X-API-KEY", equalTo("secret-key"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonResponse)));

        StepVerifier.create(externalClient.getExternalFiatRates())
                .expectNextMatches(list ->
                        list.size() == 2 &&
                                list.get(0).currency().equals("USD") &&
                                list.get(1).currency().equals("EUR"))
                .verifyComplete();
    }

    @Test
    void getExternalCryptoRates_shouldReturnRates() {
        String jsonResponse = """
                    [
                        {"name": "BTC", "value": 54321.00},
                        {"name": "ETH", "value": 2345.67}
                    ]
                """;

        stubFor(get(urlEqualTo(CRYPTO_ENDPOINT))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonResponse)));

        StepVerifier.create(externalClient.getExternalCryptoRates())
                .expectNextMatches(list ->
                        list.size() == 2 &&
                                list.get(0).name().equals("BTC") &&
                                list.get(1).name().equals("ETH"))
                .verifyComplete();
    }

    @Test
    void getExternalFiatRates_shouldHandleError() {
        stubFor(get(urlEqualTo(FIAT_ENDPOINT))
                .withHeader("X-API-KEY", equalTo("secret-key"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("Internal Server Error")));

        StepVerifier.create(externalClient.getExternalFiatRates())
                .expectErrorMatches(error ->
                        error instanceof RuntimeException &&
                                error.getMessage().contains("Fiat error"))
                .verify();
    }

    @Test
    void getExternalCryptoRates_shouldHandleError() {
        stubFor(get(urlEqualTo(CRYPTO_ENDPOINT))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("Internal Server Error")));

        StepVerifier.create(externalClient.getExternalCryptoRates())
                .expectErrorMatches(error ->
                        error instanceof RuntimeException &&
                                error.getMessage().contains("Crypto error"))
                .verify();
    }

    @Test
    void getExternalFiatRates_whenMissingApiKeyHeader_shouldReturnError() {
        stubFor(get(urlEqualTo(FIAT_ENDPOINT))
                .withHeader("X-API-KEY", notMatching("secret-key"))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withBody("Unauthorized: Missing or invalid API key")));

        WebClient webClientWithoutHeader = WebClient.builder()
                .baseUrl(BASE_URL)
                .build();
        ExternalCurrencyClientImpl clientWithoutHeader = new ExternalCurrencyClientImpl(webClientWithoutHeader);

        StepVerifier.create(clientWithoutHeader.getExternalFiatRates())
                .expectErrorMatches(error ->
                        error instanceof RuntimeException &&
                                error.getMessage().contains("Fiat error"))
                .verify();
    }

}
