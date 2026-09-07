# Work Order API

A small Spring Boot REST API for creating and managing service work orders. I built this project as a practical backend demo covering REST endpoints, validation, persistence, basic authentication, testing, and API documentation.

## Features

- REST CRUD endpoints for work orders
- Status and priority filtering with pagination
- PostgreSQL persistence through Spring Data JPA
- Request validation and centralized API error handling
- HTTP Basic protection for write operations
- Public Swagger/OpenAPI documentation
- Actuator health endpoint
- JUnit 5, Mockito, and MockMvc tests using H2
- Fictional demo records inserted only when the database is empty

## Tech Stack

- Java 21
- Spring Boot 3.5
- Spring Web
- Spring Data JPA
- PostgreSQL
- Spring Security
- Spring Boot Actuator
- JUnit 5 and MockMvc
- H2 for automated tests
- springdoc-openapi / Swagger UI
- Maven and Maven Wrapper

## Architecture

```text
Client
  |
  v
WorkOrderController
  |
  v
WorkOrderService
  |
  v
WorkOrderRepository
  |
  v
PostgreSQL
```

- `WorkOrderController` handles HTTP requests, validation, and response status codes.
- `WorkOrderService` contains application operations and maps entities to response DTOs.
- `WorkOrderRepository` provides persistence and the small set of filter queries.
- PostgreSQL stores work orders when the application runs normally; tests use H2 instead.

## API Endpoints

| Method | Endpoint | Description | Authentication |
| --- | --- | --- | --- |
| `GET` | `/api/work-orders` | List and filter work orders | Public |
| `GET` | `/api/work-orders/{id}` | Get one work order | Public |
| `POST` | `/api/work-orders` | Create a work order | HTTP Basic |
| `PUT` | `/api/work-orders/{id}` | Update work order details | HTTP Basic |
| `PATCH` | `/api/work-orders/{id}/status` | Update only the status | HTTP Basic |
| `DELETE` | `/api/work-orders/{id}` | Delete a work order | HTTP Basic |

New work orders always start with the `OPEN` status. Valid status values are `OPEN`, `IN_PROGRESS`, `COMPLETED`, and `CANCELLED`; valid priorities are `LOW`, `MEDIUM`, and `HIGH`.

## Filtering

Filters can be used separately or together, along with Spring's `page` and `size` parameters:

```text
GET /api/work-orders?status=OPEN
GET /api/work-orders?priority=HIGH
GET /api/work-orders?status=OPEN&priority=HIGH
GET /api/work-orders?page=0&size=10
```

## Running Tests

macOS/Linux:

```bash
./mvnw test
```

Windows:

```powershell
mvnw.cmd test
```

Tests use an in-memory H2 database and do not require PostgreSQL or another external service.

## Building

macOS/Linux:

```bash
./mvnw clean package
```

Windows:

```powershell
mvnw.cmd clean package
```

## Configuration

The application reads these environment variables:

| Variable | Purpose | Development fallback |
| --- | --- | --- |
| `DB_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/workorder` |
| `DB_USERNAME` | PostgreSQL username | `workorder_app` |
| `DB_PASSWORD` | PostgreSQL password | Empty; set this locally |
| `APP_USERNAME` | HTTP Basic username | `admin` |
| `APP_PASSWORD` | HTTP Basic password | `change-me`; replace before use |

Copy `.env.example` as a reference for the expected values. Spring Boot does not load `.env` files automatically, so provide the variables through your shell, IDE run configuration, or deployment service. The `.env` file is ignored by Git.

## Running Locally

1. Install Java 21 and make sure PostgreSQL is running.
2. Create a `workorder` database and a PostgreSQL user that can manage its schema.
3. Set `DB_PASSWORD`, `APP_USERNAME`, and `APP_PASSWORD`. Set the other variables too if your database does not use the documented defaults.
4. Start the application:

```bash
./mvnw spring-boot:run
```

On Windows, run `mvnw.cmd spring-boot:run` instead. The application uses Hibernate `ddl-auto=update` to manage the small demo schema.

## Example Requests

Public list request:

```bash
curl http://localhost:8080/api/work-orders
```

Authenticated creation:

```bash
curl -u username:password \
  -X POST \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Replace failed network switch",
    "description": "Connectivity is intermittent.",
    "priority": "HIGH",
    "requesterEmail": "operations@example.com",
    "dueDate": "2026-09-15"
  }' \
  http://localhost:8080/api/work-orders
```

Status update:

```bash
curl -u username:password \
  -X PATCH \
  -H "Content-Type: application/json" \
  -d '{"status":"IN_PROGRESS"}' \
  http://localhost:8080/api/work-orders/1/status
```

## Swagger

Local: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)

The OpenAPI JSON is available at [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs).

## Health

Local: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)

Only the Actuator health endpoint is exposed over HTTP.

## Live Demo

Live API: Coming soon

Swagger: Coming soon

## Deployment

The application is designed to run as a Spring Boot JAR behind Nginx with PostgreSQL.

## License

This project is available under the MIT License.
