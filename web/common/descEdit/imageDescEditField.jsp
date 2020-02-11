  <%
  if (true) {
      boolean turnedOn =  true; //AntwebProps.isDevOrStageMode();
      
      if ((accessLogin != null) || (desc.containsKey(thisDesc ))) { %>   

     <% if (accessLogin != null) { %>
          <p><a name="<%= thisDesc %>"></a><h3 style="float:left;"><%= descHeader %>:</h3>
     <% } %>
<%      String contents = (String) desc.get(thisDesc);

        if (turnedOn && accessLogin != null) { %>
       <% // They are an admin.  So display editable if this is the editField %>
       <% if (thisDesc.equals(editField)) { %>
         Click "Source" to edit html code.  Contact <%= AntwebUtil.getAdminEmail() %> with any issues.<br>
         <div class="clear"></div><%@ include file="/common/descEdit/ckEditorMinConfigDescEditForm.jsp" %>
       <% } else { %>
         <p><small style="float:left; margin-top:14px; margin-left:5px;"> <a href="<%= thisPageTarget %>&editField=<%= thisDesc %>#<%= thisDesc %>">[Edit]</a></small><div class="clear"></div></p>
         <html:form method="POST" action="descEditImageUpload.do" enctype="multipart/form-data">
           <input type="hidden" name="homePageDirectory" value="curator" />
           <input type="hidden" name="editField" value="<%= thisDesc %>">
           <input type="hidden" name="isSaveEditField" value="true">
           <input type="hidden" name="target" value="<%= thisPageTarget %>">

           <span class="float:left;"><html:file property="theFile2" /></span>
           <input type="submit" class="tool_label"  style="float:none;" value="Submit">
           <div class="clear"></div>
         </html:form>
         <%= (contents != null) ? contents : "" %>       
       <% } %>       
     <% } else { %>
       <% // just output the text %>
         <%= contents %>
     <% } %>
   <% } else { %>
     <% /* Do nothing.  Not a curator and there is no value. */ %>
   <% } 
  } %>

