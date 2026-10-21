<%@ page errorPage = "/error.jsp" %>
<%@ page import="org.calacademy.antweb.Group" %>
<%@ page import="org.calacademy.antweb.util.AntwebUtil" %>
<%@ page import="org.calacademy.antweb.Utility" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<bean:define id="project" value="" toScope="session"/> 

<%@include file="/common/antwebSecureAdmin-defs.jsp" %>

<%@include file="/curate/curatorCheck.jsp" %>
<%
  // The session's "thisLogin" may be shown only to its owner or an administrator.
  org.calacademy.antweb.Login _shownLogin = (org.calacademy.antweb.Login) session.getAttribute("thisLogin");
  org.calacademy.antweb.Login _viewer = org.calacademy.antweb.util.LoginMgr.getAccessLogin(request);
  if (_shownLogin == null || _viewer == null || (!_viewer.isAdmin() && _viewer.getId() != _shownLogin.getId())) {
    session.removeAttribute("thisLogin");
    response.sendRedirect(org.calacademy.antweb.util.AntwebProps.getDomainApp() + "/login.do");
    return;
  }
%>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
    <tiles:put name="title" value="Login - AntWeb" />
    <tiles:put name="body-content" value="/curate/viewLogin-body.jsp" />    
</tiles:insert>
