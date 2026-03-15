# AGENT.md — Goaldone Backend

> This file gives an AI agent everything it needs to understand, navigate, and contribute to the Goaldone backend codebase without prior context. Read it fully before making any changes.

---

## Project Overview

**Goaldone** is a browser-based task planning system for organizations. It is a university project (TINF2024, DHBW Stuttgart, Software Engineering I, Spring 2026).

The system allows organizations to register on a multi-tenant platform. Users within an organization can create tasks with attributes like estimated duration, cognitive load, and deadlines. A planning algorithm automatically distributes tasks across working days, respecting personal breaks and a configurable maximum daily cognitive workload (~4 hours based on research cited in the project brief).

The backend is a **Spring Boot REST API**, API-first designed using OpenAPI 3.0. The frontend is Angular (separate repository, not covered here).

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.4.3 |
| API Design | OpenAPI 3.0 (API-first, `openapi.yaml` is the source of truth) |
| Code Generation | openapi-generator-maven-plugin 7.12.0 (`interfaceOnly=true`) |
| Security | Spring Security + JWT (jjwt 0.12.6) |
| Persistence | Spring Data JPA + Hibernate |
| Database (dev) | H2 in-memory (`jdbc:h2:mem:goaldone;MODE=PostgreSQL`) |
| Database (prod) | PostgreSQL |
| Migrations | Liquibase (SQL format, master YAML) |
| Build tool | Maven (via `./mvnw`) |
| Boilerplate reduction | Lombok (`@Data`, `@Builder`, `@RequiredArgsConstructor` etc.) |
| API Documentation | springdoc-openapi (Swagger UI at `/swagger-ui.html`) |

---

## Repository Structure

```
goaldone_prototype_backend/
├── src/
│   ├── main/
│   │   ├── java/de/goaldone/backend/
│   │   │   ├── GoaldonePrototypeBackendApplication.java   ← main class
│   │   │   ├── controller/      ← @RestController classes (implement generated *Api interfaces)
│   │   │   ├── service/         ← Business logic (@Service)
│   │   │   ├── repository/      ← Spring Data JPA interfaces
│   │   │   ├── entity/          ← JPA @Entity classes (map to DB tables)
│   │   │   └── security/        ← SecurityConfig, JwtService, JwtAuthFilter
│   │   └── resources/
│   │       ├── openapi.yaml                        ← SINGLE SOURCE OF TRUTH for the API
│   │       ├── application.yml                     ← shared config
│   │       ├── application-dev.yml                 ← H2 + Liquibase dev config
│   │       ├── application-prod.yml                ← PostgreSQL prod config
│   │       └── db/changelog/
│   │           ├── db.changelog-master.yaml        ← Liquibase master (includeAll)
│   │           └── changes/
│   │               ├── 0001-create-organizations.sql
│   │               ├── 0002-create-users.sql       ← also creates refresh_tokens
│   │               ├── 0003-create-invitations.sql
│   │               ├── 0004-create-tasks.sql
│   │               ├── 0005-create-breaks.sql
│   │               └── 0006-create-schedule-entries.sql
│   └── test/
│       └── java/de/goaldone/backend/
├── target/
│   └── generated-sources/openapi/src/main/java/de/goaldone/backend/
│       ├── api/      ← Generated interfaces (*Api.java) — NEVER edit these manually
│       └── model/    ← Generated DTOs — NEVER edit these manually
└── pom.xml
```

---

## The Golden Rule: openapi.yaml is the Source of Truth

**Never manually edit files under `target/generated-sources/`.** They are regenerated on every build and all changes will be lost.

The correct workflow when the API changes:
1. Edit `src/main/resources/openapi.yaml`
2. Run `./mvnw clean generate-sources`
3. The new interfaces and DTOs are regenerated automatically
4. Update your `@RestController` classes to implement any new methods

---

## How Code Generation Works

The `openapi-generator-maven-plugin` runs on every build with `interfaceOnly=true`. It generates:

- `*Api.java` interfaces — one per OpenAPI tag, with all `@RequestMapping` annotations already applied
- DTO model classes (`LoginRequest`, `TaskResponse`, etc.) — annotated with Lombok `@Data @Builder @NoArgsConstructor @AllArgsConstructor`

Your controllers implement these interfaces:

```java
@RestController
@RequiredArgsConstructor
public class TasksController implements TasksApi {

    private final TaskService taskService;

    @Override
    public ResponseEntity<TaskResponse> createTask(CreateTaskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(taskService.create(request));
    }
    // ... all other methods from TasksApi
}
```

**Key plugin config decisions:**
- `interfaceOnly=true` — avoids the delegate pattern which is broken in generator 7.x with `@Valid`
- `cleanupOutput=true` — wipes generated output before each run to avoid stale controller files
- `useBeanValidation` and `performBeanValidation` are intentionally **omitted** — they cause signature mismatches between the generated `*Api` interface and controllers
- `dateLibrary=java8` — uses `java.time.LocalDate`, `java.time.Instant` etc.

---

## API Design

**Base URL:** `https://goaldone.de/api/v1` (prod) / `http://localhost:8080/api/v1` (dev)

**Versioning:** Version is defined once in the `servers` block of `openapi.yaml`, not per endpoint. In Spring Boot this is configured via `server.servlet.context-path=/api/v1`.

**Authentication:** JWT Bearer tokens. Include as `Authorization: Bearer <token>` on every request except public endpoints.

**Error format:** RFC 9457 Problem Details — all errors return `Content-Type: application/problem+json`.

**Pagination:** `?page=0&size=20` on all list endpoints. Responses include a `PageMetadata` object with `totalElements` and `totalPages`.

### Endpoints by Tag

| Tag | Endpoints | Auth required | Min role |
|---|---|---|---|
| `auth` | POST /auth/login, /auth/refresh, /auth/logout, /auth/password/change, /auth/invitations/{token}/accept | No (login, refresh, accept) / Yes (others) | — |
| `users` | GET/PUT/DELETE /users/me | Yes | USER |
| `organizations` | GET /organizations/me, PUT /organizations/me/settings | Yes | ADMIN |
| `members` | GET/DELETE /organizations/me/members, PATCH /organizations/me/members/{userId}/role | Yes | ADMIN |
| `invitations` | GET/POST /organizations/me/invitations, DELETE /organizations/me/invitations/{id} | Yes | ADMIN |
| `tasks` | Full CRUD on /tasks and /tasks/{taskId}, plus PATCH /tasks/{taskId}/complete and /reopen | Yes | USER |
| `breaks` | Full CRUD on /breaks and /breaks/{breakId} | Yes | USER |
| `schedule` | GET /schedule, POST /schedule/generate | Yes | USER |
| `admin` | GET/POST /admin/organizations, DELETE /admin/organizations/{orgId}, POST /admin/super-admins | Yes | SUPER_ADMIN |

---

## Authentication & Security Architecture

### JWT Structure
```
Claims:
  sub           → user UUID
  organizationId → organization UUID (null for SUPER_ADMIN without org)
  role          → SUPER_ADMIN | ADMIN | USER
  iat           → issued at
  exp           → expiry
```

- **Access Token:** 15 minutes lifetime. Sent as `Authorization: Bearer <token>`.
- **Refresh Token:** 7 days lifetime. SHA-256 hash stored in `refresh_tokens` table (raw token never persisted). Token rotation on every refresh — old token is revoked immediately.

### RBAC Roles
- `SUPER_ADMIN` — platform-wide: create/delete organizations, promote users to super admin
- `ADMIN` — own organization only: manage members, invitations, org settings
- `USER` — own data only: tasks, breaks, schedule

### Tenant Isolation (Critical)
The `organizationId` is **always read from the JWT**, never from URL parameters or request bodies. Every database query for organization-scoped resources must include a `WHERE organization_id = :jwtOrganizationId` filter. An ADMIN can never access another organization's data — attempting to do so always returns `403 Forbidden`. Only `SUPER_ADMIN` can operate cross-organization, and only via `/admin/**` endpoints.

The `/organizations/me/...` URL convention makes this explicit — `me` signals that the org comes from JWT context, not a user-supplied ID.

---

## Database Schema

7 tables, all using UUID primary keys (`gen_random_uuid()`).

### Table Overview

```
organizations
  id, name (unique), admin_email, allowed_domain (nullable), created_at

users
  id, email (unique), first_name, last_name, password_hash (BCrypt),
  role (SUPER_ADMIN|ADMIN|USER), organization_id (FK → organizations, nullable), created_at

refresh_tokens
  id, user_id (FK → users CASCADE), token_hash (unique, SHA-256 of raw token),
  expires_at, revoked_at (nullable), created_at

invitations
  id, email, organization_id (FK → organizations CASCADE),
  invited_by (FK → users SET NULL), token (unique UUID), expires_at (now+48h), created_at

tasks
  id, title, description (nullable), status (OPEN|IN_PROGRESS|DONE),
  cognitive_load (LOW|MEDIUM|HIGH), estimated_duration_minutes,
  deadline (nullable), recurrence_type (DAILY|WEEKLY|MONTHLY, nullable),
  recurrence_interval (nullable), parent_task_id (self-FK, nullable — for task splitting),
  owner_id (FK → users CASCADE), organization_id (FK → organizations CASCADE),
  completed_at (nullable, set when status=DONE), created_at, updated_at

breaks
  id, label, start_time (TIME), end_time (TIME),
  recurrence_type (DAILY|WEEKLY|MONTHLY), recurrence_interval,
  user_id (FK → users CASCADE), created_at, updated_at

schedule_entries
  id, user_id (FK → users CASCADE), organization_id (FK → organizations CASCADE),
  task_id (FK → tasks CASCADE, nullable), break_id (FK → breaks CASCADE, nullable),
  entry_date (DATE), start_time (TIME), end_time (TIME),
  entry_type (TASK|BREAK), generated_at, created_at
```

### Key Constraints to Know
- `tasks.status = 'DONE'` requires `completed_at IS NOT NULL` (DB-level check constraint)
- `tasks.recurrence_type` and `tasks.recurrence_interval` must both be set or both be null
- `schedule_entries.task_id` and `schedule_entries.break_id` are mutually exclusive — exactly one must be set, enforced by a check constraint matching `entry_type`
- `schedule_entries` has a unique constraint on `(user_id, entry_date, start_time)` — no double-booking
- `invitations` has a unique constraint on `(email, organization_id)` — one open invite per user per org

### Liquibase Rules
- **Never edit an already-applied changeset.** Liquibase checksums every changeset and will refuse to start if a checksum changes.
- Always add a new file with the next number prefix instead: `0007-your-change.sql`
- Every changeset must include a `-- rollback` comment
- Partial indexes (`WHERE` clause) are removed from dev changesets — H2 does not support them. They exist on the PostgreSQL prod schema only.
- If Liquibase fails mid-migration on H2 (dev), run `./mvnw clean` before retrying — this wipes the in-memory state.

---

## Domain Logic

### Task Splitting
When `estimated_duration_minutes > 480` (one full working day), the planning algorithm splits the task into child tasks. Child tasks reference the original via `parent_task_id`. This is handled in the `ScheduleService`, not at creation time.

### Recurrence Model (MVP)
Simple model: `recurrence_type` (DAILY/WEEKLY/MONTHLY) + `recurrence_interval` (every N units). Designed to be extended to iCal RRULE standard without breaking changes — just add an optional `rrule` field later.

### Planning Algorithm
Triggered by `POST /schedule/generate` (synchronous — the API waits for completion). Key rules:
- Maximum ~240 minutes (4h) of cognitive work per day by default (configurable via `maxDailyWorkMinutes` in the request)
- User's breaks are loaded first and block time slots
- Tasks are prioritized by `cognitiveLoad` (HIGH tasks scheduled in morning slots) and `deadline`
- Tasks longer than one working day are automatically split
- Generating a new plan deletes existing `schedule_entries` for the requested date range before inserting new ones

---

## Profiles

| Profile | Database | How to activate |
|---|---|---|
| `dev` | H2 in-memory | `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` |
| `prod` | PostgreSQL | `java -jar backend.jar --spring.profiles.active=prod` |

**Dev H2 console:** `http://localhost:8080/h2-console`
- JDBC URL: `jdbc:h2:mem:goaldone`
- Username: `sa`
- Password: *(empty)*

**Prod environment variables required:**
```
DB_USERNAME=...
DB_PASSWORD=...
```

---

## Controller Implementation Pattern

```java
// 1. Package: de.goaldone.backend.controller
// 2. Implement the generated *Api interface
// 3. Inject the corresponding *Service
// 4. Delegate immediately — no business logic in controllers

@RestController
@RequiredArgsConstructor
public class TasksController implements TasksApi {

    private final TaskService taskService;

    @Override
    public ResponseEntity<TaskResponse> createTask(CreateTaskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(taskService.create(request));
    }

    @Override
    public ResponseEntity<TaskPage> listTasks(
            Integer page, Integer size,
            TaskStatus status, LocalDate from, LocalDate to) {
        return ResponseEntity.ok(
                taskService.findAll(page, size, status, from, to));
    }
}
```

**Accessing the authenticated user in a service:**
```java
// SecurityContext is populated by JwtAuthFilter on every request
SecurityContext ctx = SecurityContextHolder.getContext();
String userId = ctx.getAuthentication().getName();           // user UUID
String orgId  = /* custom claim from JwtAuthFilter */;      // org UUID
```

---

## What is NOT Yet Implemented

As of project start, only the project skeleton exists. Nothing in `controller/`, `service/`, `repository/`, or `entity/` has been written yet. The recommended build order is:

1. `Organization` + `User` JPA entities
2. `UserRepository`, `OrganizationRepository`
3. `JwtService` (generate + validate tokens)
4. `SecurityConfig` (permit `/auth/**`, secure everything else)
5. `JwtAuthFilter` (populate `SecurityContext` from token)
6. `AuthService` + `AuthController` (login, refresh, logout)
7. Test with curl / Postman — verify login returns JWT and protected endpoints return 401
8. `Task` entity + `TaskRepository` + `TaskService` + `TasksController`
9. Remaining controllers in order: Breaks → Organizations/Members → Schedule → Admin

---

## Common Commands

```bash
# Run in dev mode
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Clean + regenerate API code + compile
./mvnw clean generate-sources compile

# Full build including tests
./mvnw clean verify

# If Liquibase fails mid-migration (always do this before retrying)
./mvnw clean

# Package for production
./mvnw clean package -Dspring-boot.run.profiles=prod
```

---

## Known Issues & Decisions

| Issue | Decision |
|---|---|
| `useBeanValidation` + `performBeanValidation` break compilation with delegate pattern in generator 7.x | Both options are omitted. Validation is handled manually in service layer. |
| H2 does not support partial indexes (`WHERE` clause on `CREATE INDEX`) | Partial indexes removed from all changesets. Standard indexes used instead. PostgreSQL-only optimizations can be added later with `-- context:postgresql`. |
| Spring Boot 4.x is pre-release and incompatible with current third-party dependencies | Pinned to Spring Boot 3.4.3 (current stable). |
| `delegatePattern=true` causes `@Valid` signature mismatch in controller/delegate | Switched to `interfaceOnly=true`. Controllers implement `*Api` directly. |
