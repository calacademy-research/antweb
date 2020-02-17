<%@ page language="java" %>
<%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Arrays" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Set" %>
<%@ page import="java.util.Iterator" %>
<%@ page import="org.calacademy.antweb.Group" %>

<%@include file="/curate/adminCheck.jsp" %>

  <h1>Administering Bolton's Catalog</h1>
  <p>
  Here are the current excel files for Bolton's catalog:<br>
  <a href="worldAuthorityFiles/extant.xls">Extant ants</a><br>
  <a href="worldAuthorityFiles/extinct.xls">Extinct ants</a><br>
  </p>
  

  