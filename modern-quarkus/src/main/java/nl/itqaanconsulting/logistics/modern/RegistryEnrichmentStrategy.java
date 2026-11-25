package nl.itqaanconsulting.logistics.modern;

import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;

/**
 * Combines the carrier event with the registry response into one document for the
 * mapping XSLT. Unchanged logic; only the interface moved from
 * {@code org.apache.camel.processor.aggregate} (Camel 2) to {@code org.apache.camel} (Camel 3+),
 * and the Blueprint {@code <bean>} became a CDI bean.
 */
@Singleton
@Named("registryEnrichment")
public class RegistryEnrichmentStrategy implements AggregationStrategy {

    @Override
    public Exchange aggregate(Exchange original, Exchange registry) {
        String event = withoutXmlDeclaration(original.getIn().getBody(String.class));
        String shipment = withoutXmlDeclaration(registry.getIn().getBody(String.class));
        original.getIn().setBody("<EnrichedStatus>" + event + shipment + "</EnrichedStatus>");
        return original;
    }

    static String withoutXmlDeclaration(String xml) {
        return xml.replaceFirst("^\\s*<\\?xml[^>]*\\?>", "").trim();
    }
}
