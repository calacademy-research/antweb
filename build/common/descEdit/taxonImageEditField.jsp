<!-- TaxonImageEditField.jsp -->
  <% /* This jsp is included for each description item.
  
     accessGroup != null: if curator, allow for the editing of descriptions.
     desc.containsKey(thisDesc): If there is a description in the edit field display to user or empty to curator.
     thisDesc.equals(editField): Curator may edit one field at a time.  If it is this one, use editor.     
  */ %>   

<%     
      if ((accessLogin != null) || (desc.containsKey(thisDesc ))) { %>   
        <% // Then we will do somesomething at least, if only a header with an empty box. %>
        <% // desc, thisDesc and utility are defined in the including jsp (taxonPage-body.jsp) %>
        <p><a name="<%= thisDesc %>"></a><h3 style="float:left;"><%= descHeader %>:</h3>

<%      String contents = (String) desc.get(thisDesc);     

        if (accessLogin != null) { %>     
       <% // They are an admin.  So display editable if this is the editField %>
       <% if (thisDesc.equals(editField)) { %>
         Click "Source" to edit html code.  Contact <%= AntwebUtil.getAdminEmail() %> with any issues.<br>       
         <div class="clear"></div><%@ include file="/common/descEdit/ckEditorMinConfigTaxonForm.jsp" %>         
       <% } else { 
              String browserParams = taxon.getBrowserParams();
              //if (AntwebProps.isDevMode()) AntwebUtil.log("taxonImageEditField.jsp taxonName:" + taxon.getTaxonName() + " browserParams:" + browserParams);       
       %>
         <p><small style="float:left; margin-top:14px; margin-left:5px;"> <a href="description.do?<%= Utility.stripParams(browserParams, "editField") %>&editField=<%= thisDesc %>#<%= thisDesc %>">[Edit]</a></small><div class="clear"></div></p>
         <html:form method="POST" action="descEditImageUpload.do" enctype="multipart/form-data">
           <input type="hidden" name="homePageDirectory" value="curator" />
<input type="hidden" name="editField" value="images">
<input type="hidden" name="isSaveEditField" value="true">
<input type="hidden" name="rank" value="<%= taxon.getRank() %>">
<input type="hidden" name="name" value="<%= taxon.getName() %>">
<input type="hidden" name="target" value="description.do?<%= Utility.stripParams(browserParams, "editField") %>">
<input type="hidden" name="contents" value="<%= ((contents==null) ? guiDefaultContent : java.net.URLEncoder.encode(contents)) %>">

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
   <% } %>
