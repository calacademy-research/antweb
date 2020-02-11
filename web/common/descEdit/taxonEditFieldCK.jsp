<!-- TaxonEditFieldCK.jsp. -->   
  <% /* This jsp is included for each description item.
     accessGroup != null: if curator, allow for the editing of descriptions.
     desc.containsKey(thisDesc): If there is a description in the edit field display to user or empty to curator.
     thisDesc.equals(editField): Curator may edit one field at a time.  If it is this one, use editor.     
  */
  
     if ((accessLogin != null) || (desc.containsKey(thisDesc ))) { 
       // Then we will do somesomething at least, if only a header with an empty box. 
       // desc, thisDesc and utility are defined in the including jsp (taxonPage-body.jsp) 
       
      //if (AntwebProps.isDevMode()) AntwebUtil.log("taxonEditFieldCK.jsp header:" + descHeader);       
       %>

     <p><a name="<%= thisDesc %>"></a><h3 style="float:left;"><%= descHeader %>:</h3>
     <% if (accessLogin != null) { 
          // They are an admin.  So display editable if this is the editField 
          if ((thisDesc.equals(editField)) && (!thisDesc.equals("notes") || ( hasExtraPrivs) )) { %>
         <div class="clear"></div>
         <%= descNotes %>
         <%@ include file="/common/descEdit/ckEditorMinConfigTaxonForm.jsp" %>
         <% // AntwebUtil.log("include cdEditorForm.jsp ****"); %>
       <% } else { %>
         <% if (thisDesc.equals("notes") && !hasExtraPrivs ) { %>            
         <%= (desc.get(thisDesc) != null) ? desc.get(thisDesc) : "" %>   
         <% } else { 
              String browserParams = taxon.getBrowserParams();
              //if (AntwebProps.isDevMode()) AntwebUtil.log("taxonEditFieldCk.jsp taxonName:" + taxon.getTaxonName() + " browserParams:" + browserParams);
              String targetDo = "description.do";
              if (taxon instanceof Specimen) targetDo = "specimen.do";
         %>
         <small style="float:left; margin-top: 1px; margin-left:5px;"> <a href="<%= targetDo %>?<%= Utility.stripParams(browserParams, "editField") %>&editField=<%= thisDesc %>#<%= thisDesc %>">[Edit]</a></small><div class="clear"></div>
         <%= (desc.get(thisDesc) != null) ? desc.get(thisDesc) : "" %>       
         <% }
          }
        } else { %>
       <% // just output the text %>
         <%= desc.get(thisDesc) %>
     <% } %>
   <% } else { %>
     <% /* Do nothing.  Not a curator and there is no value. */ %>
   <% } %>

