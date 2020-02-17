<%@ page errorPage = "/error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
<%@ page import="org.calacademy.antweb.Login" %>
<%@ page import="org.calacademy.antweb.Utility" %>
<%@ page import="org.calacademy.antweb.AncFile" %>
<%@ page import="org.calacademy.antweb.util.*" %>

<% 
   if (!org.calacademy.antweb.util.HttpUtil.isInWhiteListCheck(request.getQueryString(), response)) return;  
   String domainApp = (new Utility()).getDomainApp(); %>

<%@include file="/common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="Nonestablished Ants of the Netherlands" />	<tiles:put name="body-content" type="string">

    <div id="page_contents">
           <h1>Nonestablished Ants of the Netherlands</h1>
      <div class="clear"></div>

      <div class="page_divider"></div>

    </div>

    <div id="page_data">
      <div id="overview_data">
       
           <meta http-equiv="Content-Type" content="text/html; charset=utf-8"><meta name="ProgId" content="Word.Document"><meta name="Generator" content="Microsoft Word 12"><meta name="Originator" content="Microsoft Word 12"><link rel="File-List" href="file:///C:%5CDOCUME%7E1%5Cbfisher%5CLOCALS%7E1%5CTemp%5Cmsohtmlclip1%5C01%5Cclip_filelist.xml"><link rel="themeData" href="file:///C:%5CDOCUME%7E1%5Cbfisher%5CLOCALS%7E1%5CTemp%5Cmsohtmlclip1%5C01%5Cclip_themedata.thmx"><link rel="colorSchemeMapping" href="file:///C:%5CDOCUME%7E1%5Cbfisher%5CLOCALS%7E1%5CTemp%5Cmsohtmlclip1%5C01%5Cclip_colorschememapping.xml"><!--[if gte mso 9]><xml>
 <w:WordDocument>
 <w:View>Normal</w:View>
 <w:Zoom>0</w:Zoom>
 <w:TrackMoves/>
 <w:TrackFormatting/>
 <w:PunctuationKerning/>
 <w:ValidateAgainstSchemas/>
 <w:SaveIfXMLInvalid>false</w:SaveIfXMLInvalid>
 <w:IgnoreMixedContent>false</w:IgnoreMixedContent>
 <w:AlwaysShowPlaceholderText>false</w:AlwaysShowPlaceholderText>
 <w:DoNotPromoteQF/>
 <w:LidThemeOther>EN-US</w:LidThemeOther>
 <w:LidThemeAsian>X-NONE</w:LidThemeAsian>
 <w:LidThemeComplexScript>X-NONE</w:LidThemeComplexScript>
 <w:Compatibility>
  <w:BreakWrappedTables/>
  <w:SnapToGridInCell/>
  <w:WrapTextWithPunct/>
  <w:UseAsianBreakRules/>
  <w:DontGrowAutofit/>
  <w:SplitPgBreakAndParaMark/>
  <w:DontVertAlignCellWithSp/>
  <w:DontBreakConstrainedForcedTables/>
  <w:DontVertAlignInTxbx/>
  <w:Word11KerningPairs/>
  <w:CachedColBalance/>
 </w:Compatibility>
 <w:BrowserLevel>MicrosoftInternetExplorer4</w:BrowserLevel>
 <m:mathPr>
  <m:mathFont m:val="Cambria Math"/>
  <m:brkBin m:val="before"/>
  <m:brkBinSub m:val="--"/>
  <m:smallFrac m:val="off"/>
  <m:dispDef/>
  <m:lMargin m:val="0"/>
  <m:rMargin m:val="0"/>
  <m:defJc m:val="centerGroup"/>
  <m:wrapIndent m:val="1440"/>
  <m:intLim m:val="subSup"/>
  <m:naryLim m:val="undOvr"/>
 </m:mathPr></w:WordDocument>
</xml><![endif]--><!--[if gte mso 9]><xml>
 <w:LatentStyles DefLockedState="false" DefUnhideWhenUsed="true"
 DefSemiHidden="true" DefQFormat="false" DefPriority="99"
 LatentStyleCount="267">
 <w:LsdException Locked="false" Priority="0" SemiHidden="false"
  UnhideWhenUsed="false" QFormat="true" Name="Normal"/>
 <w:LsdException Locked="false" Priority="9" SemiHidden="false"
  UnhideWhenUsed="false" QFormat="true" Name="heading 1"/>
 <w:LsdException Locked="false" Priority="9" QFormat="true" Name="heading 2"/>
 <w:LsdException Locked="false" Priority="9" QFormat="true" Name="heading 3"/>
 <w:LsdException Locked="false" Priority="9" QFormat="true" Name="heading 4"/>
 <w:LsdException Locked="false" Priority="9" QFormat="true" Name="heading 5"/>
 <w:LsdException Locked="false" Priority="9" QFormat="true" Name="heading 6"/>
 <w:LsdException Locked="false" Priority="9" QFormat="true" Name="heading 7"/>
 <w:LsdException Locked="false" Priority="9" QFormat="true" Name="heading 8"/>
 <w:LsdException Locked="false" Priority="9" QFormat="true" Name="heading 9"/>
 <w:LsdException Locked="false" Priority="39" Name="toc 1"/>
 <w:LsdException Locked="false" Priority="39" Name="toc 2"/>
 <w:LsdException Locked="false" Priority="39" Name="toc 3"/>
 <w:LsdException Locked="false" Priority="39" Name="toc 4"/>
 <w:LsdException Locked="false" Priority="39" Name="toc 5"/>
 <w:LsdException Locked="false" Priority="39" Name="toc 6"/>
 <w:LsdException Locked="false" Priority="39" Name="toc 7"/>
 <w:LsdException Locked="false" Priority="39" Name="toc 8"/>
 <w:LsdException Locked="false" Priority="39" Name="toc 9"/>
 <w:LsdException Locked="false" Priority="35" QFormat="true" Name="caption"/>
 <w:LsdException Locked="false" Priority="10" SemiHidden="false"
  UnhideWhenUsed="false" QFormat="true" Name="Title"/>
 <w:LsdException Locked="false" Priority="1" Name="Default Paragraph Font"/>
 <w:LsdException Locked="false" Priority="11" SemiHidden="false"
  UnhideWhenUsed="false" QFormat="true" Name="Subtitle"/>
 <w:LsdException Locked="false" Priority="22" SemiHidden="false"
  UnhideWhenUsed="false" QFormat="true" Name="Strong"/>
 <w:LsdException Locked="false" Priority="20" SemiHidden="false"
  UnhideWhenUsed="false" QFormat="true" Name="Emphasis"/>
 <w:LsdException Locked="false" Priority="59" SemiHidden="false"
  UnhideWhenUsed="false" Name="Table Grid"/>
 <w:LsdException Locked="false" UnhideWhenUsed="false" Name="Placeholder Text"/>
 <w:LsdException Locked="false" Priority="1" SemiHidden="false"
  UnhideWhenUsed="false" QFormat="true" Name="No Spacing"/>
 <w:LsdException Locked="false" Priority="60" SemiHidden="false"
  UnhideWhenUsed="false" Name="Light Shading"/>
 <w:LsdException Locked="false" Priority="61" SemiHidden="false"
  UnhideWhenUsed="false" Name="Light List"/>
 <w:LsdException Locked="false" Priority="62" SemiHidden="false"
  UnhideWhenUsed="false" Name="Light Grid"/>
 <w:LsdException Locked="false" Priority="63" SemiHidden="false"
  UnhideWhenUsed="false" Name="Medium Shading 1"/>
 <w:LsdException Locked="false" Priority="64" SemiHidden="false"
  UnhideWhenUsed="false" Name="Medium Shading 2"/>
 <w:LsdException Locked="false" Priority="65" SemiHidden="false"
  UnhideWhenUsed="false" Name="Medium List 1"/>
 <w:LsdException Locked="false" Priority="66" SemiHidden="false"
  UnhideWhenUsed="false" Name="Medium List 2"/>
 <w:LsdException Locked="false" Priority="67" SemiHidden="false"
  UnhideWhenUsed="false" Name="Medium Grid 1"/>
 <w:LsdException Locked="false" Priority="68" SemiHidden="false"
  UnhideWhenUsed="false" Name="Medium Grid 2"/>
 <w:LsdException Locked="false" Priority="69" SemiHidden="false"
  UnhideWhenUsed="false" Name="Medium Grid 3"/>
 <w:LsdException Locked="false" Priority="70" SemiHidden="false"
  UnhideWhenUsed="false" Name="Dark List"/>
 <w:LsdException Locked="false" Priority="71" SemiHidden="false"
  UnhideWhenUsed="false" Name="Colorful Shading"/>
 <w:LsdException Locked="false" Priority="72" SemiHidden="false"
  UnhideWhenUsed="false" Name="Colorful List"/>
 <w:LsdException Locked="false" Priority="73" SemiHidden="false"
  UnhideWhenUsed="false" Name="Colorful Grid"/>
 <w:LsdException Locked="false" Priority="60" SemiHidden="false"
  UnhideWhenUsed="false" Name="Light Shading Accent 1"/>
 <w:LsdException Locked="false" Priority="61" SemiHidden="false"
  UnhideWhenUsed="false" Name="Light List Accent 1"/>
 <w:LsdException Locked="false" Priority="62" SemiHidden="false"
  UnhideWhenUsed="false" Name="Light Grid Accent 1"/>
 <w:LsdException Locked="false" Priority="63" SemiHidden="false"
  UnhideWhenUsed="false" Name="Medium Shading 1 Accent 1"/>
 <w:LsdException Locked="false" Priority="64" SemiHidden="false"
  UnhideWhenUsed="false" Name="Medium Shading 2 Accent 1"/>
 <w:LsdException Locked="false" Priority="65" SemiHidden="false"
  UnhideWhenUsed="false" Name="Medium List 1 Accent 1"/>
 <w:LsdException Locked="false" UnhideWhenUsed="false" Name="Revision"/>
 <w:LsdException Locked="false" Priority="34" SemiHidden="false"
  UnhideWhenUsed="false" QFormat="true" Name="List Paragraph"/>
 <w:LsdException Locked="false" Priority="29" SemiHidden="false"
  UnhideWhenUsed="false" QFormat="true" Name="Quote"/>
 <w:LsdException Locked="false" Priority="30" SemiHidden="false"
  UnhideWhenUsed="false" QFormat="true" Name="Intense Quote"/>
 <w:LsdException Locked="false" Priority="66" SemiHidden="false"
  UnhideWhenUsed="false" Name="Medium List 2 Accent 1"/>
 <w:LsdException Locked="false" Priority="67" SemiHidden="false"
  UnhideWhenUsed="false" Name="Medium Grid 1 Accent 1"/>
 <w:LsdException Locked="false" Priority="68" SemiHidden="false"
  UnhideWhenUsed="false" Name="Medium Grid 2 Accent 1"/>
 <w:LsdException Locked="false" Priority="69" SemiHidden="false"
  UnhideWhenUsed="false" Name="Medium Grid 3 Accent 1"/>
 <w:LsdException Locked="false" Priority="70" SemiHidden="false"
  UnhideWhenUsed="false" Name="Dark List Accent 1"/>
 <w:LsdException Locked="false" Priority="71" SemiHidden="false"
  UnhideWhenUsed="false" Name="Colorful Shading Accent 1"/>
 <w:LsdException Locked="false" Priority="72" SemiHidden="false"
  UnhideWhenUsed="false" Name="Colorful List Accent 1"/>
 <w:LsdException Locked="false" Priority="73" SemiHidden="false"
  UnhideWhenUsed="false" Name="Colorful Grid Accent 1"/>
 <w:LsdException Locked="false" Priority="60" SemiHidden="false"
  UnhideWhenUsed="false" Name="Light Shading Accent 2"/>
 <w:LsdException Locked="false" Priority="61" SemiHidden="false"
  UnhideWhenUsed="false" Name="Light List Accent 2"/>
 <w:LsdException Locked="false" Priority="62" SemiHidden="false"
  UnhideWhenUsed="false" Name="Light Grid Accent 2"/>
 <w:LsdException Locked="false" Priority="63" SemiHidden="false"
  UnhideWhenUsed="false" Name="Medium Shading 1 Accent 2"/>
 <w:LsdException Locked="false" Priority="64" SemiHidden="false"
  UnhideWhenUsed="false" Name="Medium Shading 2 Accent 2"/>
 <w:LsdException Locked="false" Priority="65" SemiHidden="false"
  UnhideWhenUsed="false" Name="Medium List 1 Accent 2"/>
 <w:LsdException Locked="false" Priority="66" SemiHidden="false"
  UnhideWhenUsed="false" Name="Medium List 2 Accent 2"/>
 <w:LsdException Locked="false" Priority="67" SemiHidden="false"
  UnhideWhenUsed="false" Name="Medium Grid 1 Accent 2"/>
 <w:LsdException Locked="false" Priority="68" SemiHidden="false"
  UnhideWhenUsed="false" Name="Medium Grid 2 Accent 2"/>
 <w:LsdException Locked="false" Priority="69" SemiHidden="false"
  UnhideWhenUsed="false" Name="Medium Grid 3 Accent 2"/>
 <w:LsdException Locked="false" Priority="70" SemiHidden="false"
  UnhideWhenUsed="false" Name="Dark List Accent 2"/>
 <w:LsdException Locked="false" Priority="71" SemiHidden="false"
  UnhideWhenUsed="false" Name="Colorful Shading Accent 2"/>
 <w:LsdException Locked="false" Priority="72" SemiHidden="false"
  UnhideWhenUsed="false" Name="Colorful List Accent 2"/>
 <w:LsdException Locked="false" Priority="73" SemiHidden="false"
  UnhideWhenUsed="false" Name="Colorful Grid Accent 2"/>
 <w:LsdException Locked="false" Priority="60" SemiHidden="false"
  UnhideWhenUsed="false" Name="Light Shading Accent 3"/>
 <w:LsdException Locked="false" Priority="61" SemiHidden="false"
  UnhideWhenUsed="false" Name="Light List Accent 3"/>
 <w:LsdException Locked="false" Priority="62" SemiHidden="false"
  UnhideWhenUsed="false" Name="Light Grid Accent 3"/>
 <w:LsdException Locked="false" Priority="63" SemiHidden="false"
  UnhideWhenUsed="false" Name="Medium Shading 1 Accent 3"/>
 <w:LsdException Locked="false" Priority="64" SemiHidden="false"
  UnhideWhenUsed="false" Name="Medium Shading 2 Accent 3"/>
 <w:LsdException Locked="false" Priority="65" SemiHidden="false"
  UnhideWhenUsed="false" Name="Medium List 1 Accent 3"/>
 <w:LsdException Locked="false" Priority="66" SemiHidden="false"
  UnhideWhenUsed="false" Name="Medium List 2 Accent 3"/>
 <w:LsdException Locked="false" Priority="67" SemiHidden="false"
  UnhideWhenUsed="false" Name="Medium Grid 1 Accent 3"/>
 <w:LsdException Locked="false" Priority="68" SemiHidden="false"
  UnhideWhenUsed="false" Name="Medium Grid 2 Accent 3"/>
 <w:LsdException Locked="false" Priority="69" SemiHidden="false"
  UnhideWhenUsed="false" Name="Medium Grid 3 Accent 3"/>
 <w:LsdException Locked="false" Priority="70" SemiHidden="false"
  UnhideWhenUsed="false" Name="Dark List Accent 3"/>
 <w:LsdException Locked="false" Priority="71" SemiHidden="false"
  UnhideWhenUsed="false" Name="Colorful Shading Accent 3"/>
 <w:LsdException Locked="false" Priority="72" SemiHidden="false"
  UnhideWhenUsed="false" Name="Colorful List Accent 3"/>
 <w:LsdException Locked="false" Priority="73" SemiHidden="false"
  UnhideWhenUsed="false" Name="Colorful Grid Accent 3"/>
 <w:LsdException Locked="false" Priority="60" SemiHidden="false"
  UnhideWhenUsed="false" Name="Light Shading Accent 4"/>
 <w:LsdException Locked="false" Priority="61" SemiHidden="false"
  UnhideWhenUsed="false" Name="Light List Accent 4"/>
 <w:LsdException Locked="false" Priority="62" SemiHidden="false"
  UnhideWhenUsed="false" Name="Light Grid Accent 4"/>
 <w:LsdException Locked="false" Priority="63" SemiHidden="false"
  UnhideWhenUsed="false" Name="Medium Shading 1 Accent 4"/>
 <w:LsdException Locked="false" Priority="64" SemiHidden="false"
  UnhideWhenUsed="false" Name="Medium Shading 2 Accent 4"/>
 <w:LsdException Locked="false" Priority="65" SemiHidden="false"
  UnhideWhenUsed="false" Name="Medium List 1 Accent 4"/>
 <w:LsdException Locked="false" Priority="66" SemiHidden="false"
  UnhideWhenUsed="false" Name="Medium List 2 Accent 4"/>
 <w:LsdException Locked="false" Priority="67" SemiHidden="false"
  UnhideWhenUsed="false" Name="Medium Grid 1 Accent 4"/>
 <w:LsdException Locked="false" Priority="68" SemiHidden="false"
  UnhideWhenUsed="false" Name="Medium Grid 2 Accent 4"/>
 <w:LsdException Locked="false" Priority="69" SemiHidden="false"
  UnhideWhenUsed="false" Name="Medium Grid 3 Accent 4"/>
 <w:LsdException Locked="false" Priority="70" SemiHidden="false"
  UnhideWhenUsed="false" Name="Dark List Accent 4"/>
 <w:LsdException Locked="false" Priority="71" SemiHidden="false"
  UnhideWhenUsed="false" Name="Colorful Shading Accent 4"/>
 <w:LsdException Locked="false" Priority="72" SemiHidden="false"
  UnhideWhenUsed="false" Name="Colorful List Accent 4"/>
 <w:LsdException Locked="false" Priority="73" SemiHidden="false"
  UnhideWhenUsed="false" Name="Colorful Grid Accent 4"/>
 <w:LsdException Locked="false" Priority="60" SemiHidden="false"
  UnhideWhenUsed="false" Name="Light Shading Accent 5"/>
 <w:LsdException Locked="false" Priority="61" SemiHidden="false"
  UnhideWhenUsed="false" Name="Light List Accent 5"/>
 <w:LsdException Locked="false" Priority="62" SemiHidden="false"
  UnhideWhenUsed="false" Name="Light Grid Accent 5"/>
 <w:LsdException Locked="false" Priority="63" SemiHidden="false"
  UnhideWhenUsed="false" Name="Medium Shading 1 Accent 5"/>
 <w:LsdException Locked="false" Priority="64" SemiHidden="false"
  UnhideWhenUsed="false" Name="Medium Shading 2 Accent 5"/>
 <w:LsdException Locked="false" Priority="65" SemiHidden="false"
  UnhideWhenUsed="false" Name="Medium List 1 Accent 5"/>
 <w:LsdException Locked="false" Priority="66" SemiHidden="false"
  UnhideWhenUsed="false" Name="Medium List 2 Accent 5"/>
 <w:LsdException Locked="false" Priority="67" SemiHidden="false"
  UnhideWhenUsed="false" Name="Medium Grid 1 Accent 5"/>
 <w:LsdException Locked="false" Priority="68" SemiHidden="false"
  UnhideWhenUsed="false" Name="Medium Grid 2 Accent 5"/>
 <w:LsdException Locked="false" Priority="69" SemiHidden="false"
  UnhideWhenUsed="false" Name="Medium Grid 3 Accent 5"/>
 <w:LsdException Locked="false" Priority="70" SemiHidden="false"
  UnhideWhenUsed="false" Name="Dark List Accent 5"/>
 <w:LsdException Locked="false" Priority="71" SemiHidden="false"
  UnhideWhenUsed="false" Name="Colorful Shading Accent 5"/>
 <w:LsdException Locked="false" Priority="72" SemiHidden="false"
  UnhideWhenUsed="false" Name="Colorful List Accent 5"/>
 <w:LsdException Locked="false" Priority="73" SemiHidden="false"
  UnhideWhenUsed="false" Name="Colorful Grid Accent 5"/>
 <w:LsdException Locked="false" Priority="60" SemiHidden="false"
  UnhideWhenUsed="false" Name="Light Shading Accent 6"/>
 <w:LsdException Locked="false" Priority="61" SemiHidden="false"
  UnhideWhenUsed="false" Name="Light List Accent 6"/>
 <w:LsdException Locked="false" Priority="62" SemiHidden="false"
  UnhideWhenUsed="false" Name="Light Grid Accent 6"/>
 <w:LsdException Locked="false" Priority="63" SemiHidden="false"
  UnhideWhenUsed="false" Name="Medium Shading 1 Accent 6"/>
 <w:LsdException Locked="false" Priority="64" SemiHidden="false"
  UnhideWhenUsed="false" Name="Medium Shading 2 Accent 6"/>
 <w:LsdException Locked="false" Priority="65" SemiHidden="false"
  UnhideWhenUsed="false" Name="Medium List 1 Accent 6"/>
 <w:LsdException Locked="false" Priority="66" SemiHidden="false"
  UnhideWhenUsed="false" Name="Medium List 2 Accent 6"/>
 <w:LsdException Locked="false" Priority="67" SemiHidden="false"
  UnhideWhenUsed="false" Name="Medium Grid 1 Accent 6"/>
 <w:LsdException Locked="false" Priority="68" SemiHidden="false"
  UnhideWhenUsed="false" Name="Medium Grid 2 Accent 6"/>
 <w:LsdException Locked="false" Priority="69" SemiHidden="false"
  UnhideWhenUsed="false" Name="Medium Grid 3 Accent 6"/>
 <w:LsdException Locked="false" Priority="70" SemiHidden="false"
  UnhideWhenUsed="false" Name="Dark List Accent 6"/>
 <w:LsdException Locked="false" Priority="71" SemiHidden="false"
  UnhideWhenUsed="false" Name="Colorful Shading Accent 6"/>
 <w:LsdException Locked="false" Priority="72" SemiHidden="false"
  UnhideWhenUsed="false" Name="Colorful List Accent 6"/>
 <w:LsdException Locked="false" Priority="73" SemiHidden="false"
  UnhideWhenUsed="false" Name="Colorful Grid Accent 6"/>
 <w:LsdException Locked="false" Priority="19" SemiHidden="false"
  UnhideWhenUsed="false" QFormat="true" Name="Subtle Emphasis"/>
 <w:LsdException Locked="false" Priority="21" SemiHidden="false"
  UnhideWhenUsed="false" QFormat="true" Name="Intense Emphasis"/>
 <w:LsdException Locked="false" Priority="31" SemiHidden="false"
  UnhideWhenUsed="false" QFormat="true" Name="Subtle Reference"/>
 <w:LsdException Locked="false" Priority="32" SemiHidden="false"
  UnhideWhenUsed="false" QFormat="true" Name="Intense Reference"/>
 <w:LsdException Locked="false" Priority="33" SemiHidden="false"
  UnhideWhenUsed="false" QFormat="true" Name="Book Title"/>
 <w:LsdException Locked="false" Priority="37" Name="Bibliography"/>
 <w:LsdException Locked="false" Priority="39" QFormat="true" Name="TOC Heading"/>
 </w:LatentStyles>
</xml><![endif]--><style>
<!--
 /* Font Definitions */
 @font-face
	{font-family:"Cambria Math";
	panose-1:2 4 5 3 5 4 6 3 2 4;
	mso-font-charset:0;
	mso-generic-font-family:roman;
	mso-font-pitch:variable;
	mso-font-signature:-1610611985 1107304683 0 0 159 0;}
@font-face
	{font-family:"Palatino Linotype";
	panose-1:2 4 5 2 5 5 5 3 3 4;
	mso-font-charset:0;
	mso-generic-font-family:roman;
	mso-font-pitch:variable;
	mso-font-signature:-536870009 1073741843 0 0 415 0;}
 /* Style Definitions */
 p.MsoNormal, li.MsoNormal, div.MsoNormal
	{mso-style-unhide:no;
	mso-style-qformat:yes;
	mso-style-parent:";
	margin:0in;
	margin-bottom:.0001pt;
	mso-pagination:widow-orphan;
	font-size:12.0pt;
	font-family:"Palatino Linotype","serif";
	mso-fareast-font-family:"Times New Roman";
	mso-bidi-font-family:"Times New Roman";
	mso-fareast-language:EN-AU;}
.MsoChpDefault
	{mso-style-type:export-only;
	mso-default-props:yes;
	mso-ascii-font-family:Calibri;
	mso-ascii-theme-font:minor-latin;
	mso-hansi-font-family:Calibri;
	mso-hansi-theme-font:minor-latin;
	mso-bidi-font-family:"Times New Roman";
	mso-bidi-theme-font:minor-bidi;
	mso-bidi-language:EN-US;}
@page Section1
	{size:8.5in 11.0in;
	margin:1.0in 1.0in 1.0in 1.0in;
	mso-header-margin:.5in;
	mso-footer-margin:.5in;
	mso-paper-source:0;}
div.Section1
	{page:Section1;}
-->
</style><!--[if gte mso 10]>
<style>
 /* Style Definitions */
 table.MsoNormalTable
	{mso-style-name:"Table Normal";
	mso-tstyle-rowband-size:0;
	mso-tstyle-colband-size:0;
	mso-style-noshow:yes;
	mso-style-priority:99;
	mso-style-qformat:yes;
	mso-style-parent:";
	mso-padding-alt:0in 5.4pt 0in 5.4pt;
	mso-para-margin:0in;
	mso-para-margin-bottom:.0001pt;
	mso-pagination:widow-orphan;
	font-size:11.0pt;
	font-family:"Calibri","sans-serif";
	mso-ascii-font-family:Calibri;
	mso-ascii-theme-font:minor-latin;
	mso-fareast-font-family:"Times New Roman";
	mso-fareast-theme-font:minor-fareast;
	mso-hansi-font-family:Calibri;
	mso-hansi-theme-font:minor-latin;}
table.MsoTableGrid
	{mso-style-name:"Table Grid";
	mso-tstyle-rowband-size:0;
	mso-tstyle-colband-size:0;
	mso-style-priority:59;
	mso-style-unhide:no;
	border:solid black 1.0pt;
	mso-border-themecolor:text1;
	mso-border-alt:solid black .5pt;
	mso-border-themecolor:text1;
	mso-padding-alt:0in 5.4pt 0in 5.4pt;
	mso-border-insideh:.5pt solid black;
	mso-border-insideh-themecolor:text1;
	mso-border-insidev:.5pt solid black;
	mso-border-insidev-themecolor:text1;
	mso-para-margin:0in;
	mso-para-margin-bottom:.0001pt;
	mso-pagination:widow-orphan;
	font-size:11.0pt;
	font-family:"Calibri","sans-serif";
	mso-ascii-font-family:Calibri;
	mso-ascii-theme-font:minor-latin;
	mso-hansi-font-family:Calibri;
	mso-hansi-theme-font:minor-latin;
	mso-bidi-language:EN-US;}
</style>
<![endif]-->

<table class="MsoTableGrid" style="border: medium none ; border-collapse: collapse;" border="1" cellpadding="0" cellspacing="0">
 <tbody><tr style="height: 12.75pt;">
 <td style="border: 1pt solid black; padding: 0in 5.4pt; width: 229.9pt; height: 12.75pt;" valign="top" width="307" nowrap="nowrap">
 <p class="MsoNormal"><strong><span style="font-size: 10pt;">Species<o:p></o:p></span></strong></p>
 </td>
 <td style="border-style: solid solid solid none; border-color: black black black -moz-use-text-color; border-width: 1pt 1pt 1pt medium; padding: 0in 5.4pt; width: 213.3pt; height: 12.75pt;" valign="top" width="284" nowrap="nowrap">
 <p class="MsoNormal"><strong><span style="font-size: 10pt;">Distribution<o:p></o:p></span></strong></p>
 </td>
 </tr>
 <tr style="height: 12.75pt;">
 <td style="border-style: none solid solid; border-color: -moz-use-text-color black black; border-width: medium 1pt 1pt; padding: 0in 5.4pt; width: 229.9pt; height: 12.75pt;" valign="top" width="307" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">Technomyrmex pallipes<span>  </span>Emery 1893<o:p></o:p></span></p>
 </td>
 <td style="border-style: none solid solid none; border-color: -moz-use-text-color black black -moz-use-text-color; border-width: medium 1pt 1pt medium; padding: 0in 5.4pt; width: 213.3pt; height: 12.75pt;" valign="top" width="284" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">once imported; not
 established<o:p></o:p></span></p>
 </td>
 </tr>
 <tr style="height: 12.75pt;">
 <td style="border-style: none solid solid; border-color: -moz-use-text-color black black; border-width: medium 1pt 1pt; padding: 0in 5.4pt; width: 229.9pt; height: 12.75pt;" valign="top" width="307" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">Gnamptogenys striatula Mayr
 1884<o:p></o:p></span></p>
 </td>
 <td style="border-style: none solid solid none; border-color: -moz-use-text-color black black -moz-use-text-color; border-width: medium 1pt 1pt medium; padding: 0in 5.4pt; width: 213.3pt; height: 12.75pt;" valign="top" width="284" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">once imported; not
 established<o:p></o:p></span></p>
 </td>
 </tr>
 <tr style="height: 12.75pt;">
 <td style="border-style: none solid solid; border-color: -moz-use-text-color black black; border-width: medium 1pt 1pt; padding: 0in 5.4pt; width: 229.9pt; height: 12.75pt;" valign="top" width="307" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">Rhytidoponera NL01 <o:p></o:p></span></p>
 </td>
 <td style="border-style: none solid solid none; border-color: -moz-use-text-color black black -moz-use-text-color; border-width: medium 1pt 1pt medium; padding: 0in 5.4pt; width: 213.3pt; height: 12.75pt;" valign="top" width="284" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">once imported; not
 established<o:p></o:p></span></p>
 </td>
 </tr>
 <tr style="height: 12.75pt;">
 <td style="border-style: none solid solid; border-color: -moz-use-text-color black black; border-width: medium 1pt 1pt; padding: 0in 5.4pt; width: 229.9pt; height: 12.75pt;" valign="top" width="307" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">Camponotus herculeanus
 (Linnaeus 1758)<o:p></o:p></span></p>
 </td>
 <td style="border-style: none solid solid none; border-color: -moz-use-text-color black black -moz-use-text-color; border-width: medium 1pt 1pt medium; padding: 0in 5.4pt; width: 213.3pt; height: 12.75pt;" valign="top" width="284" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">once imported; not
 established<o:p></o:p></span></p>
 </td>
 </tr>
 <tr style="height: 12.75pt;">
 <td style="border-style: none solid solid; border-color: -moz-use-text-color black black; border-width: medium 1pt 1pt; padding: 0in 5.4pt; width: 229.9pt; height: 12.75pt;" valign="top" width="307" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">Camponotus pennsylvanicus
 (De Geer 1773)<o:p></o:p></span></p>
 </td>
 <td style="border-style: none solid solid none; border-color: -moz-use-text-color black black -moz-use-text-color; border-width: medium 1pt 1pt medium; padding: 0in 5.4pt; width: 213.3pt; height: 12.75pt;" valign="top" width="284" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">a few times imported; not
 established<o:p></o:p></span></p>
 </td>
 </tr>
 <tr style="height: 12.75pt;">
 <td style="border-style: none solid solid; border-color: -moz-use-text-color black black; border-width: medium 1pt 1pt; padding: 0in 5.4pt; width: 229.9pt; height: 12.75pt;" valign="top" width="307" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">Camponotus fastigatus Roger
 1863<o:p></o:p></span></p>
 </td>
 <td style="border-style: none solid solid none; border-color: -moz-use-text-color black black -moz-use-text-color; border-width: medium 1pt 1pt medium; padding: 0in 5.4pt; width: 213.3pt; height: 12.75pt;" valign="top" width="284" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">once imported; not
 established<o:p></o:p></span></p>
 </td>
 </tr>
 <tr style="height: 12.75pt;">
 <td style="border-style: none solid solid; border-color: -moz-use-text-color black black; border-width: medium 1pt 1pt; padding: 0in 5.4pt; width: 229.9pt; height: 12.75pt;" valign="top" width="307" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">Camponotus platanus Roger
 1863<o:p></o:p></span></p>
 </td>
 <td style="border-style: none solid solid none; border-color: -moz-use-text-color black black -moz-use-text-color; border-width: medium 1pt 1pt medium; padding: 0in 5.4pt; width: 213.3pt; height: 12.75pt;" valign="top" width="284" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">a few times imported; not
 established<o:p></o:p></span></p>
 </td>
 </tr>
 <tr style="height: 12.75pt;">
 <td style="border-style: none solid solid; border-color: -moz-use-text-color black black; border-width: medium 1pt 1pt; padding: 0in 5.4pt; width: 229.9pt; height: 12.75pt;" valign="top" width="307" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">Camponotus atriceps Smith
 1858<o:p></o:p></span></p>
 </td>
 <td style="border-style: none solid solid none; border-color: -moz-use-text-color black black -moz-use-text-color; border-width: medium 1pt 1pt medium; padding: 0in 5.4pt; width: 213.3pt; height: 12.75pt;" valign="top" width="284" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">a few times imported; not
 established<o:p></o:p></span></p>
 </td>
 </tr>
 <tr style="height: 12.75pt;">
 <td style="border-style: none solid solid; border-color: -moz-use-text-color black black; border-width: medium 1pt 1pt; padding: 0in 5.4pt; width: 229.9pt; height: 12.75pt;" valign="top" width="307" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">Camponotus mitis Smith 1858<o:p></o:p></span></p>
 </td>
 <td style="border-style: none solid solid none; border-color: -moz-use-text-color black black -moz-use-text-color; border-width: medium 1pt 1pt medium; padding: 0in 5.4pt; width: 213.3pt; height: 12.75pt;" valign="top" width="284" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">once imported; not
 established<o:p></o:p></span></p>
 </td>
 </tr>
 <tr style="height: 12.75pt;">
 <td style="border-style: none solid solid; border-color: -moz-use-text-color black black; border-width: medium 1pt 1pt; padding: 0in 5.4pt; width: 229.9pt; height: 12.75pt;" valign="top" width="307" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">Camponotus aegyptiacus
 Emery 1915<o:p></o:p></span></p>
 </td>
 <td style="border-style: none solid solid none; border-color: -moz-use-text-color black black -moz-use-text-color; border-width: medium 1pt 1pt medium; padding: 0in 5.4pt; width: 213.3pt; height: 12.75pt;" valign="top" width="284" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">once imported; not
 established<o:p></o:p></span></p>
 </td>
 </tr>
 <tr style="height: 12.75pt;">
 <td style="border-style: none solid solid; border-color: -moz-use-text-color black black; border-width: medium 1pt 1pt; padding: 0in 5.4pt; width: 229.9pt; height: 12.75pt;" valign="top" width="307" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">Camponotus bugnioni Forel
 1899<o:p></o:p></span></p>
 </td>
 <td style="border-style: none solid solid none; border-color: -moz-use-text-color black black -moz-use-text-color; border-width: medium 1pt 1pt medium; padding: 0in 5.4pt; width: 213.3pt; height: 12.75pt;" valign="top" width="284" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">a few times imported; not
 established<o:p></o:p></span></p>
 </td>
 </tr>
 <tr style="height: 12.75pt;">
 <td style="border-style: none solid solid; border-color: -moz-use-text-color black black; border-width: medium 1pt 1pt; padding: 0in 5.4pt; width: 229.9pt; height: 12.75pt;" valign="top" width="307" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">Lasius lasioides Emery 1869<o:p></o:p></span></p>
 </td>
 <td style="border-style: none solid solid none; border-color: -moz-use-text-color black black -moz-use-text-color; border-width: medium 1pt 1pt medium; padding: 0in 5.4pt; width: 213.3pt; height: 12.75pt;" valign="top" width="284" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">once imported; not
 established<o:p></o:p></span></p>
 </td>
 </tr>
 <tr style="height: 12.75pt;">
 <td style="border-style: none solid solid; border-color: -moz-use-text-color black black; border-width: medium 1pt 1pt; padding: 0in 5.4pt; width: 229.9pt; height: 12.75pt;" valign="top" width="307" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">Oecophylla longinoda
 (Latreille 1802)<o:p></o:p></span></p>
 </td>
 <td style="border-style: none solid solid none; border-color: -moz-use-text-color black black -moz-use-text-color; border-width: medium 1pt 1pt medium; padding: 0in 5.4pt; width: 213.3pt; height: 12.75pt;" valign="top" width="284" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">a few times imported; not
 established<o:p></o:p></span></p>
 </td>
 </tr>
 <tr style="height: 12.75pt;">
 <td style="border-style: none solid solid; border-color: -moz-use-text-color black black; border-width: medium 1pt 1pt; padding: 0in 5.4pt; width: 229.9pt; height: 12.75pt;" valign="top" width="307" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">Oecophylla smaragdina
 (Fabricius 1775)<o:p></o:p></span></p>
 </td>
 <td style="border-style: none solid solid none; border-color: -moz-use-text-color black black -moz-use-text-color; border-width: medium 1pt 1pt medium; padding: 0in 5.4pt; width: 213.3pt; height: 12.75pt;" valign="top" width="284" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">a few times imported; not
 established<o:p></o:p></span></p>
 </td>
 </tr>
 <tr style="height: 12.75pt;">
 <td style="border-style: none solid solid; border-color: -moz-use-text-color black black; border-width: medium 1pt 1pt; padding: 0in 5.4pt; width: 229.9pt; height: 12.75pt;" valign="top" width="307" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">Anoplolepis gracilipes
 (Smith 1857)<o:p></o:p></span></p>
 </td>
 <td style="border-style: none solid solid none; border-color: -moz-use-text-color black black -moz-use-text-color; border-width: medium 1pt 1pt medium; padding: 0in 5.4pt; width: 213.3pt; height: 12.75pt;" valign="top" width="284" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">once imported; not
 established; no records during past 50 years<o:p></o:p></span></p>
 </td>
 </tr>
 <tr style="height: 12.75pt;">
 <td style="border-style: none solid solid; border-color: -moz-use-text-color black black; border-width: medium 1pt 1pt; padding: 0in 5.4pt; width: 229.9pt; height: 12.75pt;" valign="top" width="307" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">Lepisiota obtusa (Emery
 1901)<o:p></o:p></span></p>
 </td>
 <td style="border-style: none solid solid none; border-color: -moz-use-text-color black black -moz-use-text-color; border-width: medium 1pt 1pt medium; padding: 0in 5.4pt; width: 213.3pt; height: 12.75pt;" valign="top" width="284" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">once a temporary indoor
 establishment<o:p></o:p></span></p>
 </td>
 </tr>
 <tr style="height: 12.75pt;">
 <td style="border-style: none solid solid; border-color: -moz-use-text-color black black; border-width: medium 1pt 1pt; padding: 0in 5.4pt; width: 229.9pt; height: 12.75pt;" valign="top" width="307" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">Prenolepis nitens Mayr 1853<o:p></o:p></span></p>
 </td>
 <td style="border-style: none solid solid none; border-color: -moz-use-text-color black black -moz-use-text-color; border-width: medium 1pt 1pt medium; padding: 0in 5.4pt; width: 213.3pt; height: 12.75pt;" valign="top" width="284" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">once imported; not
 established; no records during past 50 years<o:p></o:p></span></p>
 </td>
 </tr>
 <tr style="height: 12.75pt;">
 <td style="border-style: none solid solid; border-color: -moz-use-text-color black black; border-width: medium 1pt 1pt; padding: 0in 5.4pt; width: 229.9pt; height: 12.75pt;" valign="top" width="307" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">Acromyrmex coronatus
 (Fabricius 1804)<o:p></o:p></span></p>
 </td>
 <td style="border-style: none solid solid none; border-color: -moz-use-text-color black black -moz-use-text-color; border-width: medium 1pt 1pt medium; padding: 0in 5.4pt; width: 213.3pt; height: 12.75pt;" valign="top" width="284" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">once imported; not
 established; no records during past 50 years<o:p></o:p></span></p>
 </td>
 </tr>
 <tr style="height: 12.75pt;">
 <td style="border-style: none solid solid; border-color: -moz-use-text-color black black; border-width: medium 1pt 1pt; padding: 0in 5.4pt; width: 229.9pt; height: 12.75pt;" valign="top" width="307" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">Acromyrmex lundi
 (Guerin-Meneville 1838)<o:p></o:p></span></p>
 </td>
 <td style="border-style: none solid solid none; border-color: -moz-use-text-color black black -moz-use-text-color; border-width: medium 1pt 1pt medium; padding: 0in 5.4pt; width: 213.3pt; height: 12.75pt;" valign="top" width="284" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">once imported; not
 established<o:p></o:p></span></p>
 </td>
 </tr>
 <tr style="height: 12.75pt;">
 <td style="border-style: none solid solid; border-color: -moz-use-text-color black black; border-width: medium 1pt 1pt; padding: 0in 5.4pt; width: 229.9pt; height: 12.75pt;" valign="top" width="307" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">Wasmannia auropunctata
 (Roger 1863)<o:p></o:p></span></p>
 </td>
 <td style="border-style: none solid solid none; border-color: -moz-use-text-color black black -moz-use-text-color; border-width: medium 1pt 1pt medium; padding: 0in 5.4pt; width: 213.3pt; height: 12.75pt;" valign="top" width="284" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">a few times imported; not
 established<o:p></o:p></span></p>
 </td>
 </tr>
 <tr style="height: 12.75pt;">
 <td style="border-style: none solid solid; border-color: -moz-use-text-color black black; border-width: medium 1pt 1pt; padding: 0in 5.4pt; width: 229.9pt; height: 12.75pt;" valign="top" width="307" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">Cephalotes curvistriatus
 (Forel 1899)<o:p></o:p></span></p>
 </td>
 <td style="border-style: none solid solid none; border-color: -moz-use-text-color black black -moz-use-text-color; border-width: medium 1pt 1pt medium; padding: 0in 5.4pt; width: 213.3pt; height: 12.75pt;" valign="top" width="284" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">once imported; not
 established<o:p></o:p></span></p>
 </td>
 </tr>
 <tr style="height: 12.75pt;">
 <td style="border-style: none solid solid; border-color: -moz-use-text-color black black; border-width: medium 1pt 1pt; padding: 0in 5.4pt; width: 229.9pt; height: 12.75pt;" valign="top" width="307" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">Crematogaster algirica
 (Lucas 1849)<o:p></o:p></span></p>
 </td>
 <td style="border-style: none solid solid none; border-color: -moz-use-text-color black black -moz-use-text-color; border-width: medium 1pt 1pt medium; padding: 0in 5.4pt; width: 213.3pt; height: 12.75pt;" valign="top" width="284" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">a few times imported; not
 established; no records during past 50 years<o:p></o:p></span></p>
 </td>
 </tr>
 <tr style="height: 12.75pt;">
 <td style="border-style: none solid solid; border-color: -moz-use-text-color black black; border-width: medium 1pt 1pt; padding: 0in 5.4pt; width: 229.9pt; height: 12.75pt;" valign="top" width="307" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">Crematogaster sordidula
 (Nylander 1849)<o:p></o:p></span></p>
 </td>
 <td style="border-style: none solid solid none; border-color: -moz-use-text-color black black -moz-use-text-color; border-width: medium 1pt 1pt medium; padding: 0in 5.4pt; width: 213.3pt; height: 12.75pt;" valign="top" width="284" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">a few times imported; not
 established; no records during past 50 years<o:p></o:p></span></p>
 </td>
 </tr>
 <tr style="height: 12.75pt;">
 <td style="border-style: none solid solid; border-color: -moz-use-text-color black black; border-width: medium 1pt 1pt; padding: 0in 5.4pt; width: 229.9pt; height: 12.75pt;" valign="top" width="307" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">Strumigenys minutula
 Terayama & Kubota 1989<o:p></o:p></span></p>
 </td>
 <td style="border-style: none solid solid none; border-color: -moz-use-text-color black black -moz-use-text-color; border-width: medium 1pt 1pt medium; padding: 0in 5.4pt; width: 213.3pt; height: 12.75pt;" valign="top" width="284" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">once imported; not
 established; no records during past 50 years<o:p></o:p></span></p>
 </td>
 </tr>
 <tr style="height: 12.75pt;">
 <td style="border-style: none solid solid; border-color: -moz-use-text-color black black; border-width: medium 1pt 1pt; padding: 0in 5.4pt; width: 229.9pt; height: 12.75pt;" valign="top" width="307" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">Cardiocondyla nuda (Mayr
 1866)<o:p></o:p></span></p>
 </td>
 <td style="border-style: none solid solid none; border-color: -moz-use-text-color black black -moz-use-text-color; border-width: medium 1pt 1pt medium; padding: 0in 5.4pt; width: 213.3pt; height: 12.75pt;" valign="top" width="284" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">once imported; not
 established<o:p></o:p></span></p>
 </td>
 </tr>
 <tr style="height: 12.75pt;">
 <td style="border-style: none solid solid; border-color: -moz-use-text-color black black; border-width: medium 1pt 1pt; padding: 0in 5.4pt; width: 229.9pt; height: 12.75pt;" valign="top" width="307" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">Cardiocondyla wroughtonii
 (Forel 1890)<o:p></o:p></span></p>
 </td>
 <td style="border-style: none solid solid none; border-color: -moz-use-text-color black black -moz-use-text-color; border-width: medium 1pt 1pt medium; padding: 0in 5.4pt; width: 213.3pt; height: 12.75pt;" valign="top" width="284" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">a few times imported; not
 established<o:p></o:p></span></p>
 </td>
 </tr>
 <tr style="height: 12.75pt;">
 <td style="border-style: none solid solid; border-color: -moz-use-text-color black black; border-width: medium 1pt 1pt; padding: 0in 5.4pt; width: 229.9pt; height: 12.75pt;" valign="top" width="307" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">Temnothorax subditivus
 (Wheeler 1903)<o:p></o:p></span></p>
 </td>
 <td style="border-style: none solid solid none; border-color: -moz-use-text-color black black -moz-use-text-color; border-width: medium 1pt 1pt medium; padding: 0in 5.4pt; width: 213.3pt; height: 12.75pt;" valign="top" width="284" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">once imported; not
 established<o:p></o:p></span></p>
 </td>
 </tr>
 <tr style="height: 12.75pt;">
 <td style="border-style: none solid solid; border-color: -moz-use-text-color black black; border-width: medium 1pt 1pt; padding: 0in 5.4pt; width: 229.9pt; height: 12.75pt;" valign="top" width="307" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">Pheidole dossena Wilson
 2003<o:p></o:p></span></p>
 </td>
 <td style="border-style: none solid solid none; border-color: -moz-use-text-color black black -moz-use-text-color; border-width: medium 1pt 1pt medium; padding: 0in 5.4pt; width: 213.3pt; height: 12.75pt;" valign="top" width="284" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">once imported; not
 established<o:p></o:p></span></p>
 </td>
 </tr>
 <tr style="height: 12.75pt;">
 <td style="border-style: none solid solid; border-color: -moz-use-text-color black black; border-width: medium 1pt 1pt; padding: 0in 5.4pt; width: 229.9pt; height: 12.75pt;" valign="top" width="307" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">Pheidole laticornis Wilson
 2003<o:p></o:p></span></p>
 </td>
 <td style="border-style: none solid solid none; border-color: -moz-use-text-color black black -moz-use-text-color; border-width: medium 1pt 1pt medium; padding: 0in 5.4pt; width: 213.3pt; height: 12.75pt;" valign="top" width="284" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">once imported; not
 established<o:p></o:p></span></p>
 </td>
 </tr>
 <tr style="height: 12.75pt;">
 <td style="border-style: none solid solid; border-color: -moz-use-text-color black black; border-width: medium 1pt 1pt; padding: 0in 5.4pt; width: 229.9pt; height: 12.75pt;" valign="top" width="307" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">Pheidole susannae Forel
 1886<o:p></o:p></span></p>
 </td>
 <td style="border-style: none solid solid none; border-color: -moz-use-text-color black black -moz-use-text-color; border-width: medium 1pt 1pt medium; padding: 0in 5.4pt; width: 213.3pt; height: 12.75pt;" valign="top" width="284" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">a few times imported; not
 established<o:p></o:p></span></p>
 </td>
 </tr>
 <tr style="height: 12.75pt;">
 <td style="border-style: none solid solid; border-color: -moz-use-text-color black black; border-width: medium 1pt 1pt; padding: 0in 5.4pt; width: 229.9pt; height: 12.75pt;" valign="top" width="307" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">Monomorium latinode Mayr
 1872<o:p></o:p></span></p>
 </td>
 <td style="border-style: none solid solid none; border-color: -moz-use-text-color black black -moz-use-text-color; border-width: medium 1pt 1pt medium; padding: 0in 5.4pt; width: 213.3pt; height: 12.75pt;" valign="top" width="284" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">once imported; not
 established<o:p></o:p></span></p>
 </td>
 </tr>
 <tr style="height: 12.75pt;">
 <td style="border-style: none solid solid; border-color: -moz-use-text-color black black; border-width: medium 1pt 1pt; padding: 0in 5.4pt; width: 229.9pt; height: 12.75pt;" valign="top" width="307" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">Monomorium salomonis
 (Linnaeus 1758)<o:p></o:p></span></p>
 </td>
 <td style="border-style: none solid solid none; border-color: -moz-use-text-color black black -moz-use-text-color; border-width: medium 1pt 1pt medium; padding: 0in 5.4pt; width: 213.3pt; height: 12.75pt;" valign="top" width="284" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">once imported; not
 established<o:p></o:p></span></p>
 </td>
 </tr>
 <tr style="height: 12.75pt;">
 <td style="border-style: none solid solid; border-color: -moz-use-text-color black black; border-width: medium 1pt 1pt; padding: 0in 5.4pt; width: 229.9pt; height: 12.75pt;" valign="top" width="307" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">Solenopsis gayi (Spinola
 1851)<o:p></o:p></span></p>
 </td>
 <td style="border-style: none solid solid none; border-color: -moz-use-text-color black black -moz-use-text-color; border-width: medium 1pt 1pt medium; padding: 0in 5.4pt; width: 213.3pt; height: 12.75pt;" valign="top" width="284" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">once imported; not
 established<o:p></o:p></span></p>
 </td>
 </tr>
 <tr style="height: 12.75pt;">
 <td style="border-style: none solid solid; border-color: -moz-use-text-color black black; border-width: medium 1pt 1pt; padding: 0in 5.4pt; width: 229.9pt; height: 12.75pt;" valign="top" width="307" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">Solenopsis NL01 <o:p></o:p></span></p>
 </td>
 <td style="border-style: none solid solid none; border-color: -moz-use-text-color black black -moz-use-text-color; border-width: medium 1pt 1pt medium; padding: 0in 5.4pt; width: 213.3pt; height: 12.75pt;" valign="top" width="284" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">once imported; not
 established<o:p></o:p></span></p>
 </td>
 </tr>
 <tr style="height: 12.75pt;">
 <td style="border-style: none solid solid; border-color: -moz-use-text-color black black; border-width: medium 1pt 1pt; padding: 0in 5.4pt; width: 229.9pt; height: 12.75pt;" valign="top" width="307" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">Hypoponera eduardi (Forel
 1894)<o:p></o:p></span></p>
 </td>
 <td style="border-style: none solid solid none; border-color: -moz-use-text-color black black -moz-use-text-color; border-width: medium 1pt 1pt medium; padding: 0in 5.4pt; width: 213.3pt; height: 12.75pt;" valign="top" width="284" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">once imported; not
 established<o:p></o:p></span></p>
 </td>
 </tr>
 <tr style="height: 12.75pt;">
 <td style="border-style: none solid solid; border-color: -moz-use-text-color black black; border-width: medium 1pt 1pt; padding: 0in 5.4pt; width: 229.9pt; height: 12.75pt;" valign="top" width="307" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">Odontomachus brunneus
 (Patton 1894)<o:p></o:p></span></p>
 </td>
 <td style="border-style: none solid solid none; border-color: -moz-use-text-color black black -moz-use-text-color; border-width: medium 1pt 1pt medium; padding: 0in 5.4pt; width: 213.3pt; height: 12.75pt;" valign="top" width="284" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">once imported; not
 established<o:p></o:p></span></p>
 </td>
 </tr>
 <tr style="height: 12.75pt;">
 <td style="border-style: none solid solid; border-color: -moz-use-text-color black black; border-width: medium 1pt 1pt; padding: 0in 5.4pt; width: 229.9pt; height: 12.75pt;" valign="top" width="307" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">Odontomachus haematodes
 (Linnaeus 1758)<o:p></o:p></span></p>
 </td>
 <td style="border-style: none solid solid none; border-color: -moz-use-text-color black black -moz-use-text-color; border-width: medium 1pt 1pt medium; padding: 0in 5.4pt; width: 213.3pt; height: 12.75pt;" valign="top" width="284" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">a few times imported; not
 established; no records during past 50 years<o:p></o:p></span></p>
 </td>
 </tr>
 <tr style="height: 12.75pt;">
 <td style="border-style: none solid solid; border-color: -moz-use-text-color black black; border-width: medium 1pt 1pt; padding: 0in 5.4pt; width: 229.9pt; height: 12.75pt;" valign="top" width="307" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">Odontomachus monticola
 Emery 1892<o:p></o:p></span></p>
 </td>
 <td style="border-style: none solid solid none; border-color: -moz-use-text-color black black -moz-use-text-color; border-width: medium 1pt 1pt medium; padding: 0in 5.4pt; width: 213.3pt; height: 12.75pt;" valign="top" width="284" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">a few times imported; not
 established<o:p></o:p></span></p>
 </td>
 </tr>
 <tr style="height: 12.75pt;">
 <td style="border-style: none solid solid; border-color: -moz-use-text-color black black; border-width: medium 1pt 1pt; padding: 0in 5.4pt; width: 229.9pt; height: 12.75pt;" valign="top" width="307" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">Pachycondyla unidentata
 Mayr 1862<o:p></o:p></span></p>
 </td>
 <td style="border-style: none solid solid none; border-color: -moz-use-text-color black black -moz-use-text-color; border-width: medium 1pt 1pt medium; padding: 0in 5.4pt; width: 213.3pt; height: 12.75pt;" valign="top" width="284" nowrap="nowrap">
 <p class="MsoNormal"><span style="font-size: 10pt;">once imported; not
 established<o:p></o:p></span></p>
 </td>
 </tr>
</tbody></table>

<p class="MsoNormal"><span style="font-size: 10pt;"><o:p> </o:p></span></p>






           <%
            AncFile ancFile = (AncFile) session.getAttribute("ancFile");	   
        
            Login accessLogin = LoginMgr.getAccessLogin(request);

            if (accessLogin != null) {
              String requestURL = request.getRequestURL().toString();
              String accessIdStr = "/" + (new Integer(accessLogin.getId())).toString() + "/";
              if ( (accessLogin.isAdmin())
                || (accessLogin.getProjectNames().contains("netherlandsants"))                   || (requestURL.contains(accessIdStr))
                || (requestURL.contains("curators"))	            
                 ) {
           %>
           <form method="POST" action="<%= domainApp %>/ancPageEdit.do?id=5" />                <input type="submit" value="Edit Page">
            </form>
             <% if (!(session.getAttribute("ancFile") == null)) { %>
            <form method="POST" action="<%= domainApp %>/ancPageSave.do"> 
                <input type="submit" value="Save Page">
            </form>
             <% } %>
           <% } %>	   	
         <% } %>
        
      </div>
    </div>
	</tiles:put>
</tiles:insert>
