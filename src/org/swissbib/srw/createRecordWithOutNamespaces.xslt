<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:mx="http://www.loc.gov/MARC21/slim"
                version="2.0">

    <!-- remove mx namespace from record content -->

    <xsl:output
            omit-xml-declaration="yes"
            />

    <xsl:template match="*">
        <xsl:element name="{local-name()}">
            <xsl:copy-of select="@*"/>
            <xsl:apply-templates/>
        </xsl:element>
    </xsl:template>


</xsl:stylesheet>