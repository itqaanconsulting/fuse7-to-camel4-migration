package nl.itqaanconsulting.logistics.modern;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

/** The endpoints OpenShift uses for probes and Prometheus scraping. */
@QuarkusTest
@QuarkusTestResource(IntegrationTestResources.class)
class OperationsEndpointsTest {

    @Test
    void reportsLiveAndReadyWhenRoutesAreStarted() {
        given().when().get("/q/health/live").then().statusCode(200).body("status", equalTo("UP"));
        given().when().get("/q/health/ready").then().statusCode(200).body("status", equalTo("UP"));
        given().when().get("/q/health/started").then().statusCode(200).body("status", equalTo("UP"));
    }

    @Test
    void exposesRouteMetricsForPrometheus() {
        given().when().get("/q/metrics").then()
                .statusCode(200)
                .body(containsString("routeId=\"carrier-status-in\""));
    }
}
