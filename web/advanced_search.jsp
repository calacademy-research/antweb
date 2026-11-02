<%-- Unused as of 2026-11-02: redirects to advSearch.do. No action forwards here, no page links it, and Caddy answers
     a direct request with 404 (not on the JSP allow list in docker/caddy/Caddyfile). --%><%
        String redirectURL = org.calacademy.antweb.util.AntwebProps.getDomainApp() + "/advSearch.do";
        response.sendRedirect(redirectURL);
%>