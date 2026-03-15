# Goaldone Backend — Gemini CLI Context

> This file is automatically loaded by Gemini CLI on every session in this project directory.
> It activates the right agency-agents specialists and gives them the full project context
> so you can pick up exactly where we left off without re-explaining anything.

---

## Project Context

Read `AGENT.md` in this directory first. It contains everything you need:
- Full tech stack and architecture decisions
- Database schema for all 7 tables
- API endpoint map with roles and auth requirements
- Security and tenant isolation rules
- Known issues and deliberate decisions (e.g. why `useBeanValidation` is disabled)
- Current build status and what has NOT been implemented yet

**Do not make any assumptions that contradict AGENT.md.**

---

## Active Agents

For this project, activate the following agency-agents specialists. Switch between them
as the task demands — each phase below specifies which agent to lead.

- **Backend Architect** — overall structure, layer separation, design decisions
- **Backend Developer** — implementation of entities, repositories, services, controllers
- **Security Engineer** — JWT implementation, Spring Security config, tenant isolation
- **Database Optimizer** — JPA entity mappings, query optimization, Liquibase changesets
- **API Designer** — keeping controllers aligned with the OpenAPI spec
- **Reality Checker** — before completing any phase, verify correctness and catch issues

---

## Coding Standards for This Project

Follow these rules on every file you write or edit:

- **Never touch** `target/generated-sources/` — these are regenerated on every build
- **Never add business logic to controllers** — controllers delegate to services only
- **Always filter by `organizationId` from JWT** — never from URL params or request body
- Use `@RequiredArgsConstructor` + Lombok — no manual constructors
- Use `Instant` for timestamps, `LocalDate` for dates, `UUID` for all IDs
- Services return domain objects or DTOs — never JPA entities directly to controllers
- Map entities → DTOs in a dedicated mapper method or class, not inline
- Every new Liquibase changeset goes in `src/main/resources/db/changelog/changes/`
  with the next number prefix and must include a `-- rollback` comment
- Run `./mvnw clean generate-sources compile` after any change to `openapi.yaml`
- Run `./mvnw clean` if Liquibase fails on H2 before retrying

---

## Build Plan — Step by Step

Work through these phases in order. **Complete and verify each phase before starting the next.**
After each phase, run `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` to confirm
the app still starts cleanly.

---

### Phase 1 — JPA Entities (Lead: Database Optimizer + Backend Developer)

Create `src/main/java/de/goaldone/backend/entity/` with one class per table.
Follow this exact order (foreign key dependencies):

1. `Organization.java`
2. `User.java` — references Organization
3. `RefreshToken.java` — references User
4. `Invitation.java` — references Organization + User
5. `Task.java` — references User + Organization + self (parentTaskId)
6. `Break.java` — references User
7. `ScheduleEntry.java` — references User + Organization + Task + Break

Rules for all entities:
- `@GeneratedValue(strategy = GenerationType.UUID)` for all IDs
- `@PrePersist` sets `createdAt = Instant.now()`
- `@PreUpdate` sets `updatedAt = Instant.now()` where the field exists
- `@Column(updatable = false)` on `createdAt`
- Enums stored as `@Enumerated(EnumType.STRING)`
- `ddl-auto: validate` is active — entity fields must exactly match the Liquibase schema

**Verify:** App starts, Hibernate validates schema without errors, H2 console shows all 7 tables.

---

### Phase 2 — Repositories (Lead: Backend Developer)

Create `src/main/java/de/goaldone/backend/repository/` with one interface per entity,
all extending `JpaRepository<Entity, UUID>`.

Add only the methods immediately needed:

```
UserRepository          → findByEmail, existsByEmail
OrganizationRepository  → findByAllowedDomain
RefreshTokenRepository  → findByTokenHash, deleteByUserIdAndRevokedAtIsNull
InvitationRepository    → findByToken, existsByEmailAndOrganizationId
TaskRepository          → findByOwnerIdAndOrganizationId (Pageable)
BreakRepository         → findByUserId
ScheduleEntryRepository → findByUserIdAndEntryDateBetween, deleteByUserIdAndEntryDateBetween
```

**Verify:** App still starts. No query compilation errors.

---

### Phase 3 — Security Foundation (Lead: Security Engineer)

Create `src/main/java/de/goaldone/backend/security/`.

**3a. JwtService**
- `generateAccessToken(User user)` → 15 min expiry, claims: `sub`=userId, `organizationId`, `role`
- `generateRefreshToken()` → UUID string, 7 days expiry
- `validateToken(String token)` → returns claims or throws
- `hashToken(String raw)` → SHA-256 hex string (for refresh token storage)
- Secret key loaded from `application.yml` → `app.jwt.secret` (min 256 bit)

**3b. JwtAuthFilter extends OncePerRequestFilter**
- Reads `Authorization: Bearer <token>` header
- Validates token via JwtService
- Populates `SecurityContextHolder` with a `UsernamePasswordAuthenticationToken`
  carrying userId as principal and a `GoaldoneUserDetails` object with orgId + role

**3c. SecurityConfig**
- Stateless sessions (`SessionCreationPolicy.STATELESS`)
- Permit without auth: `POST /auth/login`, `POST /auth/refresh`, `POST /auth/invitations/*/accept`
- All other requests require authentication
- Register `JwtAuthFilter` before `UsernamePasswordAuthenticationFilter`
- `PasswordEncoder` bean → `BCryptPasswordEncoder`

Add to `application.yml`:
```yaml
app:
  jwt:
    secret: ${JWT_SECRET:dev-secret-key-replace-in-production-min-256-bits}
    access-token-expiry: 900      # 15 minutes in seconds
    refresh-token-expiry: 604800  # 7 days in seconds
```

**Verify:** App starts. `POST /api/v1/auth/login` with wrong credentials returns 401.
`GET /api/v1/tasks` without token returns 401. No NPE on startup.

---

### Phase 4 — Auth Slice (Lead: Backend Developer + Security Engineer)

Create `AuthService` and `AuthController`. This is the first complete vertical slice.

**AuthService methods:**
- `login(LoginRequest)` → verify email+BCrypt password, generate both tokens,
  store hashed refresh token in `refresh_tokens`, return `LoginResponse`
- `refresh(RefreshRequest)` → validate hash exists + not revoked + not expired,
  revoke old token (`revoked_at = now()`), issue new token pair (token rotation), return `RefreshResponse`
- `logout(RefreshRequest)` → find by hash, set `revoked_at = now()`
- `changePassword(ChangePasswordRequest)` → verify current password, hash new password, save
- `acceptInvitation(String token, AcceptInvitationRequest)` → find invitation by token,
  check expiry, create User with ADMIN or USER role, delete invitation, return `LoginResponse`

**AuthController implements AuthApi** — thin delegation to AuthService only.

**Test manually with curl:**
```bash
# Login
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@test.de","password":"password123"}'

# Access protected endpoint with token
curl http://localhost:8080/api/v1/users/me \
  -H "Authorization: Bearer <token_from_above>"
```

**Verify:** Login returns `accessToken` + `refreshToken`. Protected endpoints work with token.
Invalid/expired tokens return 401. Wrong password returns 401.

---

### Phase 5 — Users & Organizations (Lead: Backend Developer)

**UsersController implements UsersApi**
- `getMyProfile()` → load user by ID from JWT, map to `UserResponse`
- `updateMyProfile(UpdateUserRequest)` → update firstName + lastName, return updated `UserResponse`
- `deleteMyAccount()` → hard delete user (CASCADE handles related records)

**OrganizationsController implements OrganizationsApi + MembersApi + InvitationsApi**
(all three interfaces can be implemented on one controller or split — your choice)

Key rule for all organization endpoints: **extract orgId from JWT, never from request**.

**OrganizationService:**
- `getMyOrganization(UUID orgId)` → findById, throw 404 if not found
- `updateSettings(UUID orgId, UpdateOrganizationSettingsRequest)` → update name/allowedDomain
- `listMembers(UUID orgId, Pageable)` → find users by organizationId, return `MemberPage`
- `removeMember(UUID orgId, UUID userId)` → verify user belongs to org, delete
- `updateMemberRole(UUID orgId, UUID userId, Role role)` → verify, update role
- `createInvitation(UUID orgId, String email)` → check no duplicate, generate UUID token,
  save to invitations table, send email (log to console for now — real email later)
- `listInvitations(UUID orgId, Pageable)` → return open invitations
- `revokeInvitation(UUID orgId, UUID invitationId)` → delete

**Verify:** All org/member/invitation endpoints work. An ADMIN from org A cannot see org B's data.

---

### Phase 6 — Tasks (Lead: Backend Developer)

**TasksController implements TasksApi** — full CRUD plus complete/reopen.

**TaskService:**
- `findAll(UUID ownerId, UUID orgId, Pageable, TaskStatus, LocalDate from, LocalDate to)`
- `create(CreateTaskRequest, UUID ownerId, UUID orgId)` → save, return `TaskResponse`
- `findById(UUID taskId, UUID ownerId)` → throw 403 if task.ownerId != ownerId
- `update(UUID taskId, UpdateTaskRequest, UUID ownerId)` → verify ownership, update, return
- `delete(UUID taskId, UUID ownerId)` → verify ownership, hard delete
- `complete(UUID taskId, UUID ownerId)` → set status=DONE, completedAt=now(), return
- `reopen(UUID taskId, UUID ownerId)` → set status=OPEN, completedAt=null, return

Ownership check: task.ownerId must equal the userId from JWT. Return 403 otherwise.

**Verify:** Full task CRUD works. User A cannot modify User B's tasks.

---

### Phase 7 — Breaks (Lead: Backend Developer)

**BreaksController implements BreaksApi** — simpler than tasks, no pagination.

**BreakService:**
- `findAll(UUID userId)` → list all breaks for user
- `create(CreateBreakRequest, UUID userId)` → save, return `BreakResponse`
- `update(UUID breakId, CreateBreakRequest, UUID userId)` → verify ownership, update
- `delete(UUID breakId, UUID userId)` → verify ownership, hard delete

**Verify:** Break CRUD works. Users cannot modify each other's breaks.

---

### Phase 8 — Schedule & Planning Algorithm (Lead: Backend Architect + Backend Developer)

**ScheduleController implements ScheduleApi**

**ScheduleService:**

`getSchedule(UUID userId, LocalDate from, LocalDate to)`:
- Load all `schedule_entries` for userId in date range
- Map to `ScheduleResponse` with list of `ScheduleEntry` objects

`generateSchedule(UUID userId, UUID orgId, GenerateScheduleRequest)`:
1. Delete existing entries for userId in date range
2. Load all OPEN + IN_PROGRESS tasks for userId ordered by (deadline ASC, cognitiveLoad DESC)
3. Load all breaks for userId
4. For each working day in range (Mon-Fri):
   a. Block break slots from the user's break config
   b. Fill remaining time with tasks up to `maxDailyWorkMinutes` (default 240)
   c. Schedule HIGH cognitive_load tasks in morning slots first
   d. If a task's remaining minutes exceed available time for the day, split it:
      create a child task with a portion of the duration, set `parent_task_id`
5. Insert `schedule_entries` for all scheduled blocks
6. Return the full `ScheduleResponse`

**Verify:** Generate schedule for a week. Check H2 console that `schedule_entries` are inserted.
Re-running for the same range replaces the old entries.

---

### Phase 9 — Admin Panel (Lead: Backend Developer + Security Engineer)

**AdminController implements AdminApi** — SUPER_ADMIN only, enforce via `@PreAuthorize`.

Enable method security in `SecurityConfig`:
```java
@EnableMethodSecurity
```

**AdminController:**
```java
@PreAuthorize("hasRole('SUPER_ADMIN')")
public ResponseEntity<OrganizationResponse> createOrganization(...) { ... }
```

**AdminService:**
- `listOrganizations(Pageable)` → all orgs, no tenant filter
- `createOrganization(CreateOrganizationRequest)` → create org, create initial invite for adminEmail
- `deleteOrganization(UUID orgId)` → hard delete (CASCADE handles everything)
- `addSuperAdmin(String email)` → find user by email, set role=SUPER_ADMIN

**Verify:** SUPER_ADMIN can list all organizations. USER and ADMIN get 403 on all `/admin/**` endpoints.

---

### Phase 10 — Error Handling (Lead: Reality Checker + Backend Developer)

Add a global exception handler implementing RFC 9457 Problem Details:

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    // Handle EntityNotFoundException   → 404
    // Handle AccessDeniedException     → 403
    // Handle ConstraintViolation       → 409
    // Handle MethodArgumentNotValid    → 400 with FieldError list
    // Handle all others                → 500
    // Always return: Content-Type: application/problem+json
    // Always return: ProblemDetail schema from generated models
}
```

**Verify:** Missing task returns `application/problem+json` 404. Duplicate invitation returns 409.

---

## How to Use This File

In any Gemini CLI session in this directory, just tell Gemini which phase to work on:

```
Work on Phase 1 — create all JPA entity classes following the plan in GEMINI.md
```

```
We finished Phase 4. Move to Phase 5 — Users and Organizations.
```

```
Use the Reality Checker agent to verify Phase 6 is complete and correct before we continue.
```

```
Use the Security Engineer agent to review the JwtAuthFilter implementation.
```

Gemini has full tool access to read, write, and execute in this project directory.
It can read AGENT.md for architecture context, run `./mvnw` commands to verify builds,
and write files directly into the correct packages.
