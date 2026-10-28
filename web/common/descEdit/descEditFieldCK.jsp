<!-- TaxonEditFieldCK.jsp. -->   
  <% 
  if (true) {
     boolean turnedOn = true; // AntwebProps.isDevOrStageMode();
     
     if (canEditDesc || (desc.containsKey(thisDesc ))) {        
       %>

     <p><a name="<%= thisDesc %>"></a><h3 style="float:left;"><%= descHeader %>:</h3>
     <% if (turnedOn && canEditDesc) { 
          // A curator or admin (DescEditSafety.canEdit).  So display editable if this is the editField 
          if ((thisDesc.equals(editField)) && (!thisDesc.equals("notes") || ( hasExtraPrivs) )) { %>
         <div class="clear"></div>
         <%= descNotes %>
         <%@ include file="/common/descEdit/ckEditorMinConfigProjectForm.jsp" %>
         <% // AntwebUtil.log("include ckEditorMinConfigForm.jsp ****"); %>
       <% } else { %>
         <% if (thisDesc.equals("notes") && !hasExtraPrivs ) { %>            
         <%= (desc.get(thisDesc) != null) ? desc.get(thisDesc) : "" %>   
         <% } else { 
         %>
         <small style="float:left; margin-top: 1px; margin-left:5px;"> <a href="<%= org.calacademy.antweb.util.Html.escAttr(thisPageTarget) %>&editField=<%= thisDesc %>#<%= thisDesc %>">[Edit]</a> <%= org.calacademy.antweb.DescHistory.link(request, thisDesc, thisPageTarget) %></small><div class="clear"></div>
         <%= (desc.get(thisDesc) != null) ? desc.get(thisDesc) : "" %>       
         <% }
          }
        } else { %>
       <% // just output the text %>
         <%= desc.get(thisDesc) %>
     <% } %>
   <% } else { %>
     <% /* Do nothing.  Not a curator and there is no value. */ %>
   <% } 
  } %>

