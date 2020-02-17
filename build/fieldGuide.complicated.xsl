<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
	xmlns:str="http://exslt.org/strings" extension-element-prefixes="str" version="1.0">	
	<xsl:output method="xml" version="1.0" omit-xml-declaration="no" indent="yes"/>
	<!-- ========================= -->
	<!-- root element: fieldguide-->
	<!-- ========================= -->
	<xsl:template match="fieldguide">
		<fo:root xmlns:fo="http://www.w3.org/1999/XSL/Format">

			<fo:layout-master-set>
<!-- (1. Define the page margins) -->
				<fo:simple-page-master master-name="cover" page-height="11in" page-width="8.5in" background-color="#827b00">
					<fo:region-body background-color="#827b00" />
                                </fo:simple-page-master>
				<fo:simple-page-master master-name="simple" page-height="11in" page-width="8.5in" margin-top="0.25in" margin-bottom="0.25in" margin-left="0.25in" margin-right="0.25in">
					<fo:region-before extent="0.5in" />
					<fo:region-body margin-top="0.2in" margin-bottom="0in"/>
				</fo:simple-page-master>
				<fo:simple-page-master master-name="after_simple" page-height="11in" page-width="8.5in" margin-top="0.25in" margin-bottom="0.25in" margin-left="0.25in" margin-right="0.25in">
					<fo:region-before extent="0.5in" />
					<fo:region-body margin-top="0.2in" margin-bottom="0in"/>
				
				</fo:simple-page-master>
			</fo:layout-master-set>
<!-- (2. For the page layout refer to the master layout)-->

<!-- this is the cover page -->
<fo:page-sequence master-reference="cover">
<fo:flow flow-name="xsl-region-body">
<fo:block margin-left="0.46in" space-before="2.0in">
<xsl:variable name="zeething" select="concat('http://maps.google.com/staticmap?center=',extentCenterLat, ',', extentCenterLon, '&amp;zoom=5&amp;size=305x366&amp;maptype=terrain&amp;sensor=false&amp;key=', googleKey)"/>
<fo:external-graphic src="url('http://www.antweb.org/images/fgt.gif')" />
<fo:external-graphic border-before-style="solid" border-before-color="white" border-before-width="10px" vertical-align="middle" src="url('{$zeething}')" />
</fo:block>
<fo:block font-size="21pt" color="white" font-weight="bold" margin-left="0.46in" space-before="5mm"  space-after="5mm">
<xsl:value-of select="title"/>
</fo:block>   
<fo:block font-size="8pt" color="white" space-before="2.0in" margin-left="0.46in">
All images, unless otherwise noted, are copyrighted by the California Academy of Sciences. Copyright 2002 - <xsl:value-of select="year"/> 
</fo:block>
<fo:block font-size="8pt" color="white" margin-left="0.46in">
Use of these images it is subject to our Attribution-NonCommercial-ShareAlike Creative Commons License. Each image should be attributed to <fo:inline color="blue"><fo:basic-link external-destination="http://www.antweb.org">antweb.org</fo:basic-link></fo:inline>
</fo:block>
</fo:flow>
</fo:page-sequence>

<!-- this is for the guide page layout -->
			<fo:page-sequence master-reference="simple">

				<fo:static-content flow-name="xsl-region-before">
					<fo:block font-size="8pt" font-weight="bold" space-after="5mm">
						AntWeb - <xsl:value-of select="title"/>
					</fo:block>   
				</fo:static-content>

				<fo:static-content flow-name="xsl-region-after">

					 <fo:block font-size="8pt" space-after="1mm" display-align="after">
            All images, unless otherwise noted, are copyrighted by the California Academy of Sciences.
            Copyright 2002 - <xsl:value-of select="year"/> Use of these images
            it is subject to our Attribution-NonCommercial-ShareAlike Creative Commons License.
            Each image should be attributed to
            <fo:inline color="blue"><fo:basic-link external-destination="http://www.antweb.org">antweb.org</fo:basic-link></fo:inline>
           </fo:block>

				</fo:static-content>
				
				<fo:flow flow-name="xsl-region-body">
 
<!-- (3. Defining the block with table definition to display data-->

					<fo:block>
						<fo:table table-layout="fixed">
							<fo:table-column column-width="proportional-column-width(50)"/>
							<fo:table-column column-width="proportional-column-width(50)"/>
							<fo:table-body>
								<xsl:apply-templates select="taxon"/>
							</fo:table-body>
						</fo:table>
					</fo:block>
				</fo:flow>
			</fo:page-sequence>
			<fo:page-sequence master-reference="after_simple">

				<fo:static-content flow-name="xsl-region-before">
					<fo:block font-size="9pt" font-weight="bold" space-after="5mm">
						AntWeb - <xsl:value-of select="title"/>
					</fo:block>   
				</fo:static-content>

				<fo:flow flow-name="xsl-region-body">

					<fo:block>
						<fo:table table-layout="fixed">
							<fo:table-column column-width="proportional-column-width(100)"/>
							<fo:table-body>
								<xsl:apply-templates select="locations/location"/>
							</fo:table-body>
						</fo:table>
					</fo:block>
				</fo:flow>
			</fo:page-sequence>
		</fo:root>
	</xsl:template>
 
	<!-- ========================= -->
	<!-- Taxon information   -->
	<!-- ========================= -->
	
	<xsl:template match="taxon">
	 <fo:table-row xmlns:fo="http://www.w3.org/1999/XSL/Format">
<fo:table-cell number-columns-spanned="2">
<fo:table>
<fo:table-column />
<fo:table-column />
<fo:table-column />
<fo:table-body>
<fo:table-row xmlns:fo="http://www.w3.org/1999/XSL/Format" keep-with-next="always">

		<fo:table-cell number-columns-spanned="3">
                    <fo:block font-weight="bold" font-size="10pt" color="blue" text-align="left">
                        <fo:basic-link>
                        <xsl:attribute name="external-destination">
                        <xsl:value-of select="link" disable-output-escaping="yes"/>
                        </xsl:attribute>
                        <xsl:value-of select="name"/>
                        </fo:basic-link>
                    </fo:block>
		</fo:table-cell>
	 </fo:table-row>
	 <fo:table-row xmlns:fo="http://www.w3.org/1999/XSL/Format" keep-with-next="always">
		<fo:table-cell padding="0.05in">
			<fo:block text-align="left" wrap-option="no-wrap">
                            <fo:external-graphic overflow="hidden" height="2.0in">
                                <xsl:attribute name="src">
                                    <xsl:value-of select="headimage"/>
                                </xsl:attribute>
                            </fo:external-graphic>
		</fo:block>
		</fo:table-cell>

		<fo:table-cell padding="0.05in">
			<fo:block text-align="left" wrap-option="no-wrap">
                            <fo:external-graphic height="2.0in">
                                <xsl:attribute name="src">
                                    <xsl:value-of select="profileimage"/>
                                </xsl:attribute>
                            </fo:external-graphic>
                        </fo:block>
		</fo:table-cell>
		<fo:table-cell padding="0.05in">
                    <fo:block>
                        <xsl:variable name="zeething" select="staticMap" />
                            <fo:external-graphic border-before-style="solid" border-before-color="white" border-before-width="10px" vertical-align="middle" src="url('{$zeething}')" />
                    </fo:block>
		</fo:table-cell>
	 </fo:table-row>
	 <fo:table-row xmlns:fo="http://www.w3.org/1999/XSL/Format" keep-with-next="always">
		<fo:table-cell number-columns-spanned="3" padding-bottom="3mm">
<fo:table>
<fo:table-column column-width="proportional-column-width(50)"/>
<fo:table-column column-width="proportional-column-width(50)"/>
<fo:table-body>
	 <fo:table-row>
		<fo:table-cell>
                    <fo:block font-size="9pt">
                        <xsl:value-of select="localityCount"/> Localities:
                    </fo:block>
		</fo:table-cell>
		<fo:table-cell text-align="right">
                    <fo:block font-size="9pt">
                       Photographer: <xsl:value-of select="artist"/>
                    </fo:block>
		</fo:table-cell>
	 </fo:table-row>
	 <fo:table-row>
		<fo:table-cell number-columns-spanned="3">
                    <fo:block border-bottom-width="thin" border-bottom-style="solid" border-bottom-color="black">
                    </fo:block>
		</fo:table-cell>
	 </fo:table-row>
</fo:table-body>
</fo:table>
		</fo:table-cell>
	 </fo:table-row>
</fo:table-body>
</fo:table>
</fo:table-cell>

	 </fo:table-row>
	</xsl:template>	

	<!-- ========================= -->
	<!-- Location information   -->
	<!-- ========================= -->

	<xsl:template match="locations/location">
<fo:table-row xmlns:fo="http://www.w3.org/1999/XSL/Format">
<fo:table-cell padding-bottom="3mm">
            <fo:block font-size="10pt" font-weight="bold">
                <xsl:value-of select="name"/> 
            </fo:block>
						<fo:table table-layout="fixed">
							<fo:table-column column-width="proportional-column-width(100)"/>
							<fo:table-body>
  		<xsl:apply-templates select="localities/locality"/>
							</fo:table-body>
						</fo:table>
                    <fo:block border-bottom-width="thin" border-bottom-style="solid" border-bottom-color="black"></fo:block>
</fo:table-cell>
</fo:table-row>
	</xsl:template>	

	<xsl:template match="localities/locality">
<fo:table-row xmlns:fo="http://www.w3.org/1999/XSL/Format">
<fo:table-cell padding-bottom="2mm">
            <fo:block font-size="9pt">
            <xsl:value-of select="."/>
            </fo:block>
</fo:table-cell>
</fo:table-row>
	</xsl:template>	
	
</xsl:stylesheet>


