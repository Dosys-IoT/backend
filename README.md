# Dosys Backend

Backend REST API para Dosys, implementado con Spring Boot 3 y Java 21.

## Stack
- Java 21
- Spring Boot 3
- Maven
- Spring Web
- Spring Data JPA (Hibernate)
- Spring Security
- Bean Validation
- PostgreSQL Driver
- Flyway
- JWT (jjwt)
- springdoc OpenAPI/Swagger

## Requisitos
- JDK 21
- Maven 3.9+ (opcional si usas wrapper)
- PostgreSQL (compatible con Supabase)

## Swagger
- UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI: `http://localhost:8080/v3/api-docs`
- Tags: `Access`, `Medication`, `Device Internal`
- Endpoints internos del dispositivo usan header `X-Device-Key` (no JWT).
