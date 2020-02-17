<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.Utility" %>
<%@ page import="java.util.*" %>
<%@ page import="org.calacademy.antweb.home.*" %>

<%@ page language="java" %>
<%@ page errorPage = "error.jsp" %>

<%
  if (org.calacademy.antweb.util.HttpUtil.isStaticCallCheck(request, out)) return;
%>
<div id="page_contents">
<h1>AntWeb's Favorite Images</h1>
<div class="clear"></div>
<div class="page_divider"></div>
</div>
<!-- /div -->
<div id="page_data">
AntWeb's curators' favorite ant images!
    <div class="clear"></div>
    <div class="page_spacer"></div>

<%
ArrayList<LikeObject> likeObjectList = (ArrayList) request.getAttribute("likeObjectList");
int index = 0;
int first = 1;
int fourth = 4;
int position = 0;
int row = 0;
int column = 0;
for (LikeObject likeObject : likeObjectList) {
    ++index;
    ++position;
    if (position == 5) {
        position = 1;
    }
%>

    <div class="slide medium<% if (position == first) { %> first<% } %><% if (position == fourth) { %> last<% } %>" style="background-image:url('<%= AntwebProps.getImgDomainApp() %>/images/<%= likeObject.getCode() %>/<%= likeObject.getCode() %>_<%= likeObject.shot %>_<%= likeObject.number %>_med.jpg');">
        <div class="hover medium" onclick="window.location='<%= AntwebProps.getDomainApp() %>/bigPicture.do?imageId=<%= likeObject.imageId %>';"></div>
        <div class="top_gradient medium"></div> <!-- inside this div tag for a note at the top of the image. -->
        <div class="name"><a href="<%= AntwebProps.getDomainApp() %>/specimen.do?name=<%= likeObject.getCode() %>"><%= Taxon.getLongPrettyTaxonName(likeObject.taxonName) %></a></div>
    </div>
<%  
} %>

            
            

