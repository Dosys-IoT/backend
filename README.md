# Dosys Backend

Backend REST API Spring Boot 3 y Java 21.

## URLs desplegadas
- Backend Cloud Run: `https://dosys-backend-149855215912.us-central1.run.app`
- Edge Cloud Run: `https://dosys-edge-149855215912.us-central1.run.app`

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

## Configuración de base de datos
En desarrollo local, la aplicación puede arrancar con H2 si no se definen variables de entorno.
Eso solo sirve para pruebas locales.

En Cloud Run, `dosys-backend` debe usar PostgreSQL/Supabase real mediante estas variables:
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`

URL esperada para Supabase:
```text
jdbc:postgresql://aws-1-us-east-2.pooler.supabase.com:5432/postgres?sslmode=require
```

La contraseña no debe commitearse. En Cloud Run debe llegar desde Secret Manager.

## Swagger
- UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI: `http://localhost:8080/v3/api-docs`
- Tags: `Access`, `Medication`, `Device Internal`

## Integración Edge / ESP32
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

12. Runtime config desde Edge:
```powershell
curl.exe -X GET "http://localhost:8080/api/v1/device/internal/1/runtime-config" `
  -H "X-Edge-Service-Key: dosys-local-edge-service-key-change-me-2026"

Invoke-RestMethod -Method Get `
  -Uri "http://localhost:8080/api/v1/device/internal/1/runtime-config" `
  -Headers @{ "X-Edge-Service-Key" = "dosys-local-edge-service-key-change-me-2026" }
```

13. Envío de lectura ambiental:
```powershell
curl.exe -X POST "http://localhost:8080/api/v1/device/internal/1/environment-readings" `
  -H "Content-Type: application/json" `
  -H "X-Edge-Service-Key: dosys-local-edge-service-key-change-me-2026" `
  -d "{""eventId"":""env-1"",""temperature"":27.8,""humidity"":60.2,""recordedAt"":""2026-06-27T12:00:00"",""firmwareVersion"":""1.0.0""}"
```

14. Envío de heartbeat:
```powershell
curl.exe -X POST "http://localhost:8080/api/v1/device/internal/1/heartbeats" `
  -H "Content-Type: application/json" `
  -H "X-Edge-Service-Key: dosys-local-edge-service-key-change-me-2026" `
  -d "{""eventId"":""hb-1"",""rtcTime"":""2026-06-27T12:00:00"",""wifiConnected"":true,""mqttConnected"":true,""rtcOk"":true,""sht3xOk"":true,""dfPlayerOk"":true,""sdCardOk"":true,""switchOk"":true,""buttonPin"":15,""freeHeap"":180000,""rssi"":-55,""deviceStatus"":""ONLINE"",""firmwareVersion"":""1.0.0""}"
```

15. Envío de intake:
```powershell
curl.exe -X POST "http://localhost:8080/api/v1/device/internal/1/intake-events" `
  -H "Content-Type: application/json" `
  -H "X-Edge-Service-Key: dosys-local-edge-service-key-change-me-2026" `
  -d "{""eventId"":""intake-1"",""scheduleId"":1,""containerNumber"":1,""scheduledAt"":""2026-06-27T08:00:00"",""confirmedAt"":""2026-06-27T08:02:15"",""status"":""TAKEN"",""source"":""PHYSICAL_BUTTON"",""buttonPin"":15}"
```

16. Envío de stock:
```powershell
curl.exe -X POST "http://localhost:8080/api/v1/device/internal/1/stock-events" `
  -H "Content-Type: application/json" `
  -H "X-Edge-Service-Key: dosys-local-edge-service-key-change-me-2026" `
  -d "{""eventId"":""stock-1"",""containerNumber"":1,""remainingPills"":19,""reportedAt"":""2026-06-27T08:02:20"",""reason"":""INTAKE_CONFIRMED""}"
```

17. El estado consultable por frontend vive en:
   - `GET /api/v1/medication/devices/1/status`

18. HiveMQ se configura en la Edge API, no en la REST API. El Backend solo recibe HTTP desde Edge y persiste en PostgreSQL/Supabase.

## Advertencia
- No commitear secretos ni claves reales.
- Usar variables de entorno para `EDGE_SERVICE_KEY`, JWT y credenciales de base de datos.
- No asumir que H2 representa la base real en despliegue; en producción siempre debe existir la configuración de PostgreSQL/Supabase.

## Tests
- `mvnw.cmd test` (Windows)
- `./mvnw test` (Linux/Mac)
