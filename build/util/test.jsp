<%@ page import="org.calacademy.antweb.Taxon" %>

<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.imageUploader.*" %>

<%@ page import="java.util.*" %>

<%@ page import="java.io.*" %>
<%@ page import="org.im4java.core.*" %>
<%@ page import="org.im4java.process.*" %>


<h1>Test.jsp</h1>
<%
/*
As opposed to the 1st version (flash, php, perl) instead of using working directory:
     /var/www/html/imageUpload/toUpload/
We use:
    /data/antweb/web/imageUploader/     
And uploaded it is database driven. Each upload and image are tracked and managed.
  Once generation of derivative files is completed we move to image table.    
*/
%>

<!--
Subfamily:<%= Taxon.getSubfamilyFromName("formicinaecamponotus maculatus") %>
<br>Genus:<%= Taxon.getGenusFromName("formicinaecamponotus maculatus") %>
-->

<%
String origFileName = "CASENT0005904_H.tif";
//String filePath = imgDir + fileName;

String code = "casent0005904";
String shot = "d";
int number = 1;

%>

<!--
Info imageInfo = new Info(filePath,true);
ImageFormat: < %= imageInfo.getImageFormat() % >
-->

<%
/* */
  ImageUpload imageUpload = new ImageUpload();
  imageUpload.setCopyright("California Academy of Sciences, 2000-2019");
  String license = "Attribution-ShareAlike (BY-SA) Creative Commons License and GNU Free Documentation License (GFDL)";
  imageUpload.setLicense(license);
  //imageUpload.setArtist();
  imageUpload.setCreated(new Date());
  String message = imageUpload.genImages(origFileName, code, shot, number);
%>
Tags: <%= message %>
