# Currency Rate Service

### Description

A small and simple application that fetches currency rates from an external 
API and stores them in a database.Built using a reactive approach with Spring Boot.

### Core Functionality

- Fetch currency rates from an external API
- Store currency rates in a database
- Return currency rates from the database if the external API fails
- Return an empty list if both the external API and the database fail

### Tech Stack

- Java 21
- Spring Boot 4.0.0
- Spring WebFlux (Reactive)
- Spring Data R2DBC
- PostgreSQL
- Flyway (for DB migrations)

### Additional Features

- Unit tests
- Integration tests
- Structured logging
- Docker Compose for local setup

### Launch Instructions

1. The fastest way to launch the application is to use the included `docker-compose.yml`.
2. Alternatively, you can override application.properties with your own values and run the application manually.
