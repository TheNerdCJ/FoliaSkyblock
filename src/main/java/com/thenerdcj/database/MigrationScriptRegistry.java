package com.thenerdcj.database;

import com.thenerdcj.FoliaSkyblock;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Flyway-style named migration steps on top of {@link DatabaseMigration} version integers.
 * Register steps in Java and/or as {@code migrations/v{N}_{name}.sql} plugin resources.
 */
public final class MigrationScriptRegistry {

    private static final Pattern SQL_FILE = Pattern.compile("^v(\\d+)_(.+)\\.sql$");

    private final List<RegisteredStep> steps = new ArrayList<>();
    private final Set<Integer> versions = new HashSet<>();

    public MigrationScriptRegistry register(int version, String name, BiConsumer<FoliaSkyblock, Connection> runner) {
        if (!versions.contains(version)) {
            steps.add(new RegisteredStep(version, name, runner));
            versions.add(version);
            steps.sort(Comparator.comparingInt(RegisteredStep::version));
        }
        return this;
    }

    public boolean hasVersion(int version) {
        return versions.contains(version);
    }

    /**
     * Loads {@code migrations/v*_* .sql} from the plugin JAR or classpath directory.
     */
    public MigrationScriptRegistry loadFromResources(FoliaSkyblock plugin) {
        String resourceDir = "migrations";
        ClassLoader loader = plugin.getClass().getClassLoader();
        try {
            java.net.URL url = loader.getResource(resourceDir);
            if (url == null) {
                return this;
            }
            if ("jar".equals(url.getProtocol())) {
                loadSqlFromJar(plugin, url, resourceDir);
            } else {
                Path dir = Path.of(url.toURI());
                if (Files.isDirectory(dir)) {
                    try (Stream<Path> paths = Files.list(dir)) {
                        paths.filter(p -> p.getFileName().toString().endsWith(".sql"))
                                .sorted()
                                .forEach(p -> registerSqlFile(plugin, p.getFileName().toString(),
                                        () -> Files.newInputStream(p)));
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[DB] Failed to scan migration resources: " + e.getMessage());
        }
        return this;
    }

    private void loadSqlFromJar(FoliaSkyblock plugin, java.net.URL jarUrl, String resourceDir) throws Exception {
        String path = jarUrl.getPath();
        int sep = path.indexOf("!/");
        if (sep < 0) {
            return;
        }
        String jarPath = path.substring(5, sep);
        try (JarFile jar = new JarFile(java.net.URLDecoder.decode(jarPath, StandardCharsets.UTF_8))) {
            jar.stream()
                    .map(JarEntry::getName)
                    .filter(name -> name.startsWith(resourceDir + "/") && name.endsWith(".sql"))
                    .sorted()
                    .forEach(name -> {
                        String fileName = name.substring(name.lastIndexOf('/') + 1);
                        registerSqlFile(plugin, fileName, () -> plugin.getClass().getClassLoader().getResourceAsStream(name));
                    });
        }
    }

    private void registerSqlFile(FoliaSkyblock plugin, String fileName, SqlStreamSupplier streamSupplier) {
        Matcher m = SQL_FILE.matcher(fileName);
        if (!m.matches()) {
            plugin.getLogger().warning("[DB] Ignoring migration file (bad name): " + fileName);
            return;
        }
        int version = Integer.parseInt(m.group(1));
        String name = m.group(2);
        if (hasVersion(version)) {
            return;
        }
        register(version, name, (p, conn) -> {
            try {
                executeSqlScript(p, conn, fileName, streamSupplier);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static void executeSqlScript(FoliaSkyblock plugin, Connection conn, String fileName, SqlStreamSupplier supplier)
            throws SQLException {
        try (InputStream in = supplier.open()) {
            if (in == null) {
                throw new SQLException("Migration resource missing: " + fileName);
            }
            String sql = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .lines()
                    .collect(java.util.stream.Collectors.joining("\n"));
            for (String stmt : splitStatements(sql)) {
                try (Statement s = conn.createStatement()) {
                    s.execute(stmt);
                }
            }
            plugin.getLogger().info("[DB] Applied SQL migration from " + fileName);
        } catch (SQLException e) {
            throw e;
        } catch (Exception e) {
            throw new SQLException("Failed to read " + fileName + ": " + e.getMessage(), e);
        }
    }

    static List<String> splitStatements(String sql) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : sql.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                continue;
            }
            current.append(line).append('\n');
            if (trimmed.endsWith(";")) {
                String stmt = current.toString().trim();
                if (stmt.endsWith(";")) {
                    stmt = stmt.substring(0, stmt.length() - 1).trim();
                }
                if (!stmt.isEmpty()) {
                    out.add(stmt);
                }
                current.setLength(0);
            }
        }
        String tail = current.toString().trim();
        if (!tail.isEmpty()) {
            if (tail.endsWith(";")) {
                tail = tail.substring(0, tail.length() - 1).trim();
            }
            if (!tail.isEmpty()) {
                out.add(tail);
            }
        }
        return out;
    }

    public void runRange(FoliaSkyblock plugin, Connection conn, int fromExclusive, int toInclusive) throws SQLException {
        for (RegisteredStep step : steps) {
            if (step.version() <= fromExclusive || step.version() > toInclusive) {
                continue;
            }
            plugin.getLogger().info("[DB] Script v" + step.version() + ": " + step.name());
            try {
                step.runner().accept(plugin, conn);
            } catch (Exception e) {
                throw new SQLException("Migration script v" + step.version() + " (" + step.name() + ") failed: " + e.getMessage(), e);
            }
        }
    }

    public static MigrationScriptRegistry createDefault(FoliaSkyblock plugin) {
        MigrationScriptRegistry registry = new MigrationScriptRegistry();
        registry.loadFromResources(plugin);
        if (!registry.hasVersion(14)) {
            registry.register(14, "island_worth_grid_index", (p, conn) -> {
                try (var ps = conn.prepareStatement(
                        "CREATE INDEX IF NOT EXISTS idx_island_worth_grid ON island_worth(grid_x, grid_z, dimension)")) {
                    ps.executeUpdate();
                } catch (SQLException ignored) {
                }
                p.getLogger().info("[DB] v14: island_worth grid index (Java fallback).");
            });
        }
        return registry;
    }

    @FunctionalInterface
    private interface SqlStreamSupplier {
        InputStream open() throws Exception;
    }

    private record RegisteredStep(int version, String name, BiConsumer<FoliaSkyblock, Connection> runner) {}
}