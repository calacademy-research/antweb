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
  Note, there are two kinds of files: those which represent species information, of which there may be many,
  and one file linking genera to their subfamilies.  This latter file, once it's uploaded will be renamed 
  subfamily_genus.txt.
  </p>
  <p>
  All uploaded files must be Microsoft Word documents which have been saved as HTML.  The 
  The system currently understands the HTML output by whatever version of Word Brian is using.
  </p>
  <html:form method="POST" action="upload.do" enctype="multipart/form-data">
  <p>
  Upload a catalog file 
  <html:select property="outputFileName">
  	<html:option value="subfamily_genus.txt">Subfamily/Genus master file</html:option>
  	<html:option value="synopsis.txt">Subfamily/Genus Synopsis</html:option>
  	<html:option value="">Species file</html:option>
  </html:select>
    
  <html:hidden property="homePageDirectory" value="worldAuthorityFiles"/>
  <html:hidden property="successKey" value="worldAuthorityFiles"/>
  <html:file property="theFile2" />
  </p>
  <html:submit />
  </html:form>
  
  <p><a href="getWorldAuthority.do?type=extant">download extant authority file</a></p>
  <p><a href="getWorldAuthority.do?type=extinct">download extinct authority file</a></p>
  <h2><a href="worldAuthorityFiles" target="listFrame">Currently existing catalog files</a></h2>
  <iframe name="listFrame" height="500" width="100%" src="worldAuthorityFiles/"></iframe>
  


  