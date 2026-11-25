# Migration guide: Fuse 7 to Camel 4 on Quarkus

This guide describes how the shipment status integration in this repository moved from Red Hat Fuse 7 (Camel 2.23 on Karaf) to Camel 4 on Quarkus, and what to watch for when you do the same with your own Fuse integrations. Every step is visible in the git history: the legacy bundle and the migrated application live side by side and pass the same scenarios.

## Why migrate now

Fuse 7 maintenance ended in June 2024 and Extended Life Cycle Support ended in June 2026. Karaf (OSGi) and JBoss EAP are no longer supported runtimes for Camel. Red Hat's successor is the Red Hat build of Apache Camel, for Quarkus or Spring Boot. This repository targets Quarkus.

## The approach

1. **Freeze behaviour first.** Before touching the code, capture what the integration does as golden-file scenarios (`test-scenarios/`): input messages, the responses of the surrounding systems, and the expected result (the request sent downstream, or the queue a message ends up on). Run them against the Fuse version until they pass. They are the acceptance criteria for the migration.
2. **Keep the contracts.** XSD, WSDL and XSLT belong to the integration, not to the runtime. They move unchanged (`contracts/`, `xslt/`), and CI checks that the mappings stay identical.
3. **Migrate the routes with as few changes as possible.** Camel 4 can still read XML routes (XML IO DSL), so the Blueprint routes move almost one-to-one. Rewriting them to Java or YAML DSL is a separate, later decision.
4. **Replace the runtime services.** Blueprint beans become CDI beans, Config Admin becomes MicroProfile Config, Karaf features become Camel Quarkus extensions, Hawtio/JMX becomes health probes and Prometheus metrics.
5. **Run the same scenarios against the new version.** Any difference is either a bug in the migration or a behaviour change in a library that needs a decision. Both happened here (see "What the scenarios caught").

## Mapping reference

### Runtime and configuration

| Fuse 7 (Karaf) | Camel 4 on Quarkus |
| --- | --- |
| OSGi bundle (`packaging=bundle`, `maven-bundle-plugin`) | Quarkus application (`quarkus-maven-plugin`), runnable jar or container image |
| Karaf features (`camel-cxf`, `camel-jms`, ...) | Camel Quarkus extensions (`camel-quarkus-cxf-soap`, `camel-quarkus-jms`, ...) |
| `<cm:property-placeholder persistent-id="...">` with `etc/<pid>.cfg` | `application.properties`, overridable with environment variables (ConfigMap on OpenShift) |
| `${jms.brokerUrl}` (Blueprint placeholder) | `{{jms.broker-url}}` (Camel placeholder) or `@ConfigProperty` (Java) |
| camelCase keys | kebab-case keys; `jms.broker-url` maps to env var `JMS_BROKER_URL` |
| Blueprint `<bean>` | CDI producer method or `@Singleton @Named` class (`IntegrationBeans`) |
| `<cxf:cxfEndpoint>` | `CxfEndpoint` produced as a named CDI bean |
| `javax.jms`, `artemis-jms-client` | `jakarta.jms`, `artemis-jakarta-client` |
| Hawtio, JMX | `/q/health/live`, `/q/health/ready`, `/q/health/started`, `/q/metrics` |
| Karaf container, `features:install` | Container image on OpenShift, Kustomize manifests (`openshift/`) |

### Route DSL (Camel 2 to Camel 4)

| Camel 2.23 (Blueprint XML) | Camel 4 (XML IO DSL) |
| --- | --- |
| `<camelContext xmlns="http://camel.apache.org/schema/blueprint">` | `<routes xmlns="http://camel.apache.org/schema/xml-io">` |
| `http4://host/path` | `http://host/path` |
| `xslt:file.xsl?saxon=true` | `xslt-saxon:file.xsl` |
| `org.apache.camel.http.common.HttpOperationFailedException` | `org.apache.camel.http.base.HttpOperationFailedException` |
| `<enrich strategyRef="...">` | `<enrich aggregationStrategy="...">` |
| `<idempotentConsumer messageIdRepositoryRef="...">` | `<idempotentConsumer idempotentRepository="...">` |
| `<setHeader headerName="...">` | `<setHeader name="...">` |
| `org.apache.camel.processor.aggregate.AggregationStrategy` | `org.apache.camel.AggregationStrategy` |
| `org.apache.camel.processor.idempotent.MemoryIdempotentRepository` | `org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository` |

Compare `legacy-fuse7/src/main/resources/OSGI-INF/blueprint/camel-context.xml` with `modern-quarkus/src/main/resources/camel/shipment-status.xml` for the complete picture.

## What the scenarios caught

### HttpClient 5 retries on its own

The `tracktrace-unavailable` scenario expects three calls to Track and Trace when it answers `503`: the first attempt plus two Camel redeliveries. The Camel 4 version made six.

Camel 4's `http` component uses Apache HttpClient 5, whose default retry strategy retries `503` and `429` responses by itself. Combined with the Camel redelivery policy, every Camel attempt became two HTTP calls. In production that doubles the load on a system that is already overloaded.

The fix is one line, so that Camel alone decides on retries, as on Fuse:

```properties
camel.component.http.automatic-retries-disabled=true
```

Without the scenarios this change would only have shown up in production, as a load spike on the downstream system during an incident.

### Camel Quarkus includes only what you declare

On Fuse, `camel-core` contains the bean language. A Simple expression such as `${exception.statusCode}` calls a method and needs it. In Camel Quarkus the route failed to start with "No language could be found for: bean" until `camel-quarkus-bean` was added. Expect more of these: every component, language and data format is a separate extension.

### Config names differ between Camel Main and Camel Quarkus

`camel.metrics.enable-route-policy` is Camel Main configuration and pulls in `camel-micrometer-prometheus`. In Camel Quarkus the equivalent is `quarkus.camel.metrics.*`, and route metrics are on by default.

## Running Fuse 7 code on a current JDK

The legacy module still has to build and be tested during the migration, often on a newer JDK than the one in production. These are the problems you will run into and how this repository solves them (all in `legacy-fuse7/pom.xml`):

| Symptom | Cause | Fix |
| --- | --- | --- |
| `ClassNotFoundException: javax.xml.bind.JAXBException`, `javax.xml.ws.WebServiceFeature`, `javax.jws.WebService`, `javax.xml.soap` | Java 11 removed Java EE modules from the JDK; Karaf provided them as bundles | Add `jaxb-api`, `jaxb-runtime`, `jaxws-api`, `javax.jws-api`, `javax.xml.soap-api`, `saaj-impl`, `javax.annotation-api`, `javax.activation-api` (test scope) |
| `NoSuchMethodException: sun.misc.Unsafe.defineClass` in JAXB | JAXB 2.3 bytecode optimisation uses an API removed after Java 11 | `-Dcom.sun.xml.bind.v2.bytecode.ClassTailor.noOptimize=true` |
| `IllegalArgumentException: Invalid Java version 65` | Aries Proxy 1.1.1 cannot weave Java 21 class files | Aries Proxy 1.1.14 |
| `NoClassDefFoundError: org/objectweb/asm/commons/AdviceAdapter` | ASM is a Karaf bundle, not on the test classpath | `asm-commons` (test scope) |
| Config overrides ignored or Blueprint container "gave up waiting" in tests | `useOverridePropertiesWithConfigAdmin` updates the configuration after start and relies on a reload | `setConfigAdminInitialConfiguration`, which supplies the configuration before the container starts |
| Blueprint container started twice, one with default settings | `target/classes` is also picked up as a bundle | `getBundleFilter()` excluding the module's own bundle |

CI runs the legacy scenarios on JDK 11 (the Fuse 7.13 runtime) and JDK 21.

## Cutover

The integration reads from a queue and writes to an API, which makes a safe switch possible:

1. Deploy the Camel 4 version next to Fuse, consuming from a copy of the inbound queue (an Artemis divert), with Track and Trace pointed at a stub or a shadow endpoint. Compare its output with production.
2. Stop the Fuse consumer, then start the Camel 4 consumer on the real queue. Messages wait on the queue in between; nothing is lost.
3. Keep the Fuse bundle installed but stopped until the new version has handled at least one full business cycle. Rolling back means stopping one consumer and starting the other.

## Known limitations and next steps

- **Idempotency is per instance.** The idempotent repository is in memory, as on Fuse, so the deployment runs one replica. To scale out, replace it with a shared repository (`camel-quarkus-sql` with `JdbcMessageIdRepository`, or Infinispan) and raise the replica count.
- **Native image is not covered.** The application runs in JVM mode. CXF and the Artemis client need extra native configuration.
- **Routes are still XML.** Moving to Java or YAML DSL is optional and easier to do once the migration has stabilised, with the same scenarios as a safety net.
