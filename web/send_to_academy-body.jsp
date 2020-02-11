<%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<% String domainApp = (new org.calacademy.antweb.Utility()).getDomainApp(); %>
<h1>Urban Ant Collector - Sending in Your Specimens</h1>
<hr></hr>
<div class=left>
There are a variety of methods to collect ants, including picking them up gently and dropping them into a vial (e.g. <a href="http://www.amazon.com/BD-Topper-Glass-Vacutainer-Sterile/dp/B000MYOCYY">http://www.amazon.com/BD-Topper-Glass-Vacutainer-Sterile/dp/B000MYOCYY</a>) or container containing a preservative. The best preservatives include a high concentration commercially available ethanol (do not use rubbing alcohol which is isopropyl alcohol). While visiting the Soviet Union in 1945, a Harvard professor, attending a dinner hosted by Josef Stalin, picked up an ant and preserved it in the vodka from his cocktail! We don't recommend that you use vodka to preserve your specimens (though in a pinch it would be better than rubbing alcohol).

<p>An alternative is to simply place the ant in a small container, and then freeze the ant.  By freezing the ant for 48 hours, the ant is quickly dried and preserved for later DNA analysis at CAS.

<p>Once you've done that, you can ship and mail the specimens to:

<p><b>Ant Survey</b><br />
Department of Entomology<br />
California Academy of Sciences<br />
55 Music Concourse Drive<br />
San Francisco, CA 94118<br />

<p>Be sure to include your name, your email address, and, if possible, the collection number you assigned to your ant (the one with your initials and a number) in the Ant Collector app. That way we can correctly map your specimens to the data you sent from your phone!

</div>
<div class=right>
<ul>
<li><a href="<%= domainApp %>/antcollect.jsp">How to use the App</a></li>
<li><b>How to send specimens to us</b></li>
</ul>
</div> 
