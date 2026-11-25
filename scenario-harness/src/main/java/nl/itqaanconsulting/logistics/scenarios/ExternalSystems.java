package nl.itqaanconsulting.logistics.scenarios;

import com.github.tomakehurst.wiremock.WireMockServer;
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

    private final WireMockServer server = new WireMockServer(options().dynamicPort());

    public ExternalSystems start() {
        server.start();
        return this;
    }

    public int port() {
        return server.port();
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
        server.resetAll();

        scenario.registryResponse().ifPresent(payload -> server.stubFor(post(urlEqualTo(REGISTRY_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml; charset=UTF-8")
                        .withBody(soapEnvelope(payload)))));

        scenario.registryFault().ifPresent(fault -> server.stubFor(post(urlEqualTo(REGISTRY_PATH))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "text/xml; charset=UTF-8")
                        .withBody(soapFault(fault)))));

        server.stubFor(post(urlEqualTo(TRACK_TRACE_PATH))
                .willReturn(aResponse().withStatus(scenario.trackTraceStatus())));
    }

    public List<String> trackTraceRequests() {
        return server.findAll(postRequestedFor(urlEqualTo(TRACK_TRACE_PATH)))
                .stream()
                .map(LoggedRequest::getBodyAsString)
                .collect(Collectors.toList());
    }

    public int registryCalls() {
        return server.findAll(postRequestedFor(urlEqualTo(REGISTRY_PATH))).size();
    }

    @Override
    public void close() {
        server.stop();
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
