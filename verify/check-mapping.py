#!/usr/bin/env python3
"""
Syllabus coverage check. The topic list below is copied from the course syllabus (Units 1-4).
For every topic it names the file that implements it and a token that must appear in that file,
so the check fails if a file is missing OR does not actually contain the concept.

  python verify/check-mapping.py           # check
  python verify/check-mapping.py --write   # check, then regenerate docs/SYLLABUS-MAPPING.md
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
W = "weather-service/src/main/java/com/skycast/weather/"
A = "alert-service/src/main/java/com/skycast/alert/"
U = "auth-service/src/main/java/com/skycast/auth/"
G = "api-gateway/src/main/java/com/skycast/gateway/"
C = "weather-common/src/main/java/com/skycast/common/"

# (unit, syllabus topic, file, token that must be in the file, how it is used)
TOPICS = [
    # ---------------- UNIT 1: Maven ----------------
    ("1", "What is Maven / POM (pom.xml)", "pom.xml", "<modelVersion>4.0.0</modelVersion>", "Parent POM of the whole project"),
    ("1", "Maven over manual dependency management", "pom.xml", "<dependencyManagement>", "Versions declared once, inherited by modules"),
    ("1", "Comparison with Gradle and Ant", "docs/VIVA-NOTES.md", "Gradle", "Explained in viva notes"),
    ("1", "Managing dependencies / dependency resolution (BOM import)", "pom.xml", "<scope>import</scope>", "Spring Cloud BOM imported; Boot parent BOM inherited"),
    ("1", "Dependency scopes", "weather-service/pom.xml", "<scope>runtime</scope>", "runtime (H2/PostgreSQL), test scopes"),
    ("1", "Maven repositories", ".mvn/wrapper/maven-wrapper.properties", "repo.maven.apache.org", "Maven Central"),
    ("1", "Lifecycle phases & goals", ".github/workflows/ci.yml", "mvnw -B verify", "validate→compile→test→package→verify"),
    ("1", "Maven plugins", "pom.xml", "spring-boot-maven-plugin", "Boot plugin configured in pluginManagement"),
    ("1", "Multi-module project", "pom.xml", "<module>weather-service</module>", "6 modules"),
    ("1", "Parent-child modules", "weather-service/pom.xml", "<artifactId>skycast-parent</artifactId>", "Child declares the parent"),
    ("1", "Dependencies across modules", "alert-service/pom.xml", "<artifactId>weather-common</artifactId>", "Services depend on weather-common"),
    ("1", "Maven profiles", "pom.xml", "<id>docker</id>", "dev / docker / fast profiles"),
    ("1", "Resource filtering by profile", "weather-service/src/main/resources/application.yml", "@activeProfile@", "Maven property filtered into app info"),
    ("1", "Maven Wrapper (mvnw)", "mvnw.cmd", "maven-wrapper.properties", "mvnw / mvnw.cmd download Maven 3.9.11"),
    ("1", "Archetypes", "docs/VIVA-NOTES.md", "archetype:generate", "Explained in viva notes"),
    ("1", "IDE support (IntelliJ, Eclipse)", "README.md", "IntelliJ", "Import as Maven project"),
    ("1", "CI/CD with Jenkins", "Jenkinsfile", "mvnw -B verify", "Declarative pipeline"),
    ("1", "CI/CD with GitHub Actions", ".github/workflows/ci.yml", "actions/setup-java", "Matrix build Java 17/21"),
    ("1", "Container-based build pipeline (Docker)", "Dockerfile", "-pl ${MODULE} -am package", "Multi-stage Maven build inside Docker"),
    # ---------------- UNIT 2: Spring Framework ----------------
    ("2", "Inversion of Control / Dependency Injection", W + "service/WeatherService.java", "public WeatherService(OpenMeteoClient client", "Constructor injection everywhere"),
    ("2", "Spring Core annotations (@Configuration, @Bean)", W + "config/ResilienceConfig.java", "@Bean", "Java configuration"),
    ("2", "XML-based configuration", W + "config/XmlBeansConfig.java", "@ImportResource(\"classpath:beans.xml\")", "beans.xml imported"),
    ("2", "XML bean with setter injection", "weather-service/src/main/resources/beans.xml", "<property name=\"descriptions\">", "WeatherCodeTranslator"),
    ("2", "Bean lifecycle (init/destroy methods)", "weather-service/src/main/resources/beans.xml", "init-method=\"init\"", "XML lifecycle callbacks"),
    ("2", "Bean lifecycle (@PostConstruct/@PreDestroy)", W + "scheduling/SnapshotScheduler.java", "@PreDestroy", "Annotation lifecycle callbacks"),
    ("2", "Spring AOP", W + "aop/PerformanceAspect.java", "@Around(\"serviceMethods() || providerCalls()\")", "Timing aspect + @AfterThrowing"),
    ("2", "Spring JDBC", W + "repository/WeatherStatsDao.java", "NamedParameterJdbcTemplate", "Aggregate SQL with RowMapper"),
    ("2", "ORM integration", W + "repository/LocationRepositoryCustomImpl.java", "@PersistenceContext", "EntityManager injected"),
    ("2", "Transaction management", W + "service/HistoryService.java", "@Transactional", "Declarative transactions"),
    ("2", "Spring MVC controllers & request mappings", W + "web/WeatherController.java", "@GetMapping(\"/current\")", "REST controller"),
    ("2", "View resolvers (server-side views)", W + "web/StatusPageController.java", "return \"status\";", "Thymeleaf view resolved by name"),
    ("2", "Form handling", W + "web/StatusPageController.java", "@ModelAttribute(\"form\")", "Bound + validated search form"),
    ("2", "Validation of request bodies", U + "web/AuthController.java", "@Valid @RequestBody RegisterRequest", "Bean Validation"),
    ("2", "Spring Security authentication", U + "config/SecurityConfig.java", "DaoAuthenticationProvider", "Username/password against DB (BCrypt)"),
    ("2", "Authorization (URL rules)", W + "config/SecurityConfig.java", ".hasRole(\"ADMIN\")", "Role-based URL rules"),
    ("2", "OAuth2 resource server / JWT", C + "security/SkycastSecurityAutoConfiguration.java", "NimbusJwtDecoder", "HS256 JWT validation"),
    ("2", "Method-level security", U + "web/UserController.java", "@PreAuthorize(\"hasRole('ADMIN')\")", "@EnableMethodSecurity"),
    ("2", "Spring Integration (messaging concepts)", "docs/VIVA-NOTES.md", "Spring Integration", "Explained; in-app pub/sub uses Reactor Sinks"),
    ("2", "Batch processing (Spring Batch)", W + "batch/HistoryImportJobConfig.java", "new StepBuilder(", "Chunk job: reader→processor→writer"),
    ("2", "Reactive stack: Spring WebFlux", "api-gateway/pom.xml", "spring-cloud-starter-gateway-server-webflux", "Gateway runs on WebFlux/Netty"),
    ("2", "Mono / Flux", A + "web/AlertController.java", "Flux<ServerSentEvent<Object>>", "SSE stream"),
    ("2", "Hot publisher (Sinks)", A + "service/AlertStreamService.java", "Sinks.many()", "Multicast sink"),
    ("2", "Router functions", G + "FallbackRouter.java", "RouterFunction<ServerResponse>", "Functional endpoints"),
    ("2", "Reactive Spring Security", G + "SecurityConfig.java", "ServerHttpSecurity", "JWT check at the edge"),
    ("2", "Running on Java 17+", "pom.xml", "<java.version>17</java.version>", "Records, switch expressions, text blocks"),
    ("2", "Jakarta-based APIs", W + "domain/Location.java", "jakarta.persistence", "Jakarta EE 10 namespaces"),
    ("2", "Observability with Micrometer", W + "aop/PerformanceAspect.java", "Timer.builder(\"skycast.method.duration\")", "Custom metrics"),
    # ---------------- UNIT 3: Spring Boot ----------------
    ("3", "Spring Boot application", W + "WeatherServiceApplication.java", "@SpringBootApplication", "Entry point"),
    ("3", "Auto-configuration (custom)", "weather-common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports", "SkycastSecurityAutoConfiguration", "Our own auto-config"),
    ("3", "Conditional beans", C + "security/SkycastSecurityAutoConfiguration.java", "@ConditionalOnMissingBean", "Back off when a service defines its own"),
    ("3", "Spring Boot starters", "weather-service/pom.xml", "spring-boot-starter-data-jpa", "web, data-jpa, security, batch, aop, cache ..."),
    ("3", "Embedded server (Tomcat / Netty)", "weather-service/src/main/resources/application.yml", "port: ${PORT:8082}", "Tomcat in services, Netty in gateway"),
    ("3", "Spring Boot CLI", "docs/VIVA-NOTES.md", "Spring Boot CLI", "Explained in viva notes"),
    ("3", "Building RESTful APIs", A + "web/AlertController.java", "@RestController", "CRUD + status codes"),
    ("3", "Spring Data JPA", W + "repository/LocationRepository.java", "extends JpaRepository<Location, Long>", "Repositories"),
    ("3", "Spring Security with JWT (issuing)", U + "service/JwtService.java", "Jwts.builder()", "jjwt HS256 tokens"),
    ("3", "Spring Boot Actuator", "weather-service/src/main/resources/application.yml", "include: health,info,metrics,prometheus", "Health probes, metrics"),
    ("3", "Spring profiles", "weather-service/src/main/resources/application-docker.yml", "jdbc:postgresql://", "dev (H2) vs docker (PostgreSQL)"),
    ("3", "Spring Cloud Eureka", "discovery-server/src/main/java/com/skycast/discovery/DiscoveryServerApplication.java", "@EnableEurekaServer", "Service registry"),
    ("3", "OpenFeign", A + "client/WeatherClient.java", "@FeignClient(name = \"weather-service\"", "Declarative client via Eureka"),
    ("3", "API Gateway", G + "GatewayRoutesConfig.java", "RouteLocatorBuilder", "Routes with lb://"),
    ("3", "Microservices development", "docker-compose.yml", "alert-service:", "5 services + DB"),
    ("3", "Resilience4j circuit breakers", W + "config/ResilienceConfig.java", "CircuitBreakerConfig.custom()", "Open-Meteo calls"),
    ("3", "Circuit breaker fallbacks", A + "client/WeatherClientFallbackFactory.java", "implements FallbackFactory<WeatherClient>", "Feign fallback"),
    ("3", "Configuration properties", C + "security/JwtProperties.java", "@ConfigurationProperties(prefix = \"skycast.jwt\")", "Type-safe config"),
    ("3", "Secrets management", "k8s/01-config.yaml", "kind: Secret", "JWT/DB secrets via env, .env, K8s Secret"),
    ("3", "Dockerizing Spring Boot", "Dockerfile", "eclipse-temurin:21-jre-alpine", "Small non-root runtime image"),
    ("3", "Docker Compose", "docker-compose.yml", "condition: service_healthy", "Ordered start with health checks"),
    ("3", "Kubernetes YAML manifests", "k8s/05-weather-service.yaml", "readinessProbe:", "Deployment + Service + probes"),
    ("3", "Helm chart", "helm/skycast/templates/services.yaml", "range $name, $svc := .Values.services", "Templated deployments"),
    ("3", "CI/CD deployment step", "Jenkinsfile", "helm upgrade --install", "Deploy from pipeline"),
    ("3", "Cloud deployment (AWS, Azure, serverless)", "docs/DEPLOYMENT.md", "Azure Container Apps", "Step-by-step guide"),
    # ---------------- UNIT 4: Spring ORM ----------------
    ("4", "Hibernate configured with Spring Boot", "weather-service/src/main/resources/application.yml", "hibernate:", "JPA/Hibernate properties"),
    ("4", "SessionFactory / Session", W + "repository/LocationRepositoryCustomImpl.java", "em.unwrap(Session.class)", "Native Hibernate Session"),
    ("4", "Entity mapping with annotations", W + "domain/WeatherSnapshot.java", "@Entity", "Annotated entities"),
    ("4", "Entity mapping with XML", "weather-service/src/main/resources/META-INF/orm.xml", "<entity class=\"com.skycast.weather.domain.FetchAuditLog\"", "FetchAuditLog mapped only in XML"),
    ("4", "CRUD operations", U + "service/FavoriteService.java", "favorites.delete(favorite)", "Create/read/update/delete"),
    ("4", "HQL", W + "repository/LocationRepositoryCustomImpl.java", "from Location l order by l.updatedAt desc", "HQL via Session"),
    ("4", "Named queries", "weather-service/src/main/resources/META-INF/orm.xml", "<named-query name=\"DailyWeather.findRange\">", "Used by DailyWeatherRepository"),
    ("4", "Named native query", "weather-service/src/main/resources/META-INF/orm.xml", "<named-native-query", "FetchAuditLog.failuresByEndpoint"),
    ("4", "Criteria API", W + "repository/LocationRepositoryCustomImpl.java", "CriteriaBuilder", "Dynamic search"),
    ("4", "Relationship mapping (1-N / N-1)", W + "domain/Location.java", "@OneToMany(mappedBy = \"location\")", "Location ↔ snapshots"),
    ("4", "Element collection", U + "domain/AppUser.java", "@ElementCollection(fetch = FetchType.EAGER)", "User roles"),
    ("4", "Lazy loading", W + "domain/WeatherSnapshot.java", "FetchType.LAZY", "Proxy until accessed"),
    ("4", "Eager loading", U + "domain/AppUser.java", "FetchType.EAGER", "Roles always loaded"),
    ("4", "Hibernate second-level cache", W + "domain/Location.java", "@Cache(usage = CacheConcurrencyStrategy.READ_WRITE", "JCache + Ehcache"),
    ("4", "Spring Cache abstraction", W + "service/WeatherService.java", "@Cacheable(cacheNames = \"forecast\"", "Caffeine cache"),
    ("4", "JPA repositories & custom query methods", W + "repository/LocationRepository.java", "findTop10ByNameContainingIgnoreCaseOrderByNameAsc", "Derived queries"),
    ("4", "Named parameters", W + "repository/LocationRepository.java", "@Param(\"country\")", "JPQL :country"),
    ("4", "JPQL and native queries", W + "repository/WeatherSnapshotRepository.java", "nativeQuery = true", "Native SQL with LIMIT"),
    ("4", "Bulk update query", A + "repository/WeatherAlertRepository.java", "@Modifying(clearAutomatically = true)", "acknowledgeAll"),
    ("4", "Transaction propagation", W + "service/FetchAuditService.java", "Propagation.REQUIRES_NEW", "Audit survives rollback"),
    ("4", "Exception handling in ORM", C + "error/GlobalExceptionHandler.java", "OptimisticLockingFailureException", "Mapped to 409"),
    ("4", "Optimistic locking", W + "domain/Location.java", "@Version", "Version column"),
    ("4", "Pessimistic locking", W + "repository/LocationRepository.java", "LockModeType.PESSIMISTIC_WRITE", "SELECT ... FOR UPDATE"),
    ("4", "Performance: fetch join / entity graph", W + "repository/WeatherSnapshotRepository.java", "join fetch s.location", "No N+1"),
    ("4", "Performance: JDBC batching", "weather-service/src/main/resources/application.yml", "batch_size: 50", "Batched inserts"),
    ("4", "Auditing and versioning", W + "domain/Auditable.java", "@CreatedDate", "Spring Data auditing"),
    ("4", "Testing Spring ORM", "weather-service/src/test/java/com/skycast/weather/repository/WeatherRepositoryTest.java", "@DataJpaTest", "Slice test on H2 + Flyway"),
    ("4", "Observability (OpenTelemetry tracing)", "weather-service/pom.xml", "micrometer-tracing-bridge-otel", "Trace ids in logs"),
    ("4", "DB migrations (Flyway)", "weather-service/src/main/resources/db/migration/V1__create_weather_schema.sql", "CREATE TABLE locations", "Versioned migrations"),
    ("4", "DB migrations (Liquibase)", "docs/VIVA-NOTES.md", "Liquibase", "Compared with Flyway"),
    ("4", "Performance tuning (indexes)", "weather-service/src/main/resources/db/migration/V1__create_weather_schema.sql", "CREATE INDEX idx_snapshot_location_time", "Composite index"),
]

UNITS = {"1": "Unit 1 – Maven", "2": "Unit 2 – Spring Framework", "3": "Unit 3 – Spring Boot", "4": "Unit 4 – Spring ORM"}


def main():
    failures = []
    for unit, topic, path, token, _ in TOPICS:
        file = ROOT / path
        if not file.is_file():
            failures.append(f"[U{unit}] {topic}: missing file {path}")
        elif token not in file.read_text(encoding="utf-8"):
            failures.append(f"[U{unit}] {topic}: '{token}' not found in {path}")
    ok = len(TOPICS) - len(failures)
    for f in failures:
        print("FAIL", f)
    print(f"{ok}/{len(TOPICS)} syllabus topics verified "
          f"({', '.join(f'U{u}: {sum(1 for t in TOPICS if t[0] == u)}' for u in UNITS)})")

    if "--write" in sys.argv:
        lines = ["# Syllabus → code mapping", "",
                 "Generated by `python verify/check-mapping.py --write`. Every row is checked: the file must exist "
                 "and contain the code shown in the *Look for* column.", ""]
        for unit, title in UNITS.items():
            lines += [f"## {title}", "", "| Topic | Where | Look for | How it is used |", "|---|---|---|---|"]
            for u, topic, path, token, how in TOPICS:
                if u == unit:
                    safe = token.replace("|", "\\|")
                    lines.append(f"| {topic} | [`{path.split('/')[-1]}`](../{path}) | `{safe}` | {how} |")
            lines.append("")
        (ROOT / "docs" / "SYLLABUS-MAPPING.md").write_text("\n".join(lines), encoding="utf-8")
        print("wrote docs/SYLLABUS-MAPPING.md")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
