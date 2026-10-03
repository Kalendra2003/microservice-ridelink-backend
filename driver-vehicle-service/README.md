# Driver & Vehicle Service

Part of **RideLink** (IT3130 Application Development group assignment).
**Primary owner:** Member 2 – Dilmi Sanjana

Manages driver operational profiles, the registered vehicle, availability, service area,
simulated current location, and the lookup of eligible available drivers used by the
Ride Management Service. It owns its own MongoDB database (`ridelink_driver_db`); no other
service reads or writes it.

## Tech stack
Java 17 · Spring Boot 4.1.1 · Spring Web MVC · Spring Data MongoDB · Bean Validation ·
springdoc OpenAPI (Swagger UI) · Nimbus JOSE+JWT · Maven (wrapper included)

## Prerequisites
- JDK 17
- MongoDB running locally (default `mongodb://localhost:27017`)

## Configuration
Secrets are never committed. Set them as environment variables (see `.env.example`).

| Variable | Required | Default | Purpose |
|---|---|---|---|
| `JWT_SECRET` | yes | – | HS256 secret (min. 32 chars); must equal the secret the Account Service signs tokens with |
| `MONGODB_URI` | no | `mongodb://localhost:27017/ridelink_driver_db` | This service's own database |
| `SERVER_PORT` | no | `8082` | HTTP port |

## Run (from the `driver-vehicle-service` folder)
Windows PowerShell:
```
$env:JWT_SECRET="ridelink-demo-secret-0123456789-abcdef"
.\mvnw.cmd spring-boot:run
```
Linux / macOS:
```
export JWT_SECRET="ridelink-demo-secret-0123456789-abcdef"
./mvnw spring-boot:run
```
Swagger UI: http://localhost:8082/swagger-ui.html · OpenAPI JSON: http://localhost:8082/v3/api-docs

Start-up order for the whole system: MongoDB → Account Service → Driver & Vehicle Service →
Ride Management Service → Fare & Payment Service.

## Tests
```
./mvnw clean test
```
51 unit tests (service rules, JWT validation, controller/security/error handling, distance
calculation). They need neither MongoDB nor environment variables. CI runs the same command
(`.github/workflows/driver-vehicle-service-ci.yml`).

Integrated scenarios: import `postman/driver-vehicle-service.postman_collection.json` and
`postman/driver-vehicle-service.local.postman_environment.json` into Postman, set the
environment variable `jwtSecret` to the same value as `JWT_SECRET`, and run the collection
(28 requests, 62 assertions: successful workflow + negative scenarios). Each run uses a fresh
test driver, so MongoDB does not need to be cleaned between runs.

## Authentication
`Authorization: Bearer <JWT>` – HS256 with claims `sub` (account id), `role`
(`PASSENGER`, `DRIVER`, `ADMIN`, `SERVICE`) and `exp`. Until the Account Service issues real tokens,
the Postman collection generates equivalent test tokens for the demo identities
`driver-<runId>`, `passenger-demo`, `admin-demo` and `ride-service` (role `SERVICE`).
In Swagger UI use **Authorize** and paste the token (without the word "Bearer").

## Endpoints (`/api/v1/drivers`)
| Method & path | Roles | Description |
|---|---|---|
| `POST /` | DRIVER | Create my profile + vehicle (starts OFFLINE) |
| `GET /me` | DRIVER | View my profile |
| `PUT /me` | DRIVER | Update profile and vehicle |
| `PATCH /me/service-area` | DRIVER | Change service area |
| `PATCH /me/availability` | DRIVER | Go `AVAILABLE` / `OFFLINE` |
| `PUT /me/location` | DRIVER | Update simulated location |
| `GET /available` | PASSENGER, ADMIN, SERVICE | Eligible drivers (`serviceArea`, `vehicleType`, `latitude`+`longitude`, `limit`) |
| `GET /{driverId}` | any authenticated | Get a driver |
| `GET /` | ADMIN | List drivers (`status` filter) |
| `POST /{driverId}/reserve` | SERVICE, ADMIN | AVAILABLE → BUSY (Ride Service, on assignment) |
| `POST /{driverId}/release` | SERVICE, ADMIN | BUSY → AVAILABLE (ride completed / cancelled) |

## Interservice interactions
Both are synchronous REST calls made by the Ride Management Service (a driver must be chosen
and confirmed before the passenger receives an answer, so a request/response call fits):
1. `GET /api/v1/drivers/available` – find eligible drivers near the pickup point.
2. `POST /api/v1/drivers/{id}/reserve` and `/release` – mark the chosen driver BUSY, then free again.

## Business rules
- One profile per account; licence number and plate number are unique (stored upper-case).
- A driver can only go `AVAILABLE` after a location has been set.
- `BUSY` can never be set by the driver; it is set by `reserve` and cleared by `release`.
- A BUSY driver cannot change availability; reserving a non-AVAILABLE driver returns `409`.
- Eligible drivers: status `AVAILABLE`, optional service-area / vehicle-type match. With pickup
  coordinates they are sorted nearest-first (haversine distance); otherwise longest-registered first.
- Optimistic locking (`@Version`) prevents two requests from reserving the same driver at once.

## Error format
```
{ "timestamp": "...", "status": 409, "error": "Conflict", "message": "...", "path": "...", "fieldErrors": {} }
