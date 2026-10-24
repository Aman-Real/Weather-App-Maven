# SkyCast — viva / exam notes

Short answers you can give while pointing at the code. File paths are relative to the project root.

## 1. The 60-second pitch

SkyCast is a weather dashboard built as **Spring Boot microservices** behind a **Spring Cloud Gateway**.
Services find each other through **Eureka**.

* `weather-service` wraps the free Open-Meteo API inside a **Resilience4j circuit breaker** and stores readings with **JPA/Hibernate**.
* `alert-service` checks users' alert rules every few minutes, fetching weather through **OpenFeign**. It pushes alerts live to the phone over **Server-Sent Events** (Reactor `Flux`).
* `auth-service` issues **JWTs**. Every service, and the gateway, verifies them as an **OAuth2 resource server**.
* The client is an installable **PWA** served by the gateway.
* It runs from one **Maven multi-module** build, on H2 locally or PostgreSQL in Docker or Kubernetes.

## 2. Unit 1 — Maven

| Question | Answer (and where) |
|---|---|
| Why Maven over copying JARs? | Maven downloads declared dependencies and their transitive dependencies from Maven Central, picks one version per artifact ("nearest wins") and caches them in `~/.m2`. Builds become repeatable. |
| What is the POM? | The Project Object Model (`pom.xml`): coordinates (`groupId:artifactId:version`), packaging, dependencies, plugins, profiles, modules. |
| Parent vs. multi-module? | The root `pom.xml` is both. It is the **parent** (children inherit `<dependencyManagement>`, plugin config and properties) and the **aggregator** (`<modules>` builds all 6 in dependency order, the *reactor*). |
| `dependencyManagement` vs `dependencies`? | `dependencyManagement` only fixes versions. A module still has to declare the dependency, but without a version. The Spring Cloud BOM is imported with `<type>pom</type><scope>import</scope>`. |
| Scopes used | `compile` (default), `runtime` (H2, PostgreSQL drivers, jjwt-impl), `test` (starter-test), `optional` (spring-web in weather-common). |
| Lifecycle | `validate → compile → test → package → verify → install → deploy`. Running a phase runs every earlier phase. `mvnw verify` = compile + unit/integration tests + package. |
| Goal vs phase | A goal is a plugin task (`spring-boot:run`, `dependency:go-offline`). Phases are bound to goals (e.g. `test` → `surefire:test`). |
| Plugins here | `maven-compiler-plugin` (Java 17, `-parameters`), `surefire` (tests), `spring-boot-maven-plugin` (executable fat JAR, `spring-boot:run`) — all configured by the Boot parent. |
| Profiles | `pom.xml` has `dev` (active by default), `docker` and `fast` (skip tests). `-Pdocker` changes the Spring profile used by `spring-boot:run` and is filtered into `info.app.maven-profile` (`@activeProfile@`). |
| Maven Wrapper | `mvnw` / `mvnw.cmd` + `.mvn/wrapper/maven-wrapper.properties` pin Maven 3.9.11. Nobody needs Maven installed, and CI uses the same version. |
| Archetypes | Project templates: `mvn archetype:generate -DarchetypeArtifactId=maven-archetype-quickstart`. Spring projects usually start from start.spring.io instead (same idea). |
| Maven vs Gradle vs Ant | **Ant:** imperative XML tasks, no dependency management (Ivy was added later). **Maven:** declarative, convention over configuration, fixed lifecycle, XML. **Gradle:** Groovy/Kotlin DSL, incremental builds and a build cache, so faster and more flexible, but less uniform. |
| CI/CD | `.github/workflows/ci.yml` (matrix Java 17/21), `Jenkinsfile` (build → images → push → Helm deploy). |
| Docker build pipeline | The `Dockerfile` builds inside `maven:3.9-eclipse-temurin-21` (`-pl MODULE -am` builds only that module and what it needs), then copies the JAR into a small `eclipse-temurin:21-jre-alpine` image that runs as a non-root user. |

## 3. Unit 2 — Spring Framework

| Question | Answer (and where) |
|---|---|
| IoC / DI | The container creates objects (beans) and *injects* their dependencies, so a class never `new`s its collaborators. We use **constructor injection** everywhere (e.g. `WeatherService`). This makes classes testable with mocks (`WeatherServiceTest`). |
| Bean scopes used | singleton (default), `@StepScope` (Spring Batch: a new reader/processor per step execution, `HistoryImportJobConfig`). |
| Bean lifecycle | Instantiate → inject properties → `@PostConstruct` / `init-method` → in use → `@PreDestroy` / `destroy-method`. Shown in `SnapshotScheduler` (annotations) and `beans.xml` (`init-method="init"`, `destroy-method="shutdown"`). |
| XML configuration | `beans.xml` defines `weatherCodeTranslator` with setter injection of a `<map>`. `XmlBeansConfig` imports it with `@ImportResource`. |
| AOP | `PerformanceAspect`: `@Pointcut` + `@Around` advice times every service and client method and records a Micrometer `Timer`. `@AfterThrowing` logs provider errors. Spring builds a proxy around the target bean. `@Transactional`, `@Cacheable` and `@PreAuthorize` work the same way. |
| Spring JDBC | `WeatherStatsDao`: `NamedParameterJdbcTemplate` + a lambda `RowMapper` for a `GROUP BY` statistics query. |
| Transactions | `@Transactional` (default propagation REQUIRED). `FetchAuditService.log` uses **REQUIRES_NEW**: the caller's transaction is suspended and the audit row commits independently. `readOnly = true` on queries. |
| MVC | `DispatcherServlet` → handler mapping → `@RestController` method → `HttpMessageConverter` (Jackson) → JSON. `StatusPageController` is a classic `@Controller` that returns the **view name** `"status"`; `ThymeleafViewResolver` maps it to `templates/status.html`. |
| Form handling | `/status` binds the query string to the `LocationSearchForm` record (`@ModelAttribute`), validates it (`@Valid` + `BindingResult`) and re-renders the form. REST bodies are validated with `@Valid` (`RegisterRequest`: `@Email`, `@Size`). |
| Spring Security | auth-service: `DaoAuthenticationProvider` + `BCryptPasswordEncoder` + `UserDetailsService` checks passwords. Then **stateless JWT** (no HTTP session). Each service is an **OAuth2 resource server** (`NimbusJwtDecoder`, HS256). The `roles` claim becomes `ROLE_*` authorities. |
| Method security | `@EnableMethodSecurity` (in the shared auto-config) + `@PreAuthorize("hasRole('ADMIN')")` on admin endpoints. |
| Spring Integration | A framework for Enterprise Integration Patterns (channels, transformers, adapters for JMS/Kafka/files). SkyCast's in-process pub/sub (`AlertStreamService`) is the same *publish-subscribe channel* idea built with Reactor Sinks. With several alert-service replicas we would replace it with Spring Integration / Spring Cloud Stream over Kafka or RabbitMQ. |
| Batch | `HistoryImportJobConfig`: `Job` → `Step` → **chunk** of 5 (reader = locations, processor = fetch archive + merge, writer = `saveAll`) with skip-on-error. Run from `POST /api/weather/admin/history-import`. Metadata tables are `BATCH_*`. |
| WebFlux / reactive | The gateway runs on Netty with non-blocking I/O. `Mono` = 0..1 value, `Flux` = 0..N values. `FallbackRouter` uses **router functions** (`RouterFunction<ServerResponse>`). `AlertController.stream` returns `Flux<ServerSentEvent>`, which merges a live alert `Flux` with a heartbeat `Flux.interval`. |
| Java 17 / Jakarta | Records (all DTOs), switch expressions (`AlertEvaluator`), text blocks (`WeatherStatsDao`). Imports come from `jakarta.*` (Jakarta EE 10), not `javax.*`. |
| Micrometer | Actuator exposes `/actuator/prometheus`. Custom metrics: `skycast.method.duration` (AOP timer), `skycast.weather.requests` and `skycast.alerts.raised` (counters). `micrometer-tracing-bridge-otel` adds trace/span ids to logs (OpenTelemetry). |

## 4. Unit 3 — Spring Boot

| Question | Answer (and where) |
|---|---|
| Auto-configuration | Boot looks at the classpath and properties and creates beans conditionally (`@ConditionalOnClass`, `@ConditionalOnMissingBean`, …). We wrote our **own**: `SkycastSecurityAutoConfiguration`, registered in `META-INF/spring/…AutoConfiguration.imports`. Any servlet service that adds `weather-common` becomes a JWT resource server. |
| Starters | Curated dependency sets: `starter-web`, `starter-data-jpa`, `starter-security`, `starter-oauth2-resource-server`, `starter-batch`, `starter-aop`, `starter-cache`, `starter-actuator`, `starter-thymeleaf`, Spring Cloud starters. |
| Embedded server | Services run embedded Tomcat; the gateway runs Netty (reactive). Change the port with `server.port` / `PORT`. To switch to Jetty, exclude `spring-boot-starter-tomcat` and add `spring-boot-starter-jetty`. |
| Spring Boot CLI | A command-line tool (`spring init`, `spring encodepassword`) for scaffolding. Not needed here because the Maven Wrapper covers building. |
| REST design | Resource URLs, correct verbs and status codes (`201` create, `204` delete, `400` validation, `401` no token, `403` wrong role, `409` conflict, `503` provider down). One error format (`GlobalExceptionHandler` → `ApiError`). |
| Profiles | `application.yml` holds the defaults (H2). `application-docker.yml` switches to PostgreSQL. `spring.profiles.default: dev`. |
| Actuator | `/actuator/health` (with Kubernetes liveness/readiness groups), `/info`, `/metrics`, `/prometheus`, `/caches`, gateway `/actuator/gateway/routes`. |
| Eureka | Each service registers (`spring.application.name`). Clients resolve `lb://weather-service` to an instance through Spring Cloud LoadBalancer. |
| Feign | `WeatherClient` is an interface; Spring generates the HTTP client. `spring.cloud.openfeign.circuitbreaker.enabled=true` wraps calls in a circuit breaker with `WeatherClientFallbackFactory`. |
| Gateway | `GatewayRoutesConfig`: Java DSL routes, a `circuitBreaker` filter with `forward:/fallback/...`. The SSE route deliberately has no breaker, because it is long-lived. |
| Resilience4j | States CLOSED → OPEN (after ≥50 % failures in the sliding window) → HALF_OPEN (trial calls) → CLOSED. A TimeLimiter caps call time. Fallback: weather-service serves the last DB snapshot (`source = CACHED`). |
| Config & secrets | `@ConfigurationProperties` records (`JwtProperties`, `OpenMeteoProperties`). Secrets come from env vars: `.env` for compose, `Secret` in k8s, Jenkins credentials. They are never committed. |
| Docker / K8s / Helm | `Dockerfile` (multi-stage), `docker-compose.yml` (health-checked start order), `k8s/` (Deployments with probes, Services, StatefulSet, ConfigMap, Secret), `helm/skycast` (one template looping over `values.services`). |

## 5. Unit 4 — Spring ORM / Hibernate

| Question | Answer (and where) |
|---|---|
| ORM | Maps classes to tables so you work with objects. Hibernate is the JPA provider. Spring Boot configures the `EntityManagerFactory`, which wraps Hibernate's `SessionFactory`, plus a `JpaTransactionManager`. |
| Session | `LocationRepositoryCustomImpl.recentlyUpdated` unwraps the Hibernate `Session` from the JPA `EntityManager` and runs **HQL**. |
| Annotation vs XML mapping | Most entities are annotated (`@Entity`, `@Table`, `@Column`). `FetchAuditLog` has **no annotations**: it is mapped in `META-INF/orm.xml`. |
| Relationships | `Location 1—N WeatherSnapshot` (`@OneToMany(mappedBy)` / `@ManyToOne`), `Location 1—N DailyWeather`, `AppUser 1—N FavoriteLocation` (cascade + orphanRemoval), roles as `@ElementCollection`. |
| Lazy vs eager | Collections and `@ManyToOne` are LAZY here, so a proxy is loaded on first access (see `nativeQueryAndFetchJoin` test, which asserts `Hibernate.isInitialized`). Roles are EAGER because every login needs them. N+1 is avoided with `join fetch` and `@EntityGraph`. |
| Query styles | Derived methods (`findTop10ByNameContainingIgnoreCaseOrderByNameAsc`), JPQL with **named parameters** (`:country`), **named query** in `orm.xml` (`DailyWeather.findRange`), **native SQL** (`findLatestNative`, named native query), **Criteria API** (`search`), **HQL** (`recentlyUpdated`), bulk `@Modifying` update. |
| Caching | First-level cache = the persistence context (per transaction). **Second-level cache** = `@Cache(READ_WRITE)` on `Location` with JCache/Ehcache. **Spring Cache** (`@Cacheable`, Caffeine with a 10 min TTL) caches whole forecast, geocode and grid responses. |
| Propagation | REQUIRED (join or create), REQUIRES_NEW (always a new tx; audit log), plus SUPPORTS, MANDATORY, NEVER, NOT_SUPPORTED and NESTED (savepoint). |
| Locking | **Optimistic:** `@Version` columns. A stale update throws `ObjectOptimisticLockingFailureException`, which becomes HTTP 409. **Pessimistic:** `@Lock(PESSIMISTIC_WRITE)` → `SELECT … FOR UPDATE` in `findByIdForUpdate` (admin rename). |
| Exceptions | Spring translates JDBC/Hibernate exceptions into the `DataAccessException` hierarchy (`DataIntegrityViolationException`, `OptimisticLockingFailureException`). `GlobalExceptionHandler` maps them to HTTP codes. |
| Auditing & versioning | `@EnableJpaAuditing` + `AuditingEntityListener` fill `@CreatedDate` and `@LastModifiedDate` (`Auditable`). `@Version` both versions the row and locks it optimistically. |
| Performance | JDBC batching (`hibernate.jdbc.batch_size: 50`, `order_inserts`), indexes in Flyway (`idx_snapshot_location_time`), fetch joins, `open-in-view: false`, read-only transactions, HikariCP pool (Boot default), second-level and Spring caches. |
| Migrations | **Flyway:** versioned SQL `V1__…sql`, `V2__seed…sql`, recorded in `flyway_schema_history`, with `ddl-auto: none` so Hibernate never changes the schema. **Liquibase** is the alternative: changelogs in XML/YAML/SQL with rollback support and DB-agnostic change types. |
| Testing ORM | `WeatherRepositoryTest` uses `@DataJpaTest`: only the JPA slice, an embedded H2 database, the real Flyway migrations, and a rollback after each test. |

## 6. Questions examiners like

* **What happens if Open-Meteo is down?** The circuit breaker opens. `/current` serves the last stored snapshot, marked "Saved data" in the UI. The service worker also serves the last cached response when the phone is offline.
* **How is the alert "real-time"?** The browser holds one `EventSource` connection to `/api/alerts/stream`. The server pushes an event the moment `AlertEngine` raises an alert (every 5 min, configurable), or instantly for "Send test alert". No polling.
* **Why does the SSE URL carry the token?** `EventSource` cannot send headers, so the token goes in `?access_token=`. Both the gateway and the services allow the query-parameter token *only for GET*.
* **Why is weather-common not used by the gateway?** It contains servlet (Spring MVC) security config, and Spring Cloud Gateway is reactive. Mixing the two stacks breaks the gateway.
* **How would you scale alert-service?** Move the pub/sub from in-memory Sinks to Kafka or Redis. Add ShedLock so only one replica runs the scheduler.
