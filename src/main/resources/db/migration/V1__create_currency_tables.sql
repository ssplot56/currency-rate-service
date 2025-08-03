CREATE TABLE crypto_rate (
                             id BIGSERIAL PRIMARY KEY,
                             currency VARCHAR(20) NOT NULL,
                             rate NUMERIC(20, 8) NOT NULL,
                             created_at TIMESTAMP NOT NULL
);

CREATE TABLE fiat_rate (
                           id BIGSERIAL PRIMARY KEY,
                           currency VARCHAR(20) NOT NULL,
                           rate NUMERIC(20, 8) NOT NULL,
                           created_at TIMESTAMP NOT NULL
);
