<%@ page import="java.text.*" %>
<%
	String groupName = null;
	String email = null; 
	String displayName = null; 
	String username = null; 
	String accessLoginId = null;

    Login tAccessLogin = LoginMgr.getAccessLogin(request);
    Group tAccessGroup = GroupMgr.getAccessGroup(request);
	if (tAccessGroup != null) {
	  groupName = tAccessGroup.getName();
	  if (tAccessLogin != null) {
//A.log("loginPanel.jsp accessGroupLogin:" + accessGroupLogin);	  
		email = tAccessLogin.getEmail();
		username = tAccessLogin.getName();
		displayName = tAccessLogin.getDisplayName(); 
		accessLoginId = "" + tAccessLogin.getId();
	  }
	}
	
    String target = HttpUtil.getTarget(request);

    String aTarget = null;
    if ( target != null && !target.contains("logout.do")){
      aTarget = "?target=" + java.net.URLEncoder.encode(target);
    } else {
      target = "";
      aTarget = "";    
    }
    
    String nowTimeStamp = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());
    //String testBeginDisplay = "2017.12.10.16.0.0";
    String beginDisplay = "2018.12.11.16.0.0";
    String endDisplay = "2018.12.12.10.30.0";
    int after = nowTimeStamp.compareTo(beginDisplay);
    int before = nowTimeStamp.compareTo(endDisplay);
    boolean display = after == 1 && before == -1;
    String siteWarning = AntwebUtil.readFile("/data/antweb/web/siteWarning.jsp");
    if (siteWarning == null) siteWarning = "";
    //A.log("academyHeader now:" + nowTimeStamp + " display:" + display + " after:" + after + " before:" + before);
    //if (siteWarning != null) out.println("<li><br>" + siteWarning + "</li>");    
    %>

    <%
	if (tAccessLogin != null) { 
	  //A.log("academyHeader.jsp displayName:" + displayName + " id:" + tAccessGroup.getId());
        
        if ("Anonymous".equals(displayName)) { %>
			<span id="login_small">Anonymous    
     <% } else { 
            String displayNameLink = null;
			if (tAccessLogin.isCurator()) {
			  displayNameLink = "<a href=\"" + AntwebProps.getSecureDomainApp() + "/viewLogin.do?id=" + accessLoginId + "\">" + displayName + "</a>";
			} else {
			  displayNameLink = "<a href=\"" + AntwebProps.getSecureDomainApp() + "/editLogin.do?id=" + accessLoginId + "\">" + displayName + "</a>";
			} 
			//AntwebUtil.log("academyHeader.jsp displayNameLink:" + displayNameLink);
			%>
			<span id="login_small"><%= displayNameLink %>  
 	 <% } // anonymous

        //A.log("academyHeader.jsp userName:" + username);
        if (username != null && (username.contains("photo_review") || "Anonymous".equals(displayName) || (tAccessGroup.getId() < 0)) ) {
          // do nothing
	    } else { %>
          &nbsp;|&nbsp;
          
          <!-- The redDot.png code was here -->
          
          <a href="<%= domainApp %>/curate.do">Curate</a> 
     <% } %>
        &nbsp;| &nbsp;<a href="<%= domainApp %>/logout.do<%= aTarget %>">Logout</a></span>
 <% } else { %>
        <div class="curator_login">

     <!-- redDot.png code -->             
	 <% 
		if (AntwebMgr.hasServerMessage()) { 
		 // A.log("loginPanel.jsp serverMessage:" + AntwebMgr.getServerMessage());
		%> 
		<img src="<%= domainApp %>/image/redDot.png" width="10" title="<%= AntwebMgr.getServerMessage() %>&nbsp;">
	 <% } %>
        
     <% if (!HttpUtil.isOnline()) { %>
          <a href='http://localhost/login.do'>
     <% } %>
     Login
     <% if (!HttpUtil.isOnline()) { %>
          </a>
     <% } %>
        </div>
 <% } %>

        <div id="login_form" style="display:none;">
            <form name="loginForm" method="post" action="<%= AntwebProps.getSecureDomainApp() %>/login.do">  
            <input type="hidden" name="target" value="<%= target %>"> 
            Username: <input id="login_username" type="text" class="login_input" name="userName"> Password: <input type="password" class="login_input" name="password"> 
            <input type="submit" class="login_submit" value="Go">
            <br />
            <div class="small" style="text-align:right; width:450px;">                
              <a href="<%= AntwebProps.getSecureDomainApp() %>/login.do">Create Account</a> |               
              <a href="<%= domainApp %>/forgotPassword.do">Forgot Password?</a> | 
              <span class="curator_login">Cancel</span>
            </div>
            </form>
        </div> <!-- end of loginForm -->                     

        <%= siteWarning %>




