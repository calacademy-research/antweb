<%-- Static include of taxonPage-body.jsp (overview, distribution, biology, identification, comments, taxonomic
     notes, notes, references) and specimen-body.jsp (the specimen's page text), once per text field: the heading
     and the field's text. A curator who may edit (canEditDesc) also gets an [Edit] link (description.do or
     specimen.do with editField) and the field's history link, and the field being edited gets the editor
     (ckEditorMinConfigTaxonForm.jsp); the notes field needs hasExtraPrivs. Uses the including page's desc,
     thisDesc, descHeader, descNotes, editField and taxon. Text in table description_edit. Tests:
     tests/e2e/test_page_editor.py, test_editor_roundtrip.py. --%><!-- TaxonEditFieldCK.jsp. -->   
  <% /* This jsp is included for each description item.
     accessGroup != null: if curator, allow for the editing of descriptions.
     desc.containsKey(thisDesc): If there is a description in the edit field display to user or empty to curator.
     thisDesc.equals(editField): Curator may edit one field at a time.  If it is this one, use editor.     
  */
  
     if (canEditDesc || (desc.containsKey(thisDesc ))) { 
       // Then we will do somesomething at least, if only a header with an empty box. 
       // desc, thisDesc and utility are defined in the including jsp (taxonPage-body.jsp) 
       
      //if (AntwebProps.isDevMode()) AntwebUtil.log("taxonEditFieldCK.jsp header:" + descHeader);       
       %>

     <p><a name="<%= thisDesc %>"></a><h3 style="float:left;"><%= descHeader %>:</h3>
     <% if (canEditDesc) { 
          // A curator or admin (DescEditSafety.canEdit).  So display editable if this is the editField 
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
         <small style="float:left; margin-top: 1px; margin-left:5px;"> <a href="<%= targetDo %>?<%= org.calacademy.antweb.util.Html.escAttr(Utility.stripParams(browserParams, "editField")) %>&editField=<%= thisDesc %>#<%= thisDesc %>">[Edit]</a> <%= org.calacademy.antweb.DescHistory.link(request, thisDesc, thisPageTarget) %></small><div class="clear"></div>
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

