<?xml version="1.0" encoding="UTF-8"?>
<!-- Builds the GetShipment SOAP payload from a carrier status event. -->
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:cs="urn:logistics:carrier:status:v1"
                xmlns:reg="urn:logistics:registry:v1"
                exclude-result-prefixes="cs">
  <xsl:output method="xml" omit-xml-declaration="yes"/>

  <xsl:template match="/cs:ShipmentStatus">
    <reg:GetShipmentRequest>
      <reg:trackingNumber><xsl:value-of select="cs:TrackingNumber"/></reg:trackingNumber>
    </reg:GetShipmentRequest>
  </xsl:template>
</xsl:stylesheet>
