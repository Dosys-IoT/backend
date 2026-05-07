# Dosys Backend

Backend REST API Spring Boot 3 y Java 21.

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

## Local Edge API Integration Test
1. Correr REST API local:
   - `mvnw.cmd spring-boot:run`

2. Abrir Swagger:
   - `http://localhost:8080/swagger-ui.html`

3. Crear usuario:
   - `POST /api/v1/access/register`

4. Login:
   - `POST /api/v1/access/login`

5. Autorizar Swagger con Bearer token.

6. Crear device:
   - `POST /api/v1/medication/devices`

7. Copiar:
   - `deviceId`
   - `deviceKey`

8. Configurar contenedor 1:
   - `PUT /api/v1/medication/devices/{deviceId}/containers/1`

9. Crear horario:
   - `POST /api/v1/medication/devices/{deviceId}/schedules`

10. Copiar:
    - `scheduleId`

11. Configurar la Edge API local con:
    - `REST_API_BASE_URL=http://localhost:8080`
    - `EDGE_SERVICE_KEY=dosys-local-edge-service-key-change-me-2026`
    - `DEVICE_KEYS_JSON={}` (opcional, solo compatibilidad)

12. Verificar runtime config con curl:
```powershell
curl.exe -X GET "http://localhost:8080/api/v1/device/internal/<deviceId>/runtime-config" -H "X-Edge-Service-Key: dosys-local-edge-service-key-change-me-2026"
```

13. HiveMQ se configura en la Edge API, no en la REST API.

## Tests
- `mvnw.cmd test` (Windows)
- `./mvnw test` (Linux/Mac)
