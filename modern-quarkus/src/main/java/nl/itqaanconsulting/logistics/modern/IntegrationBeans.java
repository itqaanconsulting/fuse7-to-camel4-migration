package nl.itqaanconsulting.logistics.modern;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import jakarta.jms.ConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.camel.builder.NoErrorHandlerBuilder;
import org.apache.camel.component.cxf.common.DataFormat;
import org.apache.camel.component.cxf.jaxws.CxfEndpoint;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.xml.namespace.QName;

/**
 * The {@code <bean>} and {@code <cxf:cxfEndpoint>} definitions of the Blueprint XML,
 * as CDI producers. Values come from MicroProfile Config (application.properties,
 * overridable with environment variables on OpenShift) instead of Config Admin.
 */
public class IntegrationBeans {

    private static final String REGISTRY_NAMESPACE = "urn:logistics:registry:v1";

    /** Picked up by camel-quarkus-jms as the connection factory of the jms component. */
    @Produces
    @Singleton
    ConnectionFactory artemisConnectionFactory(
            @ConfigProperty(name = "jms.broker-url") String brokerUrl,
            @ConfigProperty(name = "jms.username") String username,
            @ConfigProperty(name = "jms.password") String password) {
        return new ActiveMQConnectionFactory(brokerUrl, username, password);
    }

    @Produces
    @Singleton
    @Named("shipmentRegistry")
    CxfEndpoint shipmentRegistry(@ConfigProperty(name = "registry.url") String registryUrl) {
        CxfEndpoint endpoint = new CxfEndpoint();
        endpoint.setAddress(registryUrl);
        endpoint.setWsdlURL("wsdl/shipment-registry.wsdl");
        endpoint.setServiceNameAsQName(new QName(REGISTRY_NAMESPACE, "ShipmentRegistryService"));
        endpoint.setPortNameAsQName(new QName(REGISTRY_NAMESPACE, "ShipmentRegistryPort"));
        endpoint.setDataFormat(DataFormat.PAYLOAD);
        return endpoint;
    }

    @Produces
    @Singleton
    @Named("processedEvents")
    IdempotentRepository processedEvents() {
        return MemoryIdempotentRepository.memoryIdempotentRepository(10_000);
    }

    /** Lets exceptions of the lookup sub-route reach the error handling of the main route. */
    @Produces
    @Singleton
    @Named("noErrorHandler")
    NoErrorHandlerBuilder noErrorHandler() {
        return new NoErrorHandlerBuilder();
    }
}
