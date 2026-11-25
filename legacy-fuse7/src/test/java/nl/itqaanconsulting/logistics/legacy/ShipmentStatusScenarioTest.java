package nl.itqaanconsulting.logistics.legacy;

import nl.itqaanconsulting.logistics.scenarios.ExternalSystems;
import nl.itqaanconsulting.logistics.scenarios.Scenario;
import nl.itqaanconsulting.logistics.scenarios.ScenarioAssertions;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.apache.camel.test.blueprint.CamelBlueprintTestSupport;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.time.Duration;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Runs every golden-file scenario against the Fuse 7 Blueprint route, with an embedded
 * AMQ 7 (Artemis) broker and simulated registry and Track and Trace systems.
 */
@RunWith(Parameterized.class)
public class ShipmentStatusScenarioTest extends CamelBlueprintTestSupport {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private static EmbeddedActiveMQ broker;
    private static ExternalSystems systems;

    private final Scenario scenario;

    public ShipmentStatusScenarioTest(Scenario scenario) {
        this.scenario = scenario;
    }

    @Parameterized.Parameters(name = "{0}")
    public static List<Scenario> scenarios() {
        return Scenario.all();
    }

    @BeforeClass
    public static void startInfrastructure() throws Exception {
        broker = new EmbeddedActiveMQ();
        broker.setConfiguration(new ConfigurationImpl()
                .setPersistenceEnabled(false)
                .setSecurityEnabled(false)
                .addAcceptorConfiguration("in-vm", "vm://0"));
        broker.start();
        systems = new ExternalSystems().start();
    }

    @AfterClass
    public static void stopInfrastructure() throws Exception {
        systems.close();
        broker.stop();
    }

    @Override
    protected String getBlueprintDescriptor() {
        return "OSGI-INF/blueprint/camel-context.xml";
    }

    /** The test bundle already contains the Blueprint XML; do not start the module's own bundle a second time. */
    @Override
    protected String getBundleFilter() {
        return "(!(Bundle-SymbolicName=nl.itqaanconsulting.logistics.shipmentstatus))";
    }

    /**
     * Same keys an operator would put in etc/nl.itqaanconsulting.logistics.shipmentstatus.cfg.
     * Supplied before the Blueprint container starts, so no reload is needed.
     */
    @Override
    protected String setConfigAdminInitialConfiguration(Properties props) {
        props.put("jms.brokerUrl", "vm://0");
        props.put("registry.url", systems.registryUrl());
        props.put("tracktrace.authority", systems.trackTraceAuthority());
        props.put("delivery.redeliveryDelay", "50");
        return "nl.itqaanconsulting.logistics.shipmentstatus";
    }

    @Test
    public void behavesAsSpecified() {
        systems.prepare(scenario);

        for (String message : scenario.messages()) {
            template.sendBody("jms:queue:carrier.status.in", message);
        }

        switch (scenario.expectedOutcome()) {
            case DELIVERED:
                ScenarioAssertions.await("Track and Trace request", TIMEOUT,
                        () -> systems.trackTraceRequests().size() >= scenario.expectedTrackTraceCalls());
                // Give a duplicate the chance to (wrongly) arrive as well.
                ScenarioAssertions.sleep(500);
                assertNoMessage("carrier.status.dlq");
                break;
            case INVALID:
                assertNotNull(scenario + ": expected message on invalid queue",
                        consumer.receiveBody("jms:queue:carrier.status.invalid", TIMEOUT.toMillis()));
                break;
            case DEAD_LETTER:
                Object deadLetter = consumer.receiveBody("jms:queue:carrier.status.dlq", TIMEOUT.toMillis());
                assertNotNull(scenario + ": expected message on dead letter queue", deadLetter);
                assertEquals(scenario + ": dead letter must contain the original carrier message",
                        scenario.messages().get(0).trim(), deadLetter.toString().trim());
                break;
            default:
                throw new IllegalStateException("Unknown outcome " + scenario.expectedOutcome());
        }

        ScenarioAssertions.assertTrackTraceRequests(scenario, systems);
    }

    private void assertNoMessage(String queue) {
        assertNull(scenario + ": unexpected message on " + queue,
                consumer.receiveBody("jms:queue:" + queue, 200));
    }
}
