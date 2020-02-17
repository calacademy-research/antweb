 <logic:equal name="child" property="hasImages" value="true">
    <div class=browse_span_col><a href="description.do?<bean:write name="child" property="browserParams"/>">
       <img src="image/has_photo.gif" border="0" title="<bean:write name="child" property="hasImagesCount" /> Images"></a>
 </logic:equal>
 <logic:notEqual name="child" property="hasImages" value="true">
     <div class=browse_span_col><img src="image/no_photo.gif" border="0">
 </logic:notEqual>

 <logic:equal name="child" property="isValidName" value="true">
  <img src="image/checkmark14.png" border="0" title="Valid Name">
 </logic:equal>
 <logic:notEqual name="child" property="isValidName" value="true">
  <img src="image/no_photo.gif" border="0">
 </logic:notEqual> 
            
 <logic:greaterThan name="child" property="childrenCount" value="0">
    <a href="description.do?<bean:write name="child" property="browserParams"/>">
 </logic:greaterThan>


