package nl.itqaanconsulting.logistics.scenarios;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Checks the observable behaviour of a scenario: which requests reached Track and Trace
 * and what they contained. Queue outcomes (invalid, dead letter) are checked by the
 * module tests, because the JMS API differs (javax.jms vs jakarta.jms).
 */
public final class ScenarioAssertions {

    private static final ObjectMapper JSON = new ObjectMapper();

    private ScenarioAssertions() {
    }

    public static void assertTrackTraceRequests(Scenario scenario, ExternalSystems systems) {
        List<String> requests = systems.trackTraceRequests();
        if (requests.size() != scenario.expectedTrackTraceCalls()) {
            throw new AssertionError(scenario + ": expected " + scenario.expectedTrackTraceCalls()
                    + " Track and Trace call(s) but got " + requests.size() + ": " + requests);
        }
        scenario.expectedRequest().ifPresent(expected -> {
            JsonNode expectedJson = parse(expected);
            for (String actual : requests) {
                JsonNode actualJson = parse(actual);
                if (!expectedJson.equals(actualJson)) {
                    throw new AssertionError(scenario + ": Track and Trace request differs.\nexpected: "
                            + expectedJson + "\nactual:   " + actualJson);
                }
            }
        });
    }

    /** Waits until the condition holds, or fails after the timeout. */
    public static void await(String description, Duration timeout, BooleanSupplier condition) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            sleep(50);
        }
        throw new AssertionError("Timed out after " + timeout + " waiting for: " + description);
    }

    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private static JsonNode parse(String json) {
        try {
            return JSON.readTree(json);
        } catch (IOException exception) {
            throw new UncheckedIOException("Not valid JSON: " + json, exception);
        }
    }
}
