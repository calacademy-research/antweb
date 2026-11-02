<%-- Unused as of 2026-11-02: redirects to the admin page of an old blog at
     http://www.antweb.org:8080/blog/wp-admin/. Nothing links it, and Caddy answers a direct request with 404 (not on
     the JSP allow list in docker/caddy/Caddyfile). --%><%
        String redirectURL = "http://www.antweb.org:8080/blog/wp-admin/";
        response.sendRedirect(redirectURL);
%>
