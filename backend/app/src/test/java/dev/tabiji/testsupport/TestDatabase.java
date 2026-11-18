package dev.tabiji.testsupport;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * One real PostgreSQL 18 per JVM for all integration tests (ADR-0005): a Testcontainers container when
 * Docker is available (CI), otherwise embedded PostgreSQL binaries (developer machines without Docker).
 * Force a mode with {@code -Dtabiji.test.db=container} or {@code -Dtabiji.test.db=embedded}.
 */
public final class TestDatabase {

    private static TestDatabase shared;

    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final String kind;

    private TestDatabase(String jdbcUrl, String username, String password, String kind) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.kind = kind;
    }

    public static synchronized TestDatabase shared() {
        if (shared == null) {
            shared = start();
        }
        return shared;
    }

    private static TestDatabase start() {
        String mode = System.getProperty("tabiji.test.db", "auto").toLowerCase(Locale.ROOT);
        boolean container = switch (mode) {
            case "container" -> true;
            case "embedded" -> false;
            default -> dockerAvailable();
        };
        return container ? startContainer() : startEmbedded();
    }

    private static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    @SuppressWarnings("resource") // stopped by Testcontainers' Ryuk reaper when the JVM exits
    private static TestDatabase startContainer() {
        PostgreSQLContainer container = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));
        container.start();
        return new TestDatabase(container.getJdbcUrl(), container.getUsername(), container.getPassword(), "container");
    }

    private static TestDatabase startEmbedded() {
        try {
            EmbeddedPostgres postgres = EmbeddedPostgres.builder().start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    postgres.close();
                } catch (IOException ignored) {
                    // JVM is exiting; nothing useful left to do
                }
            }));
            return new TestDatabase(postgres.getJdbcUrl("postgres", "postgres"), "postgres", "postgres", "embedded");
        } catch (IOException e) {
            throw new UncheckedIOException("could not start embedded PostgreSQL", e);
        }
    }

    public String jdbcUrl() {
        return jdbcUrl;
    }

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }

    public String kind() {
        return kind;
    }
}
