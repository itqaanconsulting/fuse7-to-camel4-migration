package nl.itqaanconsulting.logistics.modern;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import nl.itqaanconsulting.logistics.scenarios.ExternalSystems;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Map;

/**
 * Starts an embedded AMQ (Artemis) broker and the simulated external systems before
 * Quarkus boots, and hands their addresses to the application as configuration.
 * No Docker needed.
 */
public class IntegrationTestResources implements QuarkusTestResourceLifecycleManager {

    private EmbeddedActiveMQ broker;
    private ExternalSystems systems;

    @Override
    public Map<String, String> start() {
        try {
            int brokerPort = freePort();
            broker = new EmbeddedActiveMQ();
            broker.setConfiguration(new ConfigurationImpl()
                    .setPersistenceEnabled(false)
                    .setSecurityEnabled(false)
                    .addAcceptorConfiguration("tcp", "tcp://localhost:" + brokerPort));
            broker.start();
            systems = new ExternalSystems().start();

            return Map.of(
                    "jms.broker-url", "tcp://localhost:" + brokerPort,
                    "registry.url", systems.registryUrl(),
                    "tracktrace.authority", systems.trackTraceAuthority(),
                    "test.external-systems.port", String.valueOf(systems.port()));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not start test infrastructure", exception);
        }
    }

    @Override
    public void stop() {
        try {
            if (systems != null) {
                systems.close();
            }
            if (broker != null) {
                broker.stop();
            }
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
