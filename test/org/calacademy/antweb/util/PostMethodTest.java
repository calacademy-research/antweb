package test.org.calacademy.antweb.util;

import java.lang.reflect.Proxy;

import jakarta.servlet.http.HttpServletRequest;

import junit.framework.TestCase;

import org.calacademy.antweb.util.Csrf;
import org.calacademy.antweb.util.HttpUtil;

/**
 * 2026-10-27: a form post counts as a post only when it is sent as POST, and the CSRF check covers every
 * method that can change something. Otherwise a GET with a form content type would be treated as a post
 * by the actions while the filter, looking at the method, would not check it.
 */
public class PostMethodTest extends TestCase {

    private static HttpServletRequest request(String method, String contentType) {
        return (HttpServletRequest) Proxy.newProxyInstance(PostMethodTest.class.getClassLoader(),
            new Class<?>[] {HttpServletRequest.class}, (proxy, m, args) -> {
                switch (m.getName()) {
                    case "getMethod": return method;
                    case "getContentType": return contentType;
                    default: return null;
                }
            });
    }

    public void testFormPostIsPost() {
        assertTrue(HttpUtil.isPost(request("POST", "application/x-www-form-urlencoded")));
        assertTrue(HttpUtil.isPost(request("post", "multipart/form-data; boundary=x")));
    }

    public void testGetWithFormContentTypeIsNotPost() {
        assertFalse(HttpUtil.isPost(request("GET", "application/x-www-form-urlencoded")));
        assertFalse(HttpUtil.isPost(request("GET", "text/plain")));
        assertFalse(HttpUtil.isPost(request("PUT", "multipart/form-data")));
        assertFalse(HttpUtil.isPost(request("POST", null)));
    }

    public void testMethodsCheckedForToken() {
        assertTrue(Csrf.methodNeedsCheck("POST"));
        assertTrue(Csrf.methodNeedsCheck("put"));
        assertTrue(Csrf.methodNeedsCheck("DELETE"));
        assertTrue(Csrf.methodNeedsCheck(null));
        assertFalse(Csrf.methodNeedsCheck("GET"));
        assertFalse(Csrf.methodNeedsCheck("head"));
        assertFalse(Csrf.methodNeedsCheck("OPTIONS"));
    }
}
