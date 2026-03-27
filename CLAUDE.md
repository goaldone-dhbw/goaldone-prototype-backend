# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Quick Start

**Project:** Goaldone backend – Spring Boot REST API for a task planning system (DHBW SE I project, Spring 2026)

**Key files:**
- `src/main/resources/openapi.yaml` — the API contract (source of truth)
- `AGENT.md` — detailed architecture, database schema, security model

**Common commands:**

```bash
# Start in dev mode (H2 in-memory database)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Regenerate API interfaces/DTOs from openapi.yaml
./mvnw clean generate-sources

# Run tests (includes integration tests against test H2 database)
./mvnw verify

# Run a single test
./mvnw test -Dtest=RBACIntegrationTest

# Run only unit tests in a service
./mvnw test -Dtest=ScheduleServiceTest

# If Liquibase migration fails in dev, clean and restart
./mvnw clean
```

## Development Workflow

### When Adding/Changing API Endpoints

⚠️ **Remember: openapi.yaml is the only place to define API changes!**

1. **Edit `src/main/resources/openapi.yaml`** — add or update the endpoint definition
2. **Run `./mvnw clean generate-sources`** — this regenerates `*Api.java` interfaces and DTO classes in `target/generated-sources/`
   - Do NOT hand-edit these generated files — they will be obliterated on the next build
3. **Update or create a `@RestController`** in `src/main/java/de/goaldone/backend/controller/` to implement the new `*Api` interface
4. **Add service logic** in the corresponding `@Service` class in `src/main/java/de/goaldone/backend/service/`
5. **Write tests** following the patterns in `src/test/java/de/goaldone/backend/`

### When Adding Database Tables

1. Create a new SQL changeset in `src/main/resources/db/changelog/changes/` with the next sequential number
2. **Never edit an already-applied changeset** — Liquibase will fail due to checksum mismatch
3. Always include a `-- rollback` comment in the changeset
4. Create the corresponding JPA `@Entity` class in `src/main/java/de/goaldone/backend/entity/`
5. Create a `@Repository` interface in `src/main/java/de/goaldone/backend/repository/`

### Code Organization

```
src/main/java/de/goaldone/backend/
├── controller/       → REST endpoints (implement generated *Api interfaces)
├── service/          → Business logic (transactions, validation, planning)
├── repository/       → Spring Data JPA interfaces
├── entity/           → JPA @Entity classes mapped to database tables
├── security/         → JWT tokens, auth filter, RBAC
├── config/           → Spring configuration (Jackson, CORS, properties)
├── scheduler/        → Scheduled tasks (@Scheduled)
├── exception/        → Custom exception classes for error handling
└── entity/enums/     → Enums used across entities (Role, TaskStatus, etc.)
```

## 🔴 The Golden Rule: openapi.yaml is the Source of Truth

**`src/main/resources/openapi.yaml` is the SINGLE source of truth for the API.**

When you need to change the API structure:
1. **Edit `openapi.yaml`** — add/update endpoint definitions here
2. **Run `./mvnw clean generate-sources`** — regenerates all interfaces and DTOs
3. **Implement the new interface** in a `@RestController`

**NEVER manually edit anything in `target/generated-sources/`** — they are **completely wiped on every build** and all your changes will be lost. The openapi-generator plugin recreates them from `openapi.yaml` every time.

---

## Critical Rules

### 1. Tenant Isolation (Always)
- **Never read `organizationId` from URL or request body.** Always read from the JWT via `SecurityContext`.
- Every database query for org-scoped resources must include `WHERE organization_id = :jwtOrganizationId`.
- Use the `/organizations/me/...` URL convention to signal context comes from JWT.
- Return `403 Forbidden` if a user tries to access another organization's data.

### 2. Authentication & Roles
- `SUPER_ADMIN` — platform-wide operations (`/admin/**` endpoints only)
- `ADMIN` — own organization: manage members, invitations, settings
- `USER` — own data only: tasks, breaks, schedule
- Access is enforced via `SecurityContext` + custom authorization logic (not just Spring Security roles)

### 4. Database Migrations
- Liquibase is applied on startup via `db.changelog-master.yaml`
- Never modify an applied changeset — create a new one instead
- Test profile uses same migrations as dev (H2)
- Include both forward (`CREATE ...`) and rollback SQL

## Testing

All tests inherit from `BaseIntegrationTest`:
- Runs with `@SpringBootTest + @AutoConfigureMockMvc`
- Uses in-memory H2 database with test profile (`application-test.yml`)
- `@Transactional` ensures rollback after each test
- `JavaMailSender` is mocked

**Authentication in tests:**
```java
// Call this helper to authenticate a user for subsequent requests
authenticateAs(user);

// Then use mockMvc.perform() — the user is in SecurityContext
mockMvc.perform(get("/api/v1/tasks"))
    .andExpect(status().isOk());
```

**Patterns:**
- Integration tests use `MockMvc` and test full request/response cycles
- Unit tests (`ScheduleServiceTest`, `WorkingHoursServiceTest`) mock repositories
- Tenant isolation is tested in `TenantIsolationIntegrationTest`
- RBAC is tested in `RBACIntegrationTest`

## Key Implementation Notes

### ScheduleService (Planning Algorithm)
- Triggered by `POST /schedule/generate` (synchronous)
- Deletes existing schedule entries for the date range, then inserts new ones
- Respects user's breaks and max daily cognitive workload (~240 minutes default)
- Tasks longer than one working day are auto-split
- See `ScheduleServiceTest` for algorithm test patterns

### WorkingHoursService
- Stores custom working hour rules per user (e.g., "Mon–Fri 8–17")
- Used by the planning algorithm to determine available time slots
- See `WorkingHoursIntegrationTest` for usage examples

### Breaks
- User-defined breaks (lunch, focus time, etc.) with recurrence (DAILY/WEEKLY/MONTHLY)
- Loaded by the planning algorithm to block time slots
- Stored in `breaks` table, validated in `BreakService`

### Task Status Workflow
- `OPEN` → `IN_PROGRESS` → `DONE` (or back to `OPEN`)
- `completed_at` timestamp is set when status changes to `DONE`
- Completion is handled by `PATCH /tasks/{taskId}/complete`

## Common Patterns

### Service Method Template
```java
@Service
@RequiredArgsConstructor
public class ExampleService {
    private final ExampleRepository repo;
    private final SecurityService security;  // inject if you need auth context

    public ExampleResponse create(CreateExampleRequest req) {
        String userId = security.getCurrentUserId();
        String orgId = security.getCurrentOrganizationId();

        // validate
        // business logic
        // save
        return mapToResponse(entity);
    }
}
```

### Controller Template
```java
@RestController
@RequiredArgsConstructor
public class ExampleController implements ExampleApi {
    private final ExampleService service;

    @Override
    public ResponseEntity<ExampleResponse> create(CreateExampleRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(req));
    }
}
```

### Test Template
```java
public class ExampleIntegrationTest extends BaseIntegrationTest {
    // Inject repositories if needed
    @Autowired private ExampleRepository repo;

    @Test
    public void shouldCreateExample() throws Exception {
        User user = createUser("user@example.com");
        authenticateAs(user);

        var request = new CreateExampleRequest()
                .name("test");

        mockMvc.perform(post("/api/v1/example")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());
    }
}
```

## Environment & Profiles

**Dev (default):**
- H2 in-memory database: `jdbc:h2:mem:goaldone`
- H2 console at `http://localhost:8080/h2-console`
- Mail SMTP to localhost:1026 (configure mail catcher if needed)
- Start with: `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`

**Test:**
- Same as dev, but rollback after each `@Transactional` test
- Database is fresh for each test class (via `@SpringBootTest`)

**Production:**
- PostgreSQL (via environment variables: `DB_USERNAME`, `DB_PASSWORD`)
- JWT cookies set with `Secure` and `SameSite` flags
- Run: `java -jar backend.jar --spring.profiles.active=prod`

## Swagger/OpenAPI

- Auto-generated documentation at `http://localhost:8080/swagger-ui.html`
- Pull from `openapi.yaml` via `springdoc-openapi`
- Update `openapi.yaml`, restart, refresh browser

## Exception Handling

Custom exception classes are mapped to HTTP status codes via `GlobalExceptionHandler`:
- `ResourceNotFoundException` → 404
- `ConflictException` → 409
- `ValidationException` → 400
- `GoneException` → 410

Return `ProblemDetail` (RFC 9457) — Spring handles serialization automatically.

## Java Version & Dependencies

- Java 21
- Spring Boot 3.4.3
- See `pom.xml` for all dependencies
- Lombok is used for `@Data`, `@Builder`, `@RequiredArgsConstructor`

## References

- **Full architecture & schema:** See `AGENT.md`
- **API contract:** `src/main/resources/openapi.yaml`
- **Database migrations:** `src/main/resources/db/changelog/changes/`
