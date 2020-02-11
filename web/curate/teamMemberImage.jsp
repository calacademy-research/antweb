<% TeamMember teamMemberImg = (TeamMember) request.getAttribute("teamMember"); %>

  <table><tr><td>
    <img src="<%= domainApp %>/teamMemberImgDownload.do?id=<%= teamMemberImg.getId() %>" width="217" align="left"> <!-- was width:117 -->
  </td><td>      
	<form 
	   name="imgUploadForm" 
	   method="post" 
	   action="<%=domainApp%>/teamMemberImgUpload.do?id=<%= teamMemberImg.getId() %>" 
	   enctype="multipart/form-data">

         <input type="hidden" name="id" value="<%= teamMemberImg.getId() %>">	   
         <!-- Your name: <input type="text" name="myName" size="25"> <br>  -->
         1) Select File: <input type="file" name="myFile"><br>
         2) Then: <input type="submit" value="Upload File">
	</form>
  </td></tr></table>

<% /*
< % if ((teamMemberImg.getImgFileName() == null) || (teamMemberImg.getImgFileName().equals(""))) {
     if (teamMemberImg.getImgLoc() == null || teamMemberImg.getImgLoc().trim().length() == 0) { % >
       No image.
  < % } else { % >
     <br><img class="team" src="< %=domainApp% >< %= teamMemberImg.getImgLoc() % >"
       < % if (teamMemberImg.getImgWidth() > 0) { % >
         width="< %= teamMemberImg.getImgWidth() % >"
       < % } % >
       < % if (teamMemberImg.getImgHeight() > 0) { % >
         height="< %= teamMemberImg.getImgHeight() % >"
       < % } % >
       align="left"> <br><b>File Image:</b>< %= teamMemberImg.getImgLoc() % >
  < % } % >
< % } else { % >
    <table>
      <tr><td>
        2XXX<img src="< %=domainApp% >/teamMemberImgDownload.do?id=< %= teamMemberImg.getId() % >" >
      </td><td>
        <b>Name:</b> < %= teamMemberImg.getImgFileName() % >
        <br><b>Type:</b> < %= teamMemberImg.getImgFileType() % >
        <br><b>Size:</b> < %= teamMemberImg.getImgFileSize() % >
      </td></tr>
    </table>
< % } % >

*/ %>


