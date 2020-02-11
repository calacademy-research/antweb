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
				<fo:simple-page-master master-name="simple" page-height="11in" page-width="8.5in" 
margin-top="0.25in" margin-bottom="0.25in" margin-left="0.25in" margin-right="0.25in">
					<fo:region-before extent="0.5in"/>
					<fo:region-body margin-top="0.2in"/>
					<fo:region-after extent="0.25in"/>
				
				</fo:simple-page-master>
			</fo:layout-master-set>
<!-- (2. For the page layout refer to the master layout)-->
			<fo:page-sequence master-reference="simple">

				<fo:static-content flow-name="xsl-region-before">
					<fo:block font-size="16pt" font-weight="bold" space-after="5mm">
						<xsl:value-of select="title"/>
					</fo:block>   
				</fo:static-content>

				<fo:static-content flow-name="xsl-region-after">
					 <fo:block font-size="7pt" space-after="5mm" display-align="after">
            All images, unless otherwise noted, are copyrighted by the California Academy of Sciences.
            Copyright 2002 - <xsl:value-of select="year"/> Use of these images
            it is subject to our Attribution-ShareAlike Creative Commons License.
            Each image should be attributed to
            <fo:inline color="blue"><fo:basic-link external-destination="http://www.antweb.org">antweb.org</fo:basic-link></fo:inline>
           </fo:block>

				</fo:static-content>
				
				<fo:flow flow-name="xsl-region-body">
 
<!-- (3. Defining the block with table definition to display data-->

					<fo:block font-size="10pt">
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
		</fo:root>
	</xsl:template>
 
	<!-- ========================= -->
	<!-- Taxon information   -->
	<!-- ========================= -->
	
	<xsl:template match="taxon">
	 <fo:table-row xmlns:fo="http://www.w3.org/1999/XSL/Format" keep-together="always">
		<fo:table-cell padding="0.1in">
			<fo:block text-align="left" wrap-option="no-wrap">
							<fo:instream-foreign-object text-align="left" width="2mm" height="10mm">
								<svg:svg xmlns:svg="http://www.w3.org/2000/svg">
									<svg:text width="0.5in" x="0" y="0" transform="rotate(90)" style="font-size:6pt"><xsl:value-of select="artist"/></svg:text>
								</svg:svg>
							</fo:instream-foreign-object>
				  			<fo:external-graphic overflow="hidden" height="2.0in">
									<xsl:attribute name="src">
										<xsl:value-of select="headimage"/>
									</xsl:attribute>
								</fo:external-graphic>
								<fo:block text-align="left" color="white">
									<xsl:value-of select="name"/>
								</fo:block>
		</fo:block>
		</fo:table-cell>

		<fo:table-cell padding="0.1in">
			<fo:block text-align="left" wrap-option="no-wrap">
<!--
				 <fo:instream-foreign-object width="3in" height="2in">
                <svg:svg width="3in" height="2in" xmlns:svg="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink">
                  <svg:image x="0" y="0" width="100%" height="100%">
										<xsl:attribute name="xlink:href">
											<xsl:value-of select="profileimage"/>
										</xsl:attribute>
									</svg:image>
                </svg:svg>
         </fo:instream-foreign-object>
-->
				  			<fo:external-graphic height="2.0in">
									<xsl:attribute name="src">
										<xsl:value-of select="profileimage"/>
									</xsl:attribute>
								</fo:external-graphic>

								 <fo:block color="blue" text-align="left">
									<fo:basic-link>
					                                        <xsl:attribute name="external-destination">
                                       						         <xsl:value-of select="link" disable-output-escaping="yes"/>
                                      						 </xsl:attribute>

										<xsl:value-of select="name"/>
									</fo:basic-link>
								 </fo:block>
								</fo:block>
		</fo:table-cell>
	 </fo:table-row>
	</xsl:template>	
	
</xsl:stylesheet>


