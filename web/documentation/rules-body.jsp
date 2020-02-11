<%@ page language="java" %>
<%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
                                                                               
<br>
<div id="page_contents">
  <h1>Image Upload Filename Rules</h1>
  <div class="clear"></div>
  <div class="page_divider"></div>
</div>

<div id="page_data">
  <div id="overview_data">

<ul>
  <li>Image Upload filenames should be in the format: specimenCode_shotType[_shotNumber].fileExtension
  <ul>
    <li>Shot type can be: l (label), p (profile), h (head) or d (dorsal).
    <li>Shot number is optional.
    <li>file extension can be tif or jpg.
    <li>Specimen codes should not include underscores or parenthesis.
    <li>The only special characters permitted are dashes "-".
  </ul>
  <li>Filenames may either be uppercase or lowercase, but can not be of mixed case.
</ul>
<br>
  </div>
</div>
