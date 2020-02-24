<%@ page import="org.calacademy.antweb.util.*" %>

<% 
// taxonomicPageImages-body.jsp is same as taxonChildImages.jsp except uses theTaxaPage instead of taxon and has images=true 
            
  String casteTarget = HttpUtil.getTargetMinusParam(request, "caste"); 
 
  String cvCaste = Caste.getCaste(request);
  String displayCaste = Caste.getShortDisplayCaste(request);
  if (Caste.DEFAULT.equals(displayCaste)) displayCaste = "All";
  //A.log("cvCaste:" + cvCaste);
%>
 
<div id="caste_toggle">
            <!-- div class="left"><img src = '<%= AntwebProps.getDomainApp() %>/image/new3.jpg' width=30></div -->
            <!-- (<img src="< %=AntwebProps.getDomainApp() % >/image/i.png" width=15>) -->
            <div class="left"><a href='<%= AntwebProps.getDomainApp() %>/common/casteDisplayPage.jsp' target="new">Caste:</a></div>

            <div id="change_caste" class="has_options">
                <span id="which_caste"><span style="text-transform:capitalize;"><%= displayCaste %></span></span>
                <div id="caste_choices" class="options">
                    <ul>
                        <li><a href="<%= casteTarget + "&caste=default" %>"><span style="text-transform:capitalize;">All</span></a></li>                        
                        <li><a href="<%= casteTarget + "&caste=male" %>"><span style="text-transform:capitalize;">Male</span></a></li>                        
                          <li>&nbsp;&nbsp;<a href="<%= casteTarget + "&caste=ergatoidMale" %>"><span style="text-transform:capitalize;">Ergatoid</span></a></li>                        
                          <li>&nbsp;&nbsp;<a href="<%= casteTarget + "&caste=alateMale" %>"><span style="text-transform:capitalize;">Alate</span></a></li>                        
                        <li><a href="<%= casteTarget + "&caste=worker" %>"><span style="text-transform:capitalize;">Worker</span></a></li>                        
                          <li>&nbsp;&nbsp;<a href="<%= casteTarget + "&caste=majorSoldier" %>"><span style="text-transform:capitalize;">Major/Soldier</span></a></li>                        
                          <li>&nbsp;&nbsp;<a href="<%= casteTarget + "&caste=normal" %>"><span style="text-transform:capitalize;">Normal</span></a></li>                        
                        <li><a href="<%= casteTarget + "&caste=queen" %>"><span style="text-transform:capitalize;">Queen</span></a></li>                        
                          <li>&nbsp;&nbsp;<a href="<%= casteTarget + "&caste=ergatoidQueen" %>"><span style="text-transform:capitalize;">Ergatoid</span></a></li>                        
                          <li>&nbsp;&nbsp;<a href="<%= casteTarget + "&caste=alateDealateQueen" %>"><span style="text-transform:capitalize;">Alate/Dealate</span></a></li>                        
                          <li>&nbsp;&nbsp;<a href="<%= casteTarget + "&caste=brachypterous" %>"><span style="text-transform:capitalize;">Brachypterous</span></a></li>                        

                        <li><a href="<%= casteTarget + "&caste=other" %>"><span style="text-transform:capitalize;">Other</span></a></li>                        
                          <li>&nbsp;&nbsp;<a href="<%= casteTarget + "&caste=intercaste" %>"><span style="text-transform:capitalize;">Intercaste</span></a></li>                        
                          <li>&nbsp;&nbsp;<a href="<%= casteTarget + "&caste=gynandromorph" %>"><span style="text-transform:capitalize;">Gynandromorph</span></a></li>                        
                          <li>&nbsp;&nbsp;<a href="<%= casteTarget + "&caste=larvaPupa" %>"><span style="text-transform:capitalize;">Larva/pupa</span></a></li>                        
                    </ul>
                </div>
                <div class="clear"></div>
            </div>

</div>

