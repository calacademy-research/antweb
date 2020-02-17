<%@ page errorPage = "/error.jsp" %>
<%@ page import = "org.calacademy.antweb.SpecimenImage" %>

<%@ page import = "org.calacademy.antweb.util.*" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<%
  if (org.calacademy.antweb.util.HttpUtil.isStaticCallCheck(request, out)) return;

        if ((session == null) || (request == null)) {
          // AntwebUtil.log("null stuff");
          return;
        }

/* If this is here, it breaks the oneView.jsp getComparison function.  No page found
   when dorsal is selected, for instance.  If not here, we do not know the negative
   repercussions.  A more proper/difficult fix would be to change oneView.jsp call to
   be oneView.do and have all of the necessary parameters passed in (name, genus, etc..).
  */
//        session.removeAttribute("taxon");

        String code = (String) request.getAttribute("code");
        String shot = (String) request.getAttribute("shot");

        if (code == null) code = request.getParameter("name");
        code = code.toUpperCase();

        SpecimenImage spec = new SpecimenImage();
        spec.setShot(shot);
        //spec.setShot(request.getParameter("shot"));
        String shotString = spec.getShotText();    
        
        Object theImage = session.getAttribute("theImage");
        if ((theImage == null) 
        ) {
          AntwebUtil.log("info", "bigPicture.jsp Session error.  TheImage:" + theImage + " code:" + code);
		// String redirectURL = AntwebProps.getDomainApp() + "/bigPicture.do?" + request.getQueryString();
 		// response.sendRedirect(redirectURL);          
          return;  // Could we return an error page here, please?
        }
   
        String metaString = "<meta name='keywords' content='Specimen " + code + ", " + shotString + " ,AntWeb, ants,ant,formicidae '/>";
        metaString+= "<meta name='description' content='Closeup " + shotString + " view of Specimen " + code + " from AntWeb.'/>";

%>

<%@include file="common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
  <tiles:put name="title" type="string">
    Closeup of the <%= shotString %> of Specimen <%= code %>
  </tiles:put>
        <tiles:put name="meta" value="<%= metaString %>" />
	<tiles:put name="body-content" value="/bigPicture-body.jsp" />	
</tiles:insert>
