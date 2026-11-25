package nl.itqaanconsulting.logistics.legacy;

import org.apache.camel.Exchange;
import org.apache.camel.processor.aggregate.AggregationStrategy;

/**
 * Combines the carrier event with the registry response into one document for the
 * mapping XSLT: {@code <EnrichedStatus>event registryResponse</EnrichedStatus>}.
 *
 * <p>Camel 2 API: {@code org.apache.camel.processor.aggregate.AggregationStrategy}.
 * In Camel 3+ the interface moved to {@code org.apache.camel.AggregationStrategy}.
 */
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
