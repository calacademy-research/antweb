<%@ page language="java" %>
<%@ page errorPage = "/error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<%@ page import="org.calacademy.antweb.Formatter" %>
<%@ page import="org.calacademy.antweb.SpecimenImage" %>
<%@ page import="org.calacademy.antweb.Utility" %>
<%@ page import="org.calacademy.antweb.Rank" %>
<%@ page import="org.calacademy.antweb.util.*" %>

<% 
    if (org.calacademy.antweb.util.HttpUtil.isStaticCallCheck(request, out)) return;

    Utility util = new Utility(); 
    String imgDomainApp = AntwebProps.getImgDomainApp();
%>


<jsp:useBean id="taxon" scope="session" class="org.calacademy.antweb.Taxon" />
<jsp:setProperty name="taxon" property="*" />

<logic:present parameter="project">
  <logic:notEqual parameter="project" value=""> 
	<bean:parameter id="tempProject" name="project" />
	<bean:define id="project" name="tempProject" toScope="session" />
  </logic:notEqual>
</logic:present>

<logic:notPresent name="project">
  <bean:define id = "project" value=""/>
</logic:notPresent>

<jsp:useBean id="project" scope="session" type="java.lang.String" />

<html>
<head>
<title>Image Picker Thumbnails</title>
</head>
<body>
<logic:iterate id="thisChild" collection="<%= taxon.getChildren() %>" type="org.calacademy.antweb.Taxon">
	<logic:iterate id="theImage" name="thisChild" collection="<%= thisChild.getImages() %>">
<% 
String code = "";
String key = "p";
if (Rank.SPECIES.equals(taxon.getRank()) || Rank.SUBSPECIES.equals(taxon.getRank())) {
    code = thisChild.getName().toUpperCase();
} else {
    SpecimenImage specimenImage = (SpecimenImage) thisChild.getImages().get(key);
    if (specimenImage != null) {
      code = specimenImage.getCode().toUpperCase();
    } else {
       // Some ants don't have a "p" image.  Don't break.
      //AntwebUtil.log("imagePickImages.jsp specimenImage:null child:" + thisChild + " key:" + key + " imagesSize:" + thisChild.getImages().size());
    }
}

//if (AntwebProps.isDevMode()) AntwebUtil.log("imagePickImages.jsp code:" + code);

%>
<a href="#" onclick="parent.get_img_name('<%= code %>','image_<%= taxon.getTaxonName() %>'); return false;"> <!-- taxon.getFullName() -->
<img id=specimen_img_show src="<%= imgDomainApp %><bean:write name="theImage" property="value.lowres" />" border="0"></a>
<!-- was getFullName() -->
	</logic:iterate>
</logic:iterate>

</body>
</html>
