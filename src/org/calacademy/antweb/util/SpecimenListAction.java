package org.calacademy.antweb.util;

import java.io.*;
import java.util.*;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import javax.sql.DataSource;

import org.apache.struts.action.*;

import java.sql.*;
import java.util.Date;

import org.calacademy.antweb.*;
import org.calacademy.antweb.home.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * getSpecimenList.do: the Download Data button of a taxon page (pageToolLinks.jsp, pageTools.jsp).
 * Writes a tab-delimited file of the specimens of the taxon on that page and returns a link to it
 * (shown in the download box, an iframe).
 *
 * Limits (Brian Fisher, 2026-10-21; DownloadLimits, docs/guides/downloads.md): login required; a
 * genus, species or subspecies only (no family or subfamily files); at most 5,000 specimens
 * (administrators: no cap). A place page gives only the specimens collected in that place
 * (SpecimenDb.getDownloadFromWhere); before, it gave every specimen, worldwide, of the names on
 * the place's list.
 */
public final class SpecimenListAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(SpecimenListAction.class);

    static final String DIR = "/web/data/specimenData";

    // Files are kept a day: long enough to fetch, and the folder does not grow.
    static final long KEEP_MILLIS = 24L * 60 * 60 * 1000;

    public ActionForward execute(
            ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {

        ActionForward a = Check.loginValid(request, mapping);
        if (a != null) return a;
        Login accessLogin = LoginMgr.getAccessLogin(request);
        if (accessLogin == null) {
            request.setAttribute("message", "Please log in to download data.");
            return mapping.findForward("justMessage");
        }

        Date startTime = new Date();
        DynaActionForm df = (DynaActionForm) form;

        Overview overview = null;
        try {
            overview = OverviewMgr.getAndSetOverview(request);
        } catch (AntwebException e) {
            return OverviewMgr.returnMessage(request, mapping, e);
        }

        String taxonName = (String) df.get("taxonName");
        // taxonName becomes part of a file name: plain taxon names only (no paths).
        if (taxonName == null || !taxonName.matches("[A-Za-z0-9 ._()-]{1,200}") || taxonName.contains("..")) {
            request.setAttribute("message", "Invalid taxon name.");
            return mapping.findForward("justMessage");
        }

        Connection connection = null;
        String dbMethodName = DBUtil.getDbMethodName("SpecimenListAction.execute()");
        try {
            DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, dbMethodName);

            if (DBStatus.isServerBusy(connection, request)) {
                return mapping.findForward("message");
            }

            Taxon taxon = new TaxonDb(connection).getTaxon(taxonName);
            if (taxon == null || taxon.getRank() == null) {
                s_log.warn("execute() taxon not found:" + taxonName);
                request.setAttribute("message", "Taxon not found.");
                return mapping.findForward("justMessage");
            }
            String rank = taxon.getRank();
            if (!DownloadLimits.isDownloadRank(rank)) {
                request.setAttribute("message", DownloadLimits.RANK_REFUSED);
                return mapping.findForward("justMessage");
            }

            String subspecies = Rank.SUBSPECIES.equals(rank) ? taxon.getSubspecies() : null;
            String species = Rank.GENUS.equals(rank) ? null : taxon.getSpecies();

            SpecimenDb specimenDb = new SpecimenDb(connection);
            int matching = specimenDb.countDownloadSpecimens(overview, taxon.getFamily(), taxon.getSubfamily(), taxon.getGenus(), species, subspecies);
            if (matching < 0) {
                request.setAttribute("message", "Downloads are not available on this page.");
                return mapping.findForward("justMessage");
            }
            int cap = DownloadLimits.capFor(LoginMgr.isAdmin(accessLogin));
            ArrayList<String> codes = specimenDb.getDownloadSpecimenCodes(overview, taxon.getFamily(), taxon.getSubfamily(), taxon.getGenus(), species, subspecies, cap);

            new Utility().createDirectory(DIR);
            DownloadLimits.removeOldFiles(new File(AntwebProps.getDocRoot() + DIR), ".txt", System.currentTimeMillis() - KEEP_MILLIS);
            String fileName = "/" + new java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + "-" + accessLogin.getId() + "-"
                + overview.getName().replaceAll("[^A-Za-z0-9._-]", "_") + "-" + taxonName.replaceAll("[^A-Za-z0-9._-]", "_") + ".txt";
            String fullPath = AntwebProps.getDocRoot() + DIR + fileName;
            writeFile(codes, fullPath, connection);

            String url = AntwebProps.getDomainApp() + DIR + fileName;
            String message = "&middot; <a href=\"" + Html.escAttr(url) + "\" target=\"new\">Tab-delimited data</a>";
            if (DownloadLimits.isCapped(matching, cap)) {
                message += "<p>" + Html.esc(DownloadLimits.capMessage(matching)) + "</p>";
            }
            s_log.debug("execute() taxon:" + taxonName + " overview:" + overview + " matching:" + matching + " written:" + codes.size());
            request.setAttribute("message", message);
            return mapping.findForward("justMessage");

        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
            request.setAttribute("message", "The download could not be made.");
            return mapping.findForward("justMessage");
        } finally {
            QueryProfiler.profile("specimenList", startTime);
            DBUtil.close(connection, this, dbMethodName);
        }
    }

    private void writeFile(ArrayList<String> codes, String fullPath, Connection connection) throws SQLException {
        StringBuilder dataBuffer = new StringBuilder();
        dataBuffer.append(Specimen.getDataHeader()).append("\n");
        int count = 0;
        for (String code : codes) {
            ++count;
            Specimen specimen = Specimen.getShallowInstance(code, connection);
            dataBuffer.append(specimen.getData(connection)).append("\n");
            if (count % 1000 == 0) {
                LogMgr.appendFile(fullPath, dataBuffer.toString());
                dataBuffer = new StringBuilder();
            }
        }
        LogMgr.appendFile(fullPath, dataBuffer.toString());
    }
}
