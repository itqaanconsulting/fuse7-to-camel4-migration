# Fuse 7 to Camel 4 migration

Work in progress. A logistics integration (carrier status events → shipment registry → Track and Trace) as it runs on Red Hat Fuse 7, migrated step by step to Camel 4 on Quarkus and OpenShift, with golden-file scenarios that prove both versions behave the same.

- `legacy-fuse7/`: the starting point, a Fuse 7 bundle (Camel 2.23, Blueprint XML, AMQ 7, CXF)
- `test-scenarios/`: shared input and expected output for every scenario
- `scenario-harness/`: test support shared by the legacy and the modern tests
- `contracts/`: carrier XSD and registry WSDL (unchanged by the migration)

```bash
mvn verify
```
