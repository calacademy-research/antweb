<%@ page errorPage = "/error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
<%@ page import="java.util.*" %>
<%@ page import="org.calacademy.antweb.Group" %>
<%@ page import="org.calacademy.antweb.Utility" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.*" %>

<% String domainApp = AntwebProps.getDomainApp(); %>
<%@include file="/common/antweb-defs.jsp" %>

<% if (org.calacademy.antweb.util.HttpUtil.isStaticCallCheck(request, out)) return; %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
<tiles:put name="title" value="World Ants Collections" />
<tiles:put name="body-content" type="string">

<div id="page_contents">	   
    <h1>World Ants Collection</h1>
    <div class="clear"></div>
    <div class="page_divider"></div>
</div>

<div id="page_data">

    We at AntWeb have been busy taking photos of many of the world's great ant collections. Have a look! 

    <p>
    <h2><a href="<%= domainApp %>/geneva.do">Museum of Natural History, Geneva</a></h2>
        (Mus&eacute;e d&#39;histoire naturelle de la Ville de Gen&egrave;ve)<br />
    <a href="/geneva.jsp"><img border="0" src="<%= domainApp %>/wac/MHNG033.jpg" /></a><br />
        <strong>MHNG<br />
        Forel Collection</strong>

    <p>
    <p>
    <h2><a href="<%= domainApp %>/genoa.do">Natural History Museum, Genoa</a></h2>
        (Natural History Museum Giacomo Doria)<br />
        <a href="/genoa.jsp"><img border="0" src="<%= domainApp %>/wac/MSNG054.jpg" /></a><br />
        <strong>MSNG<br />
        Emery Collection</strong><br />

    <p>
    <p>
    <h2><a href="<%= domainApp %>/basel.do">Natural History Museum, Basel</a></h2>
        (Naturhistorisches Museum Basel)<br />
        <a href="/basel.jsp"><img border="0" src="<%= domainApp %>/wac/NHMB-021.jpg" /></a><br />
        <strong>NHMB<br />
        Santschi Collection</strong><br />

    <p>
    <p>
    <h2><a href="<%= domainApp %>/oxford.do">Oxford University Museum of Natural History</a></h2>
        <a href="/oxford.jsp"><img border="0" src="<%= domainApp %>/wac/OUMNH-TYPE-1138-1207.jpg" /></a><br />
        <strong>OUMNH&nbsp;<br />
        Hope, Crawley &amp; Bingham collections</strong><br />

    <p>
    <p>
    <h2><a href="<%= domainApp %>/berlin.do">Museum f&uuml;r Naturkunde der Humboldt-Universit&auml;t Berlin</a></h2>
    Humboldt University of Berlin (Natural History Museum of Berlin)<br />
        <a href="/berlin.jsp"><img border="0" src="<%= domainApp %>/wac/ZMHB156-4.jpg" /></a><br />
        <strong>ZMHB<br />
        Roger</strong><br />

    <p>
    <p>
    <h2><a href="<%= domainApp %>/copenhagen.do">Zoological Museum, University of Copenhagen</a></h2>
        (The Natural History Museum of Denmark)<br />
        <a href="/copenhagen.jsp"><img border="0" src="<%= domainApp %>/wac/ZMUC001.jpg" /></a><br />
        <strong>ZMUC<br />
        Fabricius</strong>

</div>	

</tiles:put>
</tiles:insert>
