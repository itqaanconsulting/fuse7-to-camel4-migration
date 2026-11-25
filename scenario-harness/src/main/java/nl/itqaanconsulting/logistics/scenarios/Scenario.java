package nl.itqaanconsulting.logistics.scenarios;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * One golden-file scenario from {@code test-scenarios/<name>/scenario.properties}.
 * The same scenario files drive the Fuse 7 tests and the Camel 4 tests: if both
 * implementations pass them, the migration did not change behaviour.
 */
public final class Scenario {

    public enum Outcome { DELIVERED, INVALID, DEAD_LETTER }

    private final String name;
    private final Path directory;
    private final Properties properties;

    private Scenario(Path directory, Properties properties) {
        this.name = directory.getFileName().toString();
        this.directory = directory;
        this.properties = properties;
    }

    /** All scenarios below the directory given by system property {@code scenarios.dir}. */
    public static List<Scenario> all() {
        String location = System.getProperty("scenarios.dir");
        if (location == null) {
            throw new IllegalStateException("System property scenarios.dir is not set");
        }
        try (Stream<Path> directories = Files.list(Path.of(location))) {
            return directories
                    .filter(directory -> Files.exists(directory.resolve("scenario.properties")))
                    .sorted()
                    .map(Scenario::load)
                    .collect(Collectors.toList());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static Scenario load(Path directory) {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(directory.resolve("scenario.properties"))) {
            properties.load(input);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return new Scenario(directory, properties);
    }

    public String name() {
        return name;
    }

    public String description() {
        return properties.getProperty("description", name);
    }

    /** Carrier messages to put on the inbound queue, in order. */
    public List<String> messages() {
        return Arrays.stream(required("messages").split(","))
                .map(String::trim)
                .map(this::read)
                .collect(Collectors.toList());
    }

    public Optional<String> registryResponse() {
        return Optional.ofNullable(properties.getProperty("registry.response")).map(this::read);
    }

    public Optional<String> registryFault() {
        return Optional.ofNullable(properties.getProperty("registry.fault"));
    }

    public int trackTraceStatus() {
        return Integer.parseInt(properties.getProperty("tracktrace.status", "202"));
    }

    public Outcome expectedOutcome() {
        return Outcome.valueOf(required("expected.outcome"));
    }

    public int expectedTrackTraceCalls() {
        return Integer.parseInt(required("expected.trackTraceCalls"));
    }

    public Optional<String> expectedRequest() {
        return Optional.ofNullable(properties.getProperty("expected.request")).map(this::read);
    }

    private String required(String key) {
        String value = properties.getProperty(key);
        if (value == null) {
            throw new IllegalStateException("Scenario " + name + " misses property " + key);
        }
        return value;
    }

    private String read(String file) {
        try {
            return Files.readString(directory.resolve(file), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    @Override
    public String toString() {
        return name;
    }
}
