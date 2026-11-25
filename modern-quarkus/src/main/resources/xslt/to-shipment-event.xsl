<?xml version="1.0" encoding="UTF-8"?>
<!--
  Maps a carrier status event, enriched with registry data, to the canonical
  Track & Trace JSON event. Input:

    <EnrichedStatus>
      <cs:ShipmentStatus .../>        (carrier event)
      <reg:GetShipmentResponse .../>  (registry lookup)
    </EnrichedStatus>
-->
<xsl:stylesheet version="3.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                xmlns:fn="http://www.w3.org/2005/xpath-functions"
                xmlns:cs="urn:logistics:carrier:status:v1"
                xmlns:reg="urn:logistics:registry:v1"
                exclude-result-prefixes="xs cs reg">
  <xsl:output method="text" encoding="UTF-8"/>

  <xsl:template match="/EnrichedStatus">
    <xsl:variable name="status" select="cs:ShipmentStatus"/>
    <xsl:variable name="shipment" select="reg:GetShipmentResponse"/>
    <xsl:variable name="json" as="element()">
      <fn:map>
        <fn:string key="eventId"><xsl:value-of select="$status/@eventId"/></fn:string>
        <fn:string key="shipmentId"><xsl:value-of select="$shipment/reg:shipmentId"/></fn:string>
        <fn:string key="customerReference"><xsl:value-of select="$shipment/reg:customerReference"/></fn:string>
        <fn:string key="carrier"><xsl:value-of select="$status/cs:Carrier"/></fn:string>
        <fn:string key="trackingNumber"><xsl:value-of select="$status/cs:TrackingNumber"/></fn:string>
        <fn:string key="status"><xsl:value-of select="cs:canonical-status($status/cs:StatusCode)"/></fn:string>
        <fn:string key="occurredAt"><xsl:value-of select="$status/cs:EventTime"/></fn:string>
        <xsl:if test="$status/cs:Location">
          <fn:map key="location">
            <fn:string key="city"><xsl:value-of select="$status/cs:Location/cs:City"/></fn:string>
            <fn:string key="country"><xsl:value-of select="$status/cs:Location/cs:Country"/></fn:string>
          </fn:map>
        </xsl:if>
        <xsl:if test="$status/cs:SignedBy">
          <fn:string key="signedBy"><xsl:value-of select="$status/cs:SignedBy"/></fn:string>
        </xsl:if>
        <xsl:if test="$status/cs:StatusCode = 'DLV'">
          <fn:boolean key="onTime">
            <xsl:value-of select="xs:date(substring($status/cs:EventTime, 1, 10))
                                  le xs:date($shipment/reg:promisedDeliveryDate)"/>
          </fn:boolean>
        </xsl:if>
      </fn:map>
    </xsl:variable>
    <xsl:value-of select="xml-to-json($json)"/>
  </xsl:template>

  <xsl:function name="cs:canonical-status" as="xs:string">
    <xsl:param name="code" as="xs:string"/>
    <xsl:sequence select="
      if ($code = 'DLV') then 'DELIVERED'
      else if ($code = 'TRN') then 'IN_TRANSIT'
      else if ($code = 'OUT') then 'OUT_FOR_DELIVERY'
      else 'EXCEPTION'"/>
  </xsl:function>
</xsl:stylesheet>
