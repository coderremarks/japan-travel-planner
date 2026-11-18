package dev.tabiji;

import dev.tabiji.testsupport.TestDatabase;
import org.springframework.boot.SpringApplication;

/**
 * Local development launcher: starts the API with the {@code local} profile against a real
 * PostgreSQL 18 — a Testcontainers container when Docker is available, embedded binaries otherwise —
 * so a fresh clone runs with nothing but a JDK.
 *
 * <pre>./mvnw -pl app -am spring-boot:test-run</pre>
 *
 * The database is ephemeral: it disappears when the process stops.
 */
public final class TestTabijiApplication {

    private TestTabijiApplication() {}

    public static void main(String[] args) {
        TestDatabase database = TestDatabase.shared();
        System.setProperty("spring.datasource.url", database.jdbcUrl());
        System.setProperty("spring.datasource.username", database.username());
        System.setProperty("spring.datasource.password", database.password());
        SpringApplication.from(TabijiApplication::main)
                .withAdditionalProfiles("local")
                .run(args);
    }
}
