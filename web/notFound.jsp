<%-- The 404 page of notFound.do, the struts action marked unknown=true: every .do address without an action gets
     this page with status 404. Tests: tests/e2e/test_actions.py (probe). --%><%@ page contentType="text/html; charset=UTF-8" %><% response.setStatus(404); %><!DOCTYPE html>
<html><head><title>Page not found - AntWeb</title></head>
<body><h1>Page not found</h1><p>There is no such page on AntWeb. <a href="/">Go to the AntWeb home page</a>.</p></body></html>
