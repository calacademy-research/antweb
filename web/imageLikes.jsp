<%-- Tiles wrapper of imageLikes-body.jsp, the curators' favourite images (title "AntWeb Curator's Favorite
     Images"): forward likes of /list.do?action=likes (ListAction; also /favs.do). --%>
<%@include file="common/antweb-defs.jsp" %>

<%
  String titleString =  "AntWeb Curator's Favorite Images";
%>  
  
<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="<%=titleString%>" />
	<tiles:put name="body-content" value="/imageLikes-body.jsp" />	
</tiles:insert>

