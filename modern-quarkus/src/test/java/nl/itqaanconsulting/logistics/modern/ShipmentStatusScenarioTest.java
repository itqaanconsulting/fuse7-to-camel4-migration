package nl.itqaanconsulting.logistics.modern;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import nl.itqaanconsulting.logistics.scenarios.ExternalSystems;
import nl.itqaanconsulting.logistics.scenarios.Scenario;
import nl.itqaanconsulting.logistics.scenarios.ScenarioAssertions;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.ProducerTemplate;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Runs every golden-file scenario against the Camel 4 / Quarkus application.
 * Same scenarios, same checks as the Fuse 7 test: equal results prove equal behaviour.
 */
@QuarkusTest
@QuarkusTestResource(IntegrationTestResources.class)
class ShipmentStatusScenarioTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    @Inject
    ProducerTemplate template;

    @Inject
    ConsumerTemplate consumer;

    @ConfigProperty(name = "test.external-systems.port")
    int externalSystemsPort;

    static List<Scenario> scenarios() {
        return Scenario.all();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void behavesAsSpecified(Scenario scenario) {
        ExternalSystems systems = ExternalSystems.connect(externalSystemsPort);
        systems.prepare(scenario);

        for (String message : scenario.messages()) {
            template.sendBody("jms:queue:carrier.status.in", message);
        }

        switch (scenario.expectedOutcome()) {
            case DELIVERED -> {
                ScenarioAssertions.await("Track and Trace request", TIMEOUT,
                        () -> systems.trackTraceRequests().size() >= scenario.expectedTrackTraceCalls());
                // Give a duplicate the chance to (wrongly) arrive as well.
                ScenarioAssertions.sleep(500);
                assertNull(consumer.receiveBody("jms:queue:carrier.status.dlq", 200),
                        scenario + ": unexpected message on dead letter queue");
            }
            case INVALID -> assertNotNull(
                    consumer.receiveBody("jms:queue:carrier.status.invalid", TIMEOUT.toMillis()),
                    scenario + ": expected message on invalid queue");
            case DEAD_LETTER -> {
                Object deadLetter = consumer.receiveBody("jms:queue:carrier.status.dlq", TIMEOUT.toMillis());
                assertNotNull(deadLetter, scenario + ": expected message on dead letter queue");
                assertEquals(scenario.messages().get(0).trim(), deadLetter.toString().trim(),
                        scenario + ": dead letter must contain the original carrier message");
            }
        }

        ScenarioAssertions.assertTrackTraceRequests(scenario, systems);
    }
}
