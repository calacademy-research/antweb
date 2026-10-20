<%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<% if (org.calacademy.antweb.util.HttpUtil.isStaticCallCheck(request, out)) return; %>

<div id="page_contents">
    <h1>World Ants Collections - Copenhagen</h1>
    <div class="clear"></div>
    <div class="page_divider"></div>

    <b>Zoological Museum, University of Copenhagen</b><br>
    (The Natural History Museum of Denmark)<br>
    <b>ZMUC<br>
    Fabricius</b><br>

    <p><a href=wac/zm_copenhagen_lg.jpg target=new><img border=0 src=wac/zm_copenhagen_sm.jpg></a><br><a class=footer_href href=wac/zm_copenhagen_lg.jpg target=new>Click for larger version</a><br>(Photo by Geert Brovad)<br>

    <%-- The larger version is the specimen's profile image in AntWeb's image store (2026-10-19). --%>
    <p><a href=https://static.antweb.org/images/casent0102533/casent0102533_p_1_high.jpg target=new><img border=0 src=wac/CASENT0102533_P.jpg></a><br><a class=footer_href href=https://static.antweb.org/images/casent0102533/casent0102533_p_1_high.jpg target=new>Click for larger version</a>
    (<a class=footer_href href=specimen.do?code=casent0102533>specimen CASENT0102533</a>)<br>

    <p><a href=https://static.antweb.org/images/casent0102533/casent0102533_l_1_high.jpg target=new><img border=0 src=wac/CASENT0102533_L.jpg></a><br><a class=footer_href href=https://static.antweb.org/images/casent0102533/casent0102533_l_1_high.jpg target=new>Click for larger version</a><br>

    <p><a href=wac/Fabricius_lg.jpg target=new><img border=0 src=wac/Fabricius.jpg></a><br><a class=footer_href href=wac/Fabricius_lg.jpg target=new>Click for larger version</a><br>

    <p><a href=wac/ZMUC001_lg.jpg target=new><img border=0 src=wac/ZMUC001.jpg></a><br><a class=footer_href href=wac/ZMUC001_lg.jpg target=new>Click for larger version</a><br>

</div>
