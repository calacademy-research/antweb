
<% TeamMember teamMemberA = (TeamMember) request.getAttribute("teamMember"); %>
   <table>
<% if (teamMemberA != null) {
    String fileType = teamMemberA.getImgFileType();
    if ((fileType != null) && (!fileType.equals(""))) {   // Get Img from database (default)   
      //AntwebUtil.log("teamMember.jsp 1 fileType: " + fileType);
    %>
     <tr>
      <td>
        <img src="<%= domainApp %>/teamMemberImgDownload.do?id=<%= teamMemberA.getId() %>" width="217" align="left">
      </td>  
     </tr>
  <% } else {
       //AntwebUtil.log("teamMember.jsp 2 fileType is " + fileType);

       //out.println("teamMember.jsp filetype is " + fileType);
     } %>  
   <tr>
    <td>
    <!-- p>Y<img src="< %= teamMemberA.getImgLoc() % >< %= teamMemberA.getImgFileName() % >" alt="< %= teamMemberA.getName() % >" -->
    <p><strong><a href="mailto:<%= teamMemberA.getEmail() %>"><%= teamMemberA.getName() %></a>, <%= teamMemberA.getRoleOrg() %></strong>
    <br><%= teamMemberA.getText() %></p>
    </td>
   </tr>
   </table>
<% } %>  
