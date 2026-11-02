<%-- Body of basel.jsp (basel.do): the Natural History Museum Basel (NHMB), Santschi collection, with photos of the
     museum, type drawers and specimens from web/wac, each linked to its larger version. A direct request of
     basel.jsp prints "Invalid URL.  Direct JSP calls unsupported." here (HttpUtil.isStaticCallCheck). Tests:
     tests/e2e/test_world_ant_collections.py. --%><%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<% if (org.calacademy.antweb.util.HttpUtil.isStaticCallCheck(request, out)) return; %>

<div id="page_contents">
    <h1>World Ants Collections - Basel</h1>
    <div class="clear"></div>
    <div class="page_divider"></div>

    <b>Natural History Museum, Basel</b><br>
    (Naturhistorisches Museum Basel)<br>
    <b>NHMB<br>
    Santschi Collection</b><br>


    <p><a href=wac/Museo-Basilea-basel.jpg target=new><img border=0 src=wac/Museo-Basilea-basel.jpg></a><br><a class=footer_href href=wac/Museo-Basilea-basel.jpg target=new>Click for larger version</a><br>

    <p><a href=wac/CASENT0101134_L_lg.jpg target=new><img border=0 src=wac/CASENT0101134_L.jpg></a><br><a class=footer_href href=wac/CASENT0101134_L_lg.jpg target=new>Click for larger version</a><br>

    <p><a href=wac/CASENT0101547_L_lg.jpg target=new><img border=0 src=wac/CASENT0101547_L.jpg></a><br><a class=footer_href href=wac/CASENT0101547_L_lg.jpg target=new>Click for larger version</a><br>

    <p><a href=wac/NHMB-021_lg.jpg target=new><img border=0 src=wac/NHMB-021.jpg></a><br><a class=footer_href href=wac/NHMB-021_lg.jpg target=new>Click for larger version</a><br>

</div>
