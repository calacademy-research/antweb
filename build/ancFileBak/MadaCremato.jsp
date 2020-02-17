<%@ page errorPage = "/error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
<%@ page import="org.calacademy.antweb.Login" %>
<%@ page import="org.calacademy.antweb.Utility" %>
<% String domainApp = (new Utility()).getDomainApp(); %>
<%@include file="/common/antweb-defs.jsp" %>
<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
<tiles:put name="title" value="The Crematogaster of Madagascar" />
<tiles:put name="body-content" type="string">
<div class="left">
    <h1>The Crematogaster of Madagascar</h1>	   	   
    <h3><strong>Taxonomic Notes</strong></h3>
    <p></p><p>The taxonomic revision of&nbsp;<em>Crematogaster</em>&nbsp;in Madagascar is in progress. Currently there are 25 valid species names and 9 subspecies names for Malagasy&nbsp;<em>Crematogaster</em>. The species-level taxonomy of&nbsp;<em>Crematogaster</em>&nbsp;is notoriously challenging and these numbers are in flux as many taxonomic changes will take place in the near future. In Madagascar,&nbsp;<em>Crematogaster</em>&nbsp;can be classified into five of the traditional subgenera within the genus:&nbsp;<em>Decacrema</em>Forel (1910),&nbsp;<em>Oxygyne</em>&nbsp;Forel (1901),&nbsp;<em>Orthocrema</em>&nbsp;Santschi (1918),&nbsp;<em>Mesocrema</em>&nbsp;Santschi (1928) and&nbsp;<em>Crematogaster</em>sensu stricto. For a brief discussion on the usefulness of the subgeneric classification within&nbsp;<em>Crematogaster</em>, please refer to Blaimer (2010). As the first in a series of species-group revisions, the Malagasy&nbsp;<em>Decacrema</em>-group was recently revised. This study discovered 4 new species, elevated one subspecies to species rank and could confirm one of the previously described species. Three remaining species names were associated with a species-complex that could not be resolved with current morphological and molecular methods and needs further investigation. The remaining species-group revisions will likely present a similar picture, resulting in a mixture of new species discoveries and synonymization of previous species names.</p><p></p><h3></h3><h3>Biology</h3><p></p><p>Most Malagasy&nbsp;<em>Crematogaster</em>&nbsp;species are arboreal, nesting in the lower and upper canopy either in dead branches or twigs, beneath canopy moss or epiphyte mats or in carton nests. A few species stray from this largely arboreal lifestyle and nest on the ground, either in rotten logs or under stones. In Madagascar independent nest constructions such as carton nests are built exclusively by&nbsp;<em>Crematogaster</em>&nbsp;species from at least three of the 'subgenera' (<em>Decacrema</em>,&nbsp;<em>Oxygyne</em>&nbsp;and&nbsp;<em>Crematogaster</em>). Carton nests are made from masticated plant material that the ants then plaster together in layers. Most arboreal species seem to also forage extensively on the ground, but this aspect of life history needs further investigation.&nbsp;<em>Crematogaster</em>&nbsp;is certainly one of the most dominant and abundant group of ants in the canopy, rivalled in numbers only by&nbsp;<em>Camponotus</em>,&nbsp;<em>Tetraponera</em>&nbsp;and&nbsp;<em>Pheidole</em>. In contrast to many other tropical regions,&nbsp;<em>Crematogaster</em>&nbsp;is not known to be involved in any close associations with plants in Madagascar. The majority of Malagasy&nbsp;<em>Crematogaster</em>&nbsp;seem to tend mealybugs, and carton nesters often house these in the main nest or in special carton shelters. Both twig- and carton-nesting species can further be found living in association with myrmecophilous beetles (modified from Blaimer, 2010).&nbsp;</p><p></p><h3>Distribution</h3><p></p><p>All native forest habitats in Madagascar; a few species also thrive in urban gardens.</p><p></p><h4>References</h4><p></p><p>Blaimer, B. B. (2010) Taxonomy and Natural History of the&nbsp;<em>Crematogaster</em>&nbsp;(<em>Decacrema</em>) group in Madagascar. Zootaxa, 2714, 1-39.</p>	   	   
    <%
    Login accessLogin = LoginMgr.getAccessLogin(request);      
    if ((accessLogin != null) && (accessLogin.isAdmin() || (accessLogin.getProjects().contains("madants")))) { %>
        <form method="POST" action="<%= domainApp %>/ancPageEdit.do?id=12" />	   		
            <input type="submit" value="Edit Page">	   	
        </form>
        <form method="POST" action="<%= domainApp %>/ancPageSave.do">	   		
            <input type="submit" value="Save Page">	   	
        </form>
    <% } %>
</div>
</tiles:put>
</tiles:insert>