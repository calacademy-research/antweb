package org.calacademy.antweb.curate;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import org.calacademy.antweb.AntwebAction;
import org.calacademy.antweb.Login;
import org.calacademy.antweb.util.AntwebProps;
import org.calacademy.antweb.util.Check;
import org.calacademy.antweb.util.LoginMgr;

/**
 * /curatorFiles.do: the logged-in curator's own folder, web/curator/<login id>/
 * (images uploaded for page text, and ancillary files).
 * Lists names, sizes and dates, with links to the files. Only the caller's own
 * folder; there is no parameter to choose another one. Tomcat does not list
 * folders itself.
 */
public final class CuratorFilesAction extends AntwebAction {

    /** One entry of the folder. */
    public record Entry(String name, long size, long modified, boolean isDir) {}

    private static final int MAX_ENTRIES = 5000;

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        ActionForward c = Check.curator(request, mapping); if (c != null) return c;
        Login accessLogin = LoginMgr.getAccessLogin(request);

        String webPath = "/web/curator/" + accessLogin.getId() + "/";
        File dir = new File(AntwebProps.getWebDir() + "curator/" + accessLogin.getId());

        List<Entry> entries = new ArrayList<>();
        boolean truncated = false;
        File[] files = dir.isDirectory() ? dir.listFiles() : null;
        if (files != null) {
            for (File f : files) {
                String name = f.getName();
                if (name.startsWith(".")) continue;
                if (!f.isFile() && !f.isDirectory()) continue;
                if (entries.size() >= MAX_ENTRIES) { truncated = true; break; }
                entries.add(new Entry(name, f.isFile() ? f.length() : 0, f.lastModified(), f.isDirectory()));
            }
        }
        entries.sort(Comparator.comparing((Entry e) -> !e.isDir()).thenComparing(e -> e.name().toLowerCase()));

        request.setAttribute("curatorFilesPath", webPath);
        request.setAttribute("curatorFiles", entries);
        request.setAttribute("curatorFilesTruncated", truncated);
        return mapping.findForward("success");
    }
}
