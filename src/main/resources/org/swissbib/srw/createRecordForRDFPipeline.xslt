<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:mx="http://www.loc.gov/MARC21/slim"
                version="2.0"
                xmlns="http://www.loc.gov/MARC21/slim"
                >
    <!--
    Metafacture handle-marcxml command needs the Marc-default namespace on the record level in order to accept the
    record as correct Marc-record.
    Generally, single Marc  records are wrapped into a collection tag which already contains the standard namespace
    which is not the case for regularly updates where records are sent as single messages.
    -->



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


    <xsl:template match="/mx:record">
        <xsl:element name="{local-name()}" >
            <!--
            GH: we need the type Bibliographic attribute for the Metafacture handle-marcxml command
            -->
            <xsl:attribute name="type">
                <xsl:text>Bibliographic</xsl:text>
            </xsl:attribute>
            

            <xsl:apply-templates/>
        </xsl:element>
    </xsl:template>




</xsl:stylesheet>