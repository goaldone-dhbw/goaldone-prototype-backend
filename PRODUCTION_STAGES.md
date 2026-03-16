# Goaldone — Production Readiness Stages for Gemini CLI

These prompts continue where the 10 implementation phases left off.
Each prompt is self-contained — paste it directly into Gemini CLI.
Work through them in order; each stage builds on the previous one.

---

## Stage 11 — Email Service (Required for invitations)

```
Read AGENT.md and GEMINI.md. Use the Backend Developer agent.

## Task: Implement email delivery via Spring Mail

The invitation system currently saves tokens to the database but never sends
emails. This stage wires up real email delivery with a dev-safe fallback.

### Step 1 — Add dependencies to pom.xml
Add Spring Mail:
  <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-mail</artifactId>
  </dependency>

### Step 2 — Configure mail per profile

In application.yml (shared defaults):
  app:
    mail:
      from: noreply@goaldone.de
      invitation-link-base: ${APP_BASE_URL:http://localhost:4200}/auth/accept-invitation

In application-dev.yml — use Mailpit (local SMTP catcher, zero config):
  spring:
    mail:
      host: localhost
      port: 1025
      properties:
        mail.smtp.auth: false
        mail.smtp.starttls.enable: false

In application-prod.yml — read from environment variables:
  spring:
    mail:
      host: ${MAIL_HOST}
      port: ${MAIL_PORT:587}
      username: ${MAIL_USERNAME}
      password: ${MAIL_PASSWORD}
      properties:
        mail.smtp.auth: true
        mail.smtp.starttls.enable: true

### Step 3 — Create EmailService
Create src/main/java/de/goaldone/backend/service/EmailService.java:
- Inject JavaMailSender and @Value("${app.mail.from}") + @Value("${app.mail.invitation-link-base}")
- Method: sendInvitationEmail(String toEmail, String token, String organizationName)
  - Subject: "You have been invited to join [organizationName] on Goaldone"
  - Body: plain text (no HTML for now) containing:
      "You have been invited to join [organizationName].
       Click the link below to create your account:
       [app.mail.invitation-link-base]/[token]
       This link expires in 48 hours."
  - Use SimpleMailMessage — no templates needed at this stage
- Method: sendPasswordResetEmail(String toEmail, String resetToken)
  - Stub only for now — just log "Password reset email would be sent to: [email]"
  - Will be fully implemented in a later stage

### Step 4 — Wire EmailService into OrganizationService
Find the createInvitation method in OrganizationService.
After saving the invitation to the database, call:
  emailService.sendInvitationEmail(invitation.getEmail(), invitation.getToken(), organization.getName())
Wrap the email call in a try-catch — a mail failure must NOT roll back the
invitation creation. Log the error and continue:
  log.error("Failed to send invitation email to {}: {}", email, e.getMessage());

### Step 5 — Dev mail catcher setup
Add a comment to application-dev.yml explaining how to start Mailpit:
  # Start local mail catcher: docker run -p 1025:1025 -p 8025:8025 axllent/mailpit
  # View captured emails at: http://localhost:8025

### Step 6 — Verify
1. Start Mailpit: docker run -d -p 1025:1025 -p 8025:8025 axllent/mailpit
2. Run ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
3. Call POST /api/v1/organizations/me/invitations with an admin token
4. Open http://localhost:8025 and confirm the invitation email arrived
5. Confirm the invitation link contains the correct token
6. Confirm that if Mailpit is not running, the invitation is still saved and the
   app logs the mail error without throwing an exception
```

---

## Stage 12 — Dev Test Helper Endpoint

```
Read AGENT.md and GEMINI.md. Use the Backend Developer agent.

## Task: Add a dev-only endpoint that exposes invitation tokens for automated testing

The test suite (test-api.sh) needs to test the full invitation acceptance flow
without a real email server. The solution is a @Profile("dev") controller that
exposes invitation tokens directly — never compiled into production builds.

### Step 1 — Add findFirstByEmailOrderByCreatedAtDesc to InvitationRepository
Add this method signature:
  Optional<Invitation> findFirstByEmailOrderByCreatedAtDesc(String email);

### Step 2 — Create DevTestController
Create src/main/java/de/goaldone/backend/controller/DevTestController.java:

  @RestController
  @RequestMapping("/dev")
  @Profile("dev")
  @RequiredArgsConstructor
  public class DevTestController {

      private final InvitationRepository invitationRepository;

      @GetMapping("/invitations/token")
      public ResponseEntity<Map<String, String>> getInvitationToken(@RequestParam String email) {
          return invitationRepository
              .findFirstByEmailOrderByCreatedAtDesc(email)
              .map(inv -> ResponseEntity.ok(Map.of(
                  "token", inv.getToken(),
                  "email", inv.getEmail(),
                  "expiresAt", inv.getExpiresAt().toString()
              )))
              .orElse(ResponseEntity.notFound().build());
      }
  }

### Step 3 — Exclude /dev/** from security in SecurityConfig
Add to SecurityConfig permit list:
  .requestMatchers("/dev/**").permitAll()
But ONLY when the dev profile is active. Use:
  @Value("${spring.profiles.active:prod}")
  and only add the matcher if the profile contains "dev".
Or simpler: use a @ConditionalOnProfile("dev") on a SecurityFilterChain bean
that adds the /dev/** permit rule before the main chain.

### Step 4 — Confirm the endpoint is absent in prod
Add a test comment in DevTestController explaining:
  // This class is excluded from production builds by @Profile("dev").
  // Spring will never instantiate this bean unless the "dev" profile is active.
  // It does NOT need to be removed before deployment — @Profile handles this.

### Step 5 — Verify
1. Run with dev profile — confirm GET /api/v1/dev/invitations/token?email=xxx returns the token
2. Confirm the token matches what was saved in the invitations table (check H2 console)
3. If you can test with a different profile, confirm the endpoint returns 404
```

---

## Stage 13 — CORS Configuration

```
Read AGENT.md and GEMINI.md. Use the Backend Developer agent and Security Engineer agent.

## Task: Configure CORS for the Angular frontend

Without CORS configuration the Angular app (running on localhost:4200 in dev,
and goaldone.de in prod) cannot call the API from a browser.

### Step 1 — Add CORS config to application.yml

  app:
    cors:
      allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:4200}
      allowed-methods: GET,POST,PUT,PATCH,DELETE,OPTIONS
      allowed-headers: "*"
      allow-credentials: true
      max-age: 3600

In application-dev.yml override to allow common local ports:
  app:
    cors:
      allowed-origins: http://localhost:4200,http://localhost:3000

In application-prod.yml:
  app:
    cors:
      allowed-origins: ${CORS_ALLOWED_ORIGINS}

### Step 2 — Create CorsProperties
Create src/main/java/de/goaldone/backend/config/CorsProperties.java:
  @ConfigurationProperties(prefix = "app.cors")
  @Component
  public class CorsProperties {
      private List<String> allowedOrigins;
      private List<String> allowedMethods;
      private List<String> allowedHeaders;
      private boolean allowCredentials;
      private long maxAge;
      // getters/setters via Lombok
  }

### Step 3 — Register CORS in SecurityConfig
In SecurityConfig, add a CorsConfigurationSource bean:

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
      CorsConfiguration config = new CorsConfiguration();
      config.setAllowedOrigins(corsProperties.getAllowedOrigins());
      config.setAllowedMethods(corsProperties.getAllowedMethods());
      config.setAllowedHeaders(corsProperties.getAllowedHeaders());
      config.setAllowCredentials(corsProperties.isAllowCredentials());
      config.setMaxAge(corsProperties.getMaxAge());
      UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
      source.registerCorsConfiguration("/**", config);
      return source;
  }

Wire it into the SecurityFilterChain:
  http.cors(cors -> cors.configurationSource(corsConfigurationSource()))

### Step 4 — Handle OPTIONS preflight without authentication
Preflight OPTIONS requests must return 200 without requiring a JWT.
Add to SecurityConfig permit list:
  .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

### Step 5 — Verify
1. Run the app with dev profile
2. Test a CORS preflight manually:
   curl -v -X OPTIONS http://localhost:8080/api/v1/tasks \
     -H "Origin: http://localhost:4200" \
     -H "Access-Control-Request-Method: GET" \
     -H "Access-Control-Request-Headers: Authorization"
3. Confirm the response contains:
   Access-Control-Allow-Origin: http://localhost:4200
   Access-Control-Allow-Credentials: true
   Access-Control-Allow-Methods: GET,POST,PUT,PATCH,DELETE,OPTIONS
4. Confirm an origin NOT in the allowed list is rejected (no CORS headers in response)
```

---

## Stage 14 — Input Validation

```
Read AGENT.md and GEMINI.md. Use the Backend Developer agent and Reality Checker agent.

## Task: Implement proper input validation across all service methods

Bean validation was intentionally disabled in the OpenAPI generator config
(useBeanValidation=false) to avoid a compilation bug. Validation must now be
implemented manually in the service layer.

### Step 1 — Create a ValidationService utility
Create src/main/java/de/goaldone/backend/service/ValidationService.java:
  @Component
  public class ValidationService {

      public void requireNotBlank(String value, String fieldName) {
          if (value == null || value.isBlank()) {
              throw new ValidationException(fieldName, "must not be blank");
          }
      }

      public void requireNotNull(Object value, String fieldName) {
          if (value == null) {
              throw new ValidationException(fieldName, "must not be null");
          }
      }

      public void requirePositive(Integer value, String fieldName) {
          if (value == null || value < 1) {
              throw new ValidationException(fieldName, "must be a positive number");
          }
      }

      public void requireValidEmail(String email) {
          if (email == null || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
              throw new ValidationException("email", "must be a valid email address");
          }
      }

      public void requireMinLength(String value, String fieldName, int min) {
          if (value == null || value.length() < min) {
              throw new ValidationException(fieldName, "must be at least " + min + " characters");
          }
      }

      public void requireMaxLength(String value, String fieldName, int max) {
          if (value != null && value.length() > max) {
              throw new ValidationException(fieldName, "must not exceed " + max + " characters");
          }
      }
  }

### Step 2 — Create ValidationException
Create src/main/java/de/goaldone/backend/exception/ValidationException.java:
  public class ValidationException extends RuntimeException {
      private final String field;
      private final String message;
      // constructor, getters
  }

### Step 3 — Add validation to every service method that accepts user input
For each service, validate inputs at the top of the method BEFORE any DB access.
Cover these specific cases:

AuthService.login:
  - email: not blank, valid email format
  - password: not blank

AuthService.changePassword:
  - currentPassword: not blank
  - newPassword: not blank, min length 8

AuthService.acceptInvitation:
  - firstName: not blank, max 100
  - lastName: not blank, max 100
  - password: not blank, min 8

OrganizationService.createInvitation:
  - email: not blank, valid email format

TaskService.create:
  - title: not blank, max 255
  - cognitiveLoad: not null
  - estimatedDurationMinutes: positive integer

TaskService.update: same as create

BreakService.create:
  - label: not blank, max 255
  - startTime: not null
  - endTime: not null, must be after startTime
  - recurrence.type: not null
  - recurrence.interval: positive integer

ScheduleService.generateSchedule:
  - from: not null
  - to: not null
  - to must not be before from
  - maxDailyWorkMinutes: if provided, must be between 30 and 480

### Step 4 — Map ValidationException to 400 in GlobalExceptionHandler
In GlobalExceptionHandler, add:
  @ExceptionHandler(ValidationException.class)
  public ResponseEntity<ProblemDetail> handleValidation(ValidationException ex) {
      // return RFC 9457 ProblemDetail with status 400
      // include the field + message in the errors array
  }

### Step 5 — Verify
Run ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev then test:
  # Should return 400 with field error on "title"
  curl -X POST http://localhost:8080/api/v1/tasks \
    -H "Authorization: Bearer <user_token>" \
    -H "Content-Type: application/json" \
    -d '{"title":"","cognitiveLoad":"HIGH","estimatedDurationMinutes":60}'

  # Should return 400 with field error on "email"
  curl -X POST http://localhost:8080/api/v1/organizations/me/invitations \
    -H "Authorization: Bearer <admin_token>" \
    -H "Content-Type: application/json" \
    -d '{"email":"not-an-email"}'

Confirm both return Content-Type: application/problem+json with status 400
and a meaningful errors array.
```

---

## Stage 15 — Scheduled Cleanup Jobs

```
Read AGENT.md and GEMINI.md. Use the Backend Developer agent.

## Task: Add scheduled jobs to clean up expired database records

Expired refresh tokens and expired invitations accumulate in the database
indefinitely without cleanup. This stage adds scheduled housekeeping jobs.

### Step 1 — Enable scheduling
Add @EnableScheduling to the main application class:
  @SpringBootApplication
  @EnableScheduling
  public class GoaldonePrototypeBackendApplication { ... }

### Step 2 — Add bulk delete methods to repositories

In RefreshTokenRepository:
  @Modifying
  @Query("DELETE FROM RefreshToken r WHERE r.expiresAt < :cutoff OR r.revokedAt IS NOT NULL")
  int deleteExpiredAndRevoked(@Param("cutoff") Instant cutoff);

In InvitationRepository:
  @Modifying
  @Query("DELETE FROM Invitation i WHERE i.expiresAt < :now")
  int deleteExpired(@Param("now") Instant now);

### Step 3 — Create CleanupScheduler
Create src/main/java/de/goaldone/backend/scheduler/CleanupScheduler.java:

  @Component
  @Slf4j
  @RequiredArgsConstructor
  public class CleanupScheduler {

      private final RefreshTokenRepository refreshTokenRepository;
      private final InvitationRepository invitationRepository;

      // Run every day at 02:00
      @Scheduled(cron = "0 0 2 * * *")
      @Transactional
      public void cleanupExpiredRefreshTokens() {
          int deleted = refreshTokenRepository.deleteExpiredAndRevoked(Instant.now());
          log.info("Cleanup: removed {} expired/revoked refresh tokens", deleted);
      }

      // Run every day at 02:15
      @Scheduled(cron = "0 15 2 * * *")
      @Transactional
      public void cleanupExpiredInvitations() {
          int deleted = invitationRepository.deleteExpired(Instant.now());
          log.info("Cleanup: removed {} expired invitations", deleted);
      }
  }

### Step 4 — Make the schedule configurable
Add to application.yml:
  app:
    scheduler:
      cleanup-cron: "0 0 2 * * *"
      invitation-cleanup-cron: "0 15 2 * * *"

Update @Scheduled to use:
  @Scheduled(cron = "${app.scheduler.cleanup-cron}")

### Step 5 — Add a manual trigger endpoint for dev/testing
In DevTestController (Stage 12), add:

  @Autowired
  private CleanupScheduler cleanupScheduler;

  @PostMapping("/cleanup/run")
  public ResponseEntity<Map<String, String>> triggerCleanup() {
      cleanupScheduler.cleanupExpiredRefreshTokens();
      cleanupScheduler.cleanupExpiredInvitations();
      return ResponseEntity.ok(Map.of("status", "cleanup triggered"));
  }

### Step 6 — Verify
1. Run the app and check the startup logs — scheduler should initialize without errors
2. Manually insert an expired refresh token directly via H2 console:
   INSERT INTO refresh_tokens (id, user_id, token_hash, expires_at, created_at)
   VALUES (random_uuid(), '<user_id>', 'expiredtesthash', now() - interval '1 day', now());
3. Call POST /api/v1/dev/cleanup/run
4. Verify the expired token is gone from the H2 console
5. Run ./mvnw clean verify to confirm the full build passes
```

---

## Stage 16 — Health Checks & Actuator

```
Read AGENT.md and GEMINI.md. Use the Backend Developer agent.

## Task: Configure Spring Boot Actuator for production health monitoring

### Step 1 — Add Actuator dependency
  <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-actuator</artifactId>
  </dependency>

### Step 2 — Configure Actuator endpoints

In application.yml:
  management:
    endpoints:
      web:
        base-path: /actuator
        exposure:
          include: health,info,metrics
    endpoint:
      health:
        show-details: when-authorized
        show-components: when-authorized
    info:
      env:
        enabled: true
    server:
      port: ${MANAGEMENT_PORT:8081}

  info:
    app:
      name: Goaldone Backend
      version: "@project.version@"
      description: "Task Planning System API"

Using a separate management port (8081) means health endpoints are never
exposed on the same port as the API — important for production firewall rules.

In application-dev.yml — show everything for easier debugging:
  management:
    endpoint:
      health:
        show-details: always
        show-components: always
    server:
      port: 8081

### Step 3 — Secure Actuator endpoints
In SecurityConfig, permit the health endpoint for load balancer checks
but protect the others:
  .requestMatchers("/actuator/health").permitAll()
  .requestMatchers("/actuator/**").hasRole("SUPER_ADMIN")

Since Actuator is on port 8081 and the API on 8080, the firewall can block
8081 from public traffic entirely. Document this in application-prod.yml comments.

### Step 4 — Add a custom health indicator
Create src/main/java/de/goaldone/backend/health/DatabaseHealthIndicator.java:

  @Component
  public class DatabaseHealthIndicator implements HealthIndicator {

      private final UserRepository userRepository;

      @Override
      public Health health() {
          try {
              long userCount = userRepository.count();
              return Health.up()
                  .withDetail("users", userCount)
                  .withDetail("status", "database reachable")
                  .build();
          } catch (Exception e) {
              return Health.down()
                  .withException(e)
                  .build();
          }
      }
  }

### Step 5 — Verify
1. Run the app with dev profile
2. curl http://localhost:8081/actuator/health  → should return {"status":"UP"}
3. curl http://localhost:8081/actuator/info    → should return app name + version
4. curl http://localhost:8081/actuator/metrics → should return list of metric names
5. Confirm curl http://localhost:8081/actuator/metrics/jvm.memory.used returns data
6. Confirm API traffic on 8080 is unaffected
```

---

## Stage 17 — Request Logging & Correlation IDs

```
Read AGENT.md and GEMINI.md. Use the Backend Developer agent.

## Task: Add structured request logging with correlation IDs

Every HTTP request should be assigned a unique correlation ID (UUID) that
appears in every log line for that request. This makes debugging production
issues possible by searching logs for a single ID.

### Step 1 — Add a request logging filter
Create src/main/java/de/goaldone/backend/filter/RequestLoggingFilter.java:

  @Component
  @Order(1)
  public class RequestLoggingFilter extends OncePerRequestFilter {

      private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
      private static final String CORRELATION_ID_MDC_KEY = "correlationId";

      @Override
      protected void doFilterInternal(HttpServletRequest request,
                                       HttpServletResponse response,
                                       FilterChain chain)
              throws ServletException, IOException {

          String correlationId = Optional
              .ofNullable(request.getHeader(CORRELATION_ID_HEADER))
              .filter(h -> !h.isBlank())
              .orElse(UUID.randomUUID().toString());

          MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
          response.addHeader(CORRELATION_ID_HEADER, correlationId);

          long start = System.currentTimeMillis();
          try {
              chain.doFilter(request, response);
          } finally {
              long duration = System.currentTimeMillis() - start;
              log.info("{} {} → {} ({}ms)",
                  request.getMethod(),
                  request.getRequestURI(),
                  response.getStatus(),
                  duration);
              MDC.clear();
          }
      }

      @Override
      protected boolean shouldNotFilter(HttpServletRequest request) {
          // Don't log actuator health checks — too noisy
          return request.getRequestURI().startsWith("/actuator/health");
      }
  }

### Step 2 — Configure logback to include the correlation ID
Create src/main/resources/logback-spring.xml:

  <configuration>
      <springProfile name="dev">
          <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
              <encoder>
                  <pattern>%d{HH:mm:ss.SSS} [%thread] [%X{correlationId:-no-corr-id}] %-5level %logger{36} - %msg%n</pattern>
              </encoder>
          </appender>
          <root level="INFO">
              <appender-ref ref="CONSOLE"/>
          </root>
          <logger name="de.goaldone" level="DEBUG"/>
      </springProfile>

      <springProfile name="prod">
          <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
              <encoder class="net.logstash.logback.encoder.LogstashEncoder">
                  <!-- JSON output for log aggregation tools (Datadog, ELK, etc.) -->
              </encoder>
          </appender>
          <root level="INFO">
              <appender-ref ref="CONSOLE"/>
          </root>
      </springProfile>
  </configuration>

NOTE: The Logstash encoder in the prod profile requires this optional dependency —
only add it if you plan to use a log aggregation service:
  <dependency>
      <groupId>net.logstash.logback</groupId>
      <artifactId>logstash-logback-encoder</artifactId>
      <version>8.0</version>
      <optional>true</optional>
  </dependency>
If not, use the same pattern encoder for prod as for dev.

### Step 3 — Add logging to key service operations
In AuthService, add log statements at INFO level for:
  - Successful login: log.info("Login successful for user: {}", user.getId())
  - Failed login: log.warn("Login failed for email: {}", email)
  - Token refresh: log.debug("Token refreshed for user: {}", userId)
  - Logout: log.info("User logged out: {}", userId)

In OrganizationService:
  - Invitation created: log.info("Invitation created for: {} in org: {}", email, orgId)
  - Member removed: log.info("Member {} removed from org: {}", userId, orgId)

In ScheduleService:
  - log.info("Generating schedule for user: {} from {} to {}", userId, from, to)
  - log.info("Schedule generated: {} entries for user: {}", entries.size(), userId)

Never log passwords, tokens, or full request bodies — only IDs and metadata.

### Step 4 — Verify
1. Run the app with dev profile
2. Make any API call
3. Confirm the log output shows:
   - Timestamp
   - Correlation ID (UUID)
   - HTTP method + path + status + duration
   Example: 14:23:01.442 [http-nio-8080-exec-1] [a3f2c1d0-...] INFO  RequestLoggingFilter - GET /api/v1/tasks → 200 (23ms)
4. Check the response headers — X-Correlation-ID should be present
5. Make the same request twice — confirm each has a unique correlation ID
```

---

## Stage 18 — Startup Configuration Validation

```
Read AGENT.md and GEMINI.md. Use the Backend Developer agent and Reality Checker agent.

## Task: Validate required configuration on startup and fail fast with clear errors

If a required environment variable is missing or misconfigured in production,
the app should refuse to start with a clear error — not fail silently at runtime.

### Step 1 — Create StartupValidator
Create src/main/java/de/goaldone/backend/config/StartupValidator.java:

  @Component
  @Slf4j
  public class StartupValidator implements ApplicationRunner {

      @Value("${app.jwt.secret}")
      private String jwtSecret;

      @Value("${spring.profiles.active:dev}")
      private String activeProfile;

      @Autowired
      private DataSource dataSource;

      @Override
      public void run(ApplicationArguments args) {
          log.info("=== Goaldone Startup Validation ===");
          validateJwtSecret();
          validateDatabaseConnection();
          logActiveProfile();
          log.info("=== Startup validation passed ===");
      }

      private void validateJwtSecret() {
          if (jwtSecret == null || jwtSecret.isBlank()) {
              throw new IllegalStateException(
                  "STARTUP FAILED: app.jwt.secret is not configured. " +
                  "Set the JWT_SECRET environment variable.");
          }
          if (jwtSecret.contains("dev-secret") && activeProfile.contains("prod")) {
              throw new IllegalStateException(
                  "STARTUP FAILED: app.jwt.secret contains the dev placeholder value. " +
                  "Set a strong JWT_SECRET in production.");
          }
          if (jwtSecret.length() < 32) {
              throw new IllegalStateException(
                  "STARTUP FAILED: app.jwt.secret is too short. " +
                  "Use at least 32 characters (256 bits) for HS256.");
          }
          log.info("✓ JWT secret configured ({} chars)", jwtSecret.length());
      }

      private void validateDatabaseConnection() {
          try (Connection conn = dataSource.getConnection()) {
              log.info("✓ Database connection OK ({})", conn.getMetaData().getDatabaseProductName());
          } catch (Exception e) {
              throw new IllegalStateException(
                  "STARTUP FAILED: Cannot connect to database. " + e.getMessage(), e);
          }
      }

      private void logActiveProfile() {
          log.info("✓ Active profile: {}", activeProfile);
          if (activeProfile.contains("prod")) {
              log.info("  Running in PRODUCTION mode");
          } else {
              log.warn("  Running in DEVELOPMENT mode — not suitable for production");
          }
      }
  }

### Step 2 — Add validation to SuperAdminProperties
In SuperAdminProperties (or wherever the superAdmins list is defined),
add validation so that if the file exists but has malformed entries, the app
fails fast:
  - Each entry must have a non-blank email
  - Each entry must have a non-blank password
  - Each entry must have a non-blank firstName and lastName
  - Log a warning (not an error) if no super admins are configured — the app
    can still run, but an operator should know

### Step 3 — Add @Validated to all @ConfigurationProperties classes
Ensure CorsProperties and SuperAdminProperties (and any other config classes)
are annotated with @Validated so Spring validates them at context startup using
JSR-303 constraints where possible.

### Step 4 — Verify fast-fail behavior
1. Temporarily set app.jwt.secret to a short value in application-dev.yml
2. Run the app — confirm it refuses to start with a clear STARTUP FAILED message
3. Restore the correct secret
4. Temporarily break the DB URL in application-dev.yml
5. Confirm the app refuses to start with a database connection error
6. Restore the correct DB URL
7. Run ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev with correct config
8. Confirm all startup validation checks pass and the log shows the ✓ lines
```

---

## Stage 19 — Dockerfile & Docker Compose

```
Read AGENT.md and GEMINI.md. Use the Backend Architect agent and Backend Developer agent.

## Task: Containerize the application with Docker and Docker Compose

### Step 1 — Create a production Dockerfile
Create Dockerfile in the project root:

  # Stage 1: Build
  FROM eclipse-temurin:21-jdk-alpine AS builder
  WORKDIR /app
  COPY .mvn/ .mvn/
  COPY mvnw pom.xml ./
  RUN ./mvnw dependency:go-offline -q
  COPY src/ src/
  RUN ./mvnw clean package -DskipTests -q

  # Stage 2: Runtime (minimal image)
  FROM eclipse-temurin:21-jre-alpine AS runtime
  WORKDIR /app

  # Create a non-root user for security
  RUN addgroup -S goaldone && adduser -S goaldone -G goaldone
  USER goaldone

  COPY --from=builder /app/target/*.jar app.jar

  EXPOSE 8080
  EXPOSE 8081

  ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]

### Step 2 — Create .dockerignore
Create .dockerignore in the project root:
  target/
  .git/
  .github/
  *.md
  !README.md
  .idea/
  *.iml
  super-admins.yml

### Step 3 — Create docker-compose.yml for local full-stack development
Create docker-compose.yml in the project root:

  version: "3.9"

  services:
    postgres:
      image: postgres:16-alpine
      environment:
        POSTGRES_DB: goaldone
        POSTGRES_USER: goaldone
        POSTGRES_PASSWORD: goaldone_dev
      ports:
        - "5432:5432"
      volumes:
        - postgres_data:/var/lib/postgresql/data
      healthcheck:
        test: ["CMD-SHELL", "pg_isready -U goaldone"]
        interval: 5s
        timeout: 5s
        retries: 5

    mailpit:
      image: axllent/mailpit:latest
      ports:
        - "1025:1025"   # SMTP
        - "8025:8025"   # Web UI at http://localhost:8025

    backend:
      build: .
      depends_on:
        postgres:
          condition: service_healthy
      environment:
        SPRING_PROFILES_ACTIVE: prod
        SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/goaldone
        DB_USERNAME: goaldone
        DB_PASSWORD: goaldone_dev
        JWT_SECRET: local-docker-dev-secret-min-32-chars!!
        MAIL_HOST: mailpit
        MAIL_PORT: 1025
        CORS_ALLOWED_ORIGINS: http://localhost:4200
        APP_BASE_URL: http://localhost:8080
      ports:
        - "8080:8080"
        - "8081:8081"
      volumes:
        # Mount super-admins.yml into the container at startup
        - ./super-admins.yml:/app/super-admins.yml:ro
      healthcheck:
        test: ["CMD-SHELL", "wget -qO- http://localhost:8081/actuator/health || exit 1"]
        interval: 10s
        timeout: 5s
        retries: 5

  volumes:
    postgres_data:

### Step 4 — Create docker-compose.dev.yml for running only infrastructure
Create docker-compose.dev.yml (for running only postgres + mailpit while
running the Spring Boot app locally via Maven):

  version: "3.9"

  services:
    postgres:
      image: postgres:16-alpine
      environment:
        POSTGRES_DB: goaldone
        POSTGRES_USER: goaldone
        POSTGRES_PASSWORD: goaldone_dev
      ports:
        - "5432:5432"
      volumes:
        - postgres_dev_data:/var/lib/postgresql/data

    mailpit:
      image: axllent/mailpit:latest
      ports:
        - "1025:1025"
        - "8025:8025"

  volumes:
    postgres_dev_data:

### Step 5 — Update application-prod.yml to use environment variables
Ensure application-prod.yml reads all sensitive config from env vars:
  spring:
    datasource:
      url: ${SPRING_DATASOURCE_URL}
      username: ${DB_USERNAME}
      password: ${DB_PASSWORD}

### Step 6 — Verify

Test the infrastructure-only compose:
  docker compose -f docker-compose.dev.yml up -d
  ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

Test the full stack compose:
  docker compose up --build
  curl http://localhost:8080/api/v1/auth/login \
    -X POST -H "Content-Type: application/json" \
    -d '{"email":"superadmin@goaldone.de","password":"ChangeMe123!"}'

Confirm:
  1. App starts and Liquibase runs cleanly against PostgreSQL
  2. Super admin from super-admins.yml is created on first boot
  3. Login returns a valid JWT
  4. http://localhost:8025 shows the Mailpit UI
  5. Health check at http://localhost:8081/actuator/health returns UP
```

---

## Stage 20 — Write the Test Script

```
Read AGENT.md and GEMINI.md. Use the Backend Developer agent and Reality Checker agent.

## Task: Write the complete automated test script (test-api.sh)

Now that all production readiness stages are implemented, write the full test
suite that uses only the super-admins.yml credentials as the entry point.

The script must:
1. Read SUPER_ADMIN_EMAIL and SUPER_ADMIN_PASSWORD from environment variables
   or accept them as CLI arguments — never hardcode credentials
2. Use the super admin to create a fresh test organization via POST /admin/organizations
3. Get the invitation token via GET /dev/invitations/token (Stage 12 dev endpoint)
4. Accept the invitation to create a test ADMIN user
5. Use the new admin to invite a test USER
6. Get the USER invitation token via the dev endpoint
7. Accept the invitation to create the test USER
8. Run all 55 endpoint tests using these three dynamically created accounts
9. Clean up: delete the test organization at the end (CASCADE deletes all test data)

### Script signature
  ./test-api.sh \
    --super-admin-email superadmin@goaldone.de \
    --super-admin-password ChangeMe123! \
    [--base-url http://localhost:8080/api/v1] \
    [--verbose] \
    [--no-cleanup]

### Key difference from previous test script design
- No seed data required — the script is fully self-contained
- No hardcoded UUIDs or passwords in any file
- The test organization is created fresh on each run and deleted at the end
  (unless --no-cleanup is passed, useful for debugging)
- The only prerequisite is a running app and a valid super admin in super-admins.yml

### Test organization naming
Use a timestamp in the org name to avoid conflicts if cleanup fails:
  TEST_ORG_NAME="Test Org $(date +%Y%m%d-%H%M%S)"

### After writing the script:
1. chmod +x test-api.sh
2. Run: ./test-api.sh --super-admin-email superadmin@goaldone.de --super-admin-password ChangeMe123!
3. Confirm all tests pass
4. Run again immediately — confirm it still passes (idempotency via fresh org per run)
5. Run ./mvnw clean verify to confirm the full build passes
```
