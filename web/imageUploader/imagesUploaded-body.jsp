<%@ page import = "java.util.*"%>


<%@ page import = "org.calacademy.antweb.*" %>
<%@ page import = "org.calacademy.antweb.util.*" %>

<div class="left">

<br>
<< Back to <a href="<%= AntwebProps.getDomainApp() %>/imageUploader.do">Image Uploader</a> 

<br><br>
    <div class="admin_layout" style="background:#fff;">
        <div class="admin_action_item">
            <div style="float:left;"><h1>AntWeb Images Uploaded</h1></div>
            <div class="clear"></div>
        </div>
    </div>

<br>

<% 
   ImageUpload imageUpload = (ImageUpload) request.getAttribute("imageUploaded");
   out.println(imageUpload.toString());
%>
<br><br>
<%

   String message = (String) request.getAttribute("message"); 
   if (! (message == null)) {
     out.println(message);
   }
%>

</div>