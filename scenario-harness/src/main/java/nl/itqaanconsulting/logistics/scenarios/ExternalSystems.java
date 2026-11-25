package nl.itqaanconsulting.logistics.scenarios;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import java.util.List;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * Simulates the systems around the integration: the SOAP shipment registry and the
 * Track and Trace REST API. One WireMock server, two paths.
 */
public final class ExternalSystems implements AutoCloseable {

    public static final String REGISTRY_PATH = "/registry";
    public static final String TRACK_TRACE_PATH = "/api/shipment-events";

    private final WireMockServer server;
    private WireMock client;
    private int port;

    public ExternalSystems() {
        this.server = new WireMockServer(options().dynamicPort());
    }

    private ExternalSystems(int port) {
        this.server = null;
        this.port = port;
        this.client = new WireMock("localhost", port);
    }

    /**
     * Connects to systems already started elsewhere, for example by a Quarkus test resource
     * that has to know the port before the application starts.
     */
    public static ExternalSystems connect(int port) {
        return new ExternalSystems(port);
    }

    public ExternalSystems start() {
        server.start();
        port = server.port();
        client = new WireMock("localhost", port);
        return this;
    }

    public int port() {
        return port;
    }

    public String registryUrl() {
        return "http://localhost:" + port() + REGISTRY_PATH;
    }

    /** Host and port of Track and Trace, without scheme. */
    public String trackTraceAuthority() {
        return "localhost:" + port();
    }

    /** Resets all stubs and recorded requests, then stubs both systems for the scenario. */
    public void prepare(Scenario scenario) {
        client.resetMappings();
        client.resetRequests();

        scenario.registryResponse().ifPresent(payload -> client.register(post(urlEqualTo(REGISTRY_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml; charset=UTF-8")
                        .withBody(soapEnvelope(payload)))));

        scenario.registryFault().ifPresent(fault -> client.register(post(urlEqualTo(REGISTRY_PATH))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "text/xml; charset=UTF-8")
                        .withBody(soapFault(fault)))));

        client.register(post(urlEqualTo(TRACK_TRACE_PATH))
                .willReturn(aResponse().withStatus(scenario.trackTraceStatus())));
    }

    public List<String> trackTraceRequests() {
        return client.find(postRequestedFor(urlEqualTo(TRACK_TRACE_PATH)))
                .stream()
                .map(LoggedRequest::getBodyAsString)
                .collect(Collectors.toList());
    }

    public int registryCalls() {
        return client.find(postRequestedFor(urlEqualTo(REGISTRY_PATH))).size();
    }

    @Override
    public void close() {
        if (server != null) {
            server.stop();
        }
    }

    private static String soapEnvelope(String payload) {
        return "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body>"
                + payload
                + "</soap:Body></soap:Envelope>";
    }

    private static String soapFault(String reason) {
        return "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body>"
                + "<soap:Fault><faultcode>soap:Client</faultcode><faultstring>" + reason + "</faultstring></soap:Fault>"
                + "</soap:Body></soap:Envelope>";
    }
}
