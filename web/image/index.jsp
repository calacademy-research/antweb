<%-- Redirects to the home page (index.do). Tomcat opens it for the folder address /image/ (index.jsp is one of its
     default welcome files), so the folder of site icons is never listed. --%><% response.sendRedirect(org.calacademy.antweb.util.AntwebProps.getDomainApp() + "/index.do"); %>
