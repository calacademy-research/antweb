<%@ page language="java" %>
<%@ page errorPage = "/error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<%
     String domainApp = (new org.calacademy.antweb.Utility()).getDomainApp();	
%>
     
<script type="text/javascript">
 
function selectAll(thisForm) {
  var count = thisForm.adm2s.length;
  var checkedVal = thisForm.selectall.checked;
  for (var loop = 0; loop < count; loop++) {
    thisForm.adm2s[loop].checked = checkedVal;
  }
}

</script>

<div class=left>
<h1>Bay Area Ants</h1>
<b></b>
<hr></hr>
Did you know that three kinds of Army Ants live in the Bay Area? Other interesting ants include the centipede eating fat-waisted ants (<i><a href="<%= domainApp %>/description.do?name=amblyopone&rank=genus&project=californiaants">Amblyopone</a></i>), specialized spider egg-eating ants (<i><a href="<%= domainApp %>/description.do?name=proceratium&rank=genus&project=californiaants">Proceratium</a></i>), and the fungus-growing ants (<i><a href="<%= domainApp %>/description.do?name=cyphomyrmex&rank=genus&project=californiaants">Cyphomyrmex</a></i>), which use caterpillar frass (droppings) to feed their fungus.  In the ant world, slavery is still practiced amongst the genus <i><a href="<%= domainApp %>/description.do?name=polyergus&rank=genus&project=californiaants">Polyergus</a></i>.  These slave-maker ants stage dramatic raids on the colonies of other ant species, enslaving individuals who must then care for their captors and their brood.

<p>These are just a few examples of over 100 species of ants roaming the Bay Area.  However, the invasion of the Argentine Ant (<i><a href="<%= domainApp %>/description.do?rank=species&genus=linepithema&name=humile&project=californiaants">Linepithema humile</a></i>) into the Bay Area has put many of the native species at risk.  

<p>Join the Bay Area ant survey sponsored by California Academy of Sciences and help us discover and map the distribution of our remaining native ants populations and the spread of the Argentine Ant.
 
<p>AntWeb will provide tools to help you identify the ants found in your school or back yard.  You can also bring the ants you collect to the Naturalist Center at CAS and have the specimens identified using a microscope and ant key.

<p><a href=<%= domainApp %>/description.do?rank=species&genus=cyphomyrmex&name=wheeleri&project=californiaants><img class=border border=0 src=<%= domainApp %>/image/bayarea1.jpg width=184 height=184></a> <img class=border border=0 src=<%= domainApp %>/image/bayarea3.jpg width=184 height=184></a>

</div>

<div class="right">
<img class=border border=0 src=<%= domainApp %>/image/bayarea_map.gif width=202 height=239>

<p>
<div class=green_module><span class=module_header>SEARCH BAY AREA ANTS:</span></div>
<div class=module_contents>

<html:form method="POST" action="bayAreaSearch"> <!--  -->
<input type="hidden" name="searchMethod" value="bayAreaSearch"> <!-- was:  -->

<html:checkbox property="adm2s" value="alameda"/> Alameda<br>
<html:checkbox property="adm2s" value="contra costa"/> Contra Costa<br>
<html:checkbox property="adm2s" value="marin"/> Marin<br>
<html:checkbox property="adm2s" value="napa"/> Napa<br>
<html:checkbox property="adm2s" value="sacramento"/> Sacramento<br>
<html:checkbox property="adm2s" value="san francisco"/> San Francisco<br>
<html:checkbox property="adm2s" value="san joaquin"/> San Joaquin<br>
<html:checkbox property="adm2s" value="san mateo"/> San Mateo<br>
<html:checkbox property="adm2s" value="santa clara"/> Santa Clara<br>
<html:checkbox property="adm2s" value="santa cruz"/> Santa Cruz<br>
<html:checkbox property="adm2s" value="solano"/> Solano<br>
<html:checkbox property="adm2s" value="sonoma"/> Sonoma<br>
<html:checkbox property="adm2s" value="yolo"/> Yolo<br>
<input type="checkbox" name="selectall" onClick="selectAll(document.bayAreaSearchForm);"> Select All <br>
<p align="center"><input type="submit" class=submit value="Search &#187"></p>
</html:form>
</div>

</div>
