<%-- Static include of taxonPage-body.jsp for the Videos text field: the field's text (an html embed code a curator
     pasted, shown as the video). A curator who may edit (canEditDesc) also gets an [Edit] link and the history
     link, a saved notice after a save, and the field being edited gets the editor. Uses the including page's desc,
     thisDesc, descHeader, editField and taxon. Text in table description_edit. --%>  <% /* TaxonImageEditField.jsp.  This jsp is included for each description item.
  
     accessGroup != null: if curator, allow for the editing of descriptions.
     desc.containsKey(thisDesc): If there is a description in the edit field display to user or empty to curator.
     thisDesc.equals(editField): Curator may edit one field at a time.  If it is this one, use editor.     
  */ %>   
  <%     
      if (canEditDesc || (desc.containsKey(thisDesc ))) { %> 

        <% // Then we will do somesomething at least, if only a header with an empty box. %>
        <% // desc, thisDesc and utility are defined in the including jsp (taxonPage-body.jsp) %>
        <a name="<%= thisDesc %>"></a><h3 style="float:left;"><%= descHeader %>:</h3>
<%      String contents = (String) desc.get(thisDesc);     
        if (canEditDesc) { %>     
       <% // They are an admin.  So display editable if this is the editField %>
       <% if (thisDesc.equals(editField)) { %>
         Click "Source" (in the new editor: the &lt;&gt; Source code button) and enter an html embed code.  Contact <%= AntwebUtil.getAdminEmail() %> with any technical issues.<br>
         After "Save" you will need to refresh the page to see the video.
         <div class="clear"></div><%@ include file="/common/descEdit/ckEditorMinConfigTaxonForm.jsp" %>         
       <% } else { 
              String browserParams = taxon.getBrowserParams();
              //if (AntwebProps.isDevMode()) AntwebUtil.log("taxonImageEditField.jsp taxonName:" + taxon.getTaxonName() + " browserParams:" + browserParams);       
       %>
         <p><small style="float:left; margin-top:14px; margin-left:5px;"> <a href="description.do?<%= org.calacademy.antweb.util.Html.escAttr(Utility.stripParams(browserParams, "editField")) %>&editField=<%= thisDesc %>#<%= thisDesc %>">[Edit]</a> <%= org.calacademy.antweb.DescHistory.link(request, thisDesc, thisPageTarget) %></small><div class="clear"></div></p>
         <% if (DescEditSafety.result(request) != null && DescEditSafety.result(request).saved && thisDesc.equals(DescEditSafety.result(request).field)) { %>
           <b><font color=green>Your Content has been saved.  </font><a href='<%= org.calacademy.antweb.util.Html.escAttr(AntwebProps.getDomainApp() + "/description.do?" + browserParams) %>'>Click here to proceed.</a> <font color=green></font></b>
         <% } else { %>
           <!-- Here we show the video -->
           <%= (contents != null) ? contents : "" %>
         <% } %>

       <% } %>
     <% } else { %>
       <% // just output the text %>
         <%= contents %>
     <% } %>
   <% } else { %>
     <% /* Do nothing.  Not a curator and there is no value. */ %>
   <% } %>

