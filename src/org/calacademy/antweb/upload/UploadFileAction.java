package org.calacademy.antweb.upload;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.regex.Pattern;

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
 * /uploadFile.do?file=20220224-11:49:08-specimen2.txt: download one archived upload file
 * from web/upload/ (the copy of each specimen or species list file as it was uploaded).
 * Admins, or curators for their own group's specimen files; web/upload/ itself is not
 * served to the public.
 */
public final class UploadFileAction extends AntwebAction {

    // A plain file name in web/upload/: no folders, no leading dot.
    private static final Pattern FILE_NAME = Pattern.compile("[A-Za-z0-9_:-][A-Za-z0-9_.:-]{0,127}");

    /** True if name is an acceptable web/upload/ file name. */
    public static boolean isValidName(String name) {
        return name != null && FILE_NAME.matcher(name).matches() && !name.contains("..");
    }

    /**
     * True if this login may read the archived upload file: admins any file, curators only
     * their own group's specimen files (20220224-11:49:08-specimen2.txt, ...-specimenTW2.txt,
     * ...-specimenGBIF2.txt for group 2). Worldants and species list files are admin only.
     */
    public static boolean mayRead(Login login, String name) {
        if (login == null || name == null) return false;
        if (LoginMgr.isAdmin(login)) return true;
        if (!LoginMgr.isCurator(login)) return false;
        int groupId = login.getGroupId();
        if (groupId <= 0) return false;
        for (String kind : new String[] {"-specimen", "-specimenTW", "-specimenGBIF"}) {
            if (name.endsWith(kind + groupId + ".txt")) return true;
        }
        return false;
    }

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response) throws IOException {

        ActionForward c = Check.curator(request, mapping); if (c != null) return c;

        String name = request.getParameter("file");
        if (!isValidName(name) || !mayRead(LoginMgr.getAccessLogin(request), name)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }
        File dir = new File(AntwebProps.getWebDir() + "upload");
        File file = new File(dir, name);
        if (!file.isFile() || !dir.getCanonicalFile().equals(file.getCanonicalFile().getParentFile())) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }

        response.setContentType("text/plain");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + name.replace(':', '_') + "\"");
        response.setContentLengthLong(file.length());
        try (OutputStream out = response.getOutputStream()) {
            Files.copy(file.toPath(), out);
        }
        return null;
    }
}
