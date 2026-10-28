package org.calacademy.antweb.upload;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.regexp.RE;
import org.apache.regexp.RESyntaxException;
import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.upload.FormFile;
import org.calacademy.antweb.*;
import org.calacademy.antweb.curate.speciesList.SpeciesListUploader;
import org.calacademy.antweb.home.*;
import org.calacademy.antweb.util.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.*;

/**
 * This class takes the UploadForm and retrieves the text value
 * and file attributes and puts them in the request for the display.jsp
 * page to display them
 *
 */

public class UploadAction extends AntwebAction {

	private static final Log s_log = LogFactory.getLog(UploadAction.class);

	// Site-wide jobs: admins only.
	static final java.util.Set<String> ADMIN_ONLY_ACTIONS = java.util.Set.of(
			"fetchAndReloadWorldants",
			"runStatistics", "runCountCrawls", "genRecentDescEdits");

	// Removed sub-actions (unused tools, 2026-10): answered as "Action not found." before anything else.
	// allSpecimenFiles (reload every group from the working folder) and GBIFUpload: Curate page cleanup.
	// reloadSpeciesLists (its code was commented out) and toggleDownTime (serverStatus.do has the
	// working switch): removed 2026-10-19. genAll (an alias of genRecentDescEdits) merged into
	// utilData.do?action=genRecentContent (Technical page), which also regenerates recent images.
	static final java.util.Set<String> REMOVED_ACTIONS = java.util.Set.of(
			"reloadSpecimenList", "removeSpecimenList", "uploadSpeciesList", "specimenTest", "speciesTest",
			"allSpecimenFiles", "GBIFUpload", "reloadSpeciesLists", "toggleDownTime", "genAll");

	// Admin jobs linked from the Curate page: a GET shows a confirmation page that repeats the
	// request by POST with the form token (PostOnly). Every other sub-action must be posted.
	static final java.util.Set<String> CONFIRM_ON_GET = java.util.Set.of("runStatistics", "genRecentDescEdits");

	public static boolean isRemovedAction(String action) {
		return action != null && REMOVED_ACTIONS.contains(action);
	}

	/** Recalculate Your Museums: the museum list in the session must come from this login's own upload. */
	public static boolean mayRecalcMuseums(Login accessLogin, Object museumMapLoginId) {
		if (accessLogin == null || museumMapLoginId == null) return false;
		return String.valueOf(accessLogin.getId()).equals(String.valueOf(museumMapLoginId));
	}

	public static boolean isAdminOnlyAction(String action) {
		return action != null && ADMIN_ONLY_ACTIONS.contains(action);
	}

	// Need the login's "upload specimens" permission (or admin).
	static final java.util.Set<String> SPECIMEN_UPLOAD_ACTIONS = java.util.Set.of(
			"specimenUpload", "taxonWorksUpload");

	/** Upload as another curator: admins, or a curator listed in this login's "upload as" setting. */
	public static boolean isAllowedUploadAs(Login accessLogin, String curateAs) {
		if (curateAs == null || curateAs.trim().isEmpty()) return true;
		if (accessLogin == null) return false;
		if (accessLogin.isAdmin()) return true;
		String target = curateAs.trim();
		if (target.equals(String.valueOf(accessLogin.getId()))) return true;
		String allowed = accessLogin.getUploadAs();
		if (allowed == null) return false;
		for (String id : allowed.split(",")) {
			if (target.equals(id.trim())) return true;
		}
		return false;
	}

	private static final Log s_antwebEventLog = LogFactory.getLog("antwebEventLog");

	static int MAXLENGTH = 80;

	//    private static boolean m_isInProcess = false;
	// volatile: read without a lock by the status pages and the image upload gate.
	private static volatile String s_isInProcess;

	// Due to architectural wankiness this is made public.  Easier than a redesign.
	// Called from SpecimenListUpload.reloadSpeciesList(), etc...
	//public static UploadFile s_uploadFile = null;

	boolean specimenPostProcess = false;

	public static boolean isInUploadProcess() {
		return s_isInProcess != null;
	}

	public static String getIsInUploadProcess() {
		return s_isInProcess;
	}

	public static void setIsInUploadProcess(String inProcess) {
		s_isInProcess = inProcess;
	}

	/** Sets the upload flag if no upload holds it. True if this caller now holds it (background jobs). */
	public static synchronized boolean claimUploadProcess(String inProcess) {
		if (s_isInProcess != null) return false;
		s_isInProcess = inProcess;
		return true;
	}

	/** Clears the upload flag if the caller still holds it. */
	public static synchronized void releaseUploadProcess(String inProcess) {
		if (inProcess != null && inProcess.equals(s_isInProcess)) s_isInProcess = null;
	}

	/**
	 * Run in the background (BackgroundJobs) since 2026-10-20: the page answers at once with a
	 * status page (jobStatus.do). A specimen upload waits in the queue while another upload or the
	 * nightly run is running; the AntCat import waits for another upload, and is refused during the
	 * nightly run, as before.
	 */
	static final java.util.Set<String> BACKGROUND_ACTIONS = java.util.Set.of(
			"specimenUpload", "taxonWorksUpload", "fetchAndReloadWorldants");

	public static boolean isBackgroundAction(String action) {
		return action != null && BACKGROUND_ACTIONS.contains(action);
	}

	public ActionForward execute(ActionMapping mapping, ActionForm form,
								 HttpServletRequest request, HttpServletResponse response) {

		UploadForm theForm = (UploadForm) form;
		boolean isBackground = isBackgroundAction(theForm.getAction());

		HttpSession session = request.getSession();
		//A.log("execute() up:" + theForm.isUp());

		String domainApp = AntwebProps.getDomainApp();
		String errorMessage = null;
		boolean httpPostOK = false;

		Login accessLogin = LoginMgr.getAccessLogin(request);
		Group accessGroup = GroupMgr.getAccessGroup(request);

		// Every action needs a login.
		if (accessLogin == null) {
			s_log.info("execute() login failure.  No accessGroup.");
			return mapping.findForward("goToLogin");
		}

		if (isRemovedAction(theForm.getAction())) {
			request.setAttribute("message", "Action not found.");
			return mapping.findForward("message");
		}

		// Uploads are for curators only (not the anonymous "Browse" login or self-registered
		// accounts), and the site-wide jobs are for admins only.
		{
			String requested = theForm.getAction();
			boolean isAdmin = LoginMgr.isAdmin(accessLogin);
			if (!isAdmin && !LoginMgr.isCurator(accessLogin)) {
				return mapping.findForward("goToLogin");
			}
			if (!isAdmin && requested != null && ADMIN_ONLY_ACTIONS.contains(requested)) {
				request.setAttribute("message", "Only administrators can run this upload action.");
				return mapping.findForward("message");
			}
			if (!isAdmin && requested != null && SPECIMEN_UPLOAD_ACTIONS.contains(requested)
					&& !Boolean.TRUE.equals(accessLogin.isUploadSpecimens())) {
				request.setAttribute("message", "This login does not have permission to upload specimens.");
				return mapping.findForward("message");
			}
		}

		// After the login and permission checks: anonymous visitors and logins without the right always
		// get the same answer, whether or not an upload or data job is running (2026-10-22).
		// A specimen upload is not refused while another upload or the nightly run is running:
		// it waits in the queue (BackgroundJobs). The AntCat import waits for another upload.
		if (!isBackground) {
			ActionForward f = Check.upload(request, mapping);
			if (f != null) return f;
		}
		if (!isBackground || "fetchAndReloadWorldants".equals(theForm.getAction())) {
			ActionForward g = Check.compute(request, mapping);
			if (g != null) return g;
		}


		//A.log("execute() AntwebMgr.init:" + AntwebMgr.isInitialized() + " postInit:" + AntwebMgr.isPostInitialized() + " curator:" + LoginMgr.getCurator("9716"));

		if (!AntwebMgr.isPostInitialized()) {
			ActionForward af = AntwebUtil.returnMessage("AntwebMgr not yet initialized.", mapping, request);
			//A.log("execute() postInit:" + AntwebMgr.isPostInitialized() + " af:" + af);
            return af;
		}

		// Nothing in upload.do runs on a GET (a GET is not checked for the form token).
		if (!HttpUtil.isPost(request)) {
			String requested = theForm.getAction();
			if (requested != null && CONFIRM_ON_GET.contains(requested)) {
				return PostOnly.confirm(request, mapping, "Run: " + requested);
			}
			String message = "action:" + Html.esc(requested) + " must be submitted through an http post.";
			s_log.info(message);
			request.setAttribute("message", message);
			return mapping.findForward("message");
		}

		if (isBackground) {
			BackgroundJobDb.setDataSource(getDataSource(request, "conPool"));
			if ("fetchAndReloadWorldants".equals(theForm.getAction())) {
				return submitWorldantsImport(accessLogin, request, mapping);
			}
			return submitSpecimenUpload(theForm, accessLogin, request, mapping);
		}

		if (UploadAction.isInUploadProcess()) {
			String message = "Server is currently in an Upload Process. Please try again in a little while.";
			if (LoginMgr.isAdmin(request)) message += " " + getIsInUploadProcess();
			request.setAttribute("message", message);
			return mapping.findForward("message");
		}

		String root = session.getServletContext().getRealPath("") + "/";
		Connection connection = null;
		String query;

		UploadDetails uploadDetails = new UploadDetails();
		//AntwebUpload antwebUpload = null;  Though further refactoring would be required
		// Each of the operations below should be an Object of superclass AntwebUpload.
		// For instance, ReloadSpeciesList, ...

		String action = theForm.getAction();
		s_log.warn("execute() action:" + action + " accessLogin:" + accessLogin);

		boolean runCountCrawls = true;

		Date worldantsFetchTime = null;
		String dbMethodName = DBUtil.getDbMethodName("UploadAction.execute()");
		// Taken only if free: a background upload (BackgroundJobs) may have started since the check above.
		final String processName = accessLogin.getName() + ":" + accessGroup.getName();
		if (!claimUploadProcess(processName)) {
			request.setAttribute("message", "Server is currently in an Upload Process. Please try again in a little while.");
			return mapping.findForward("message");
		}
		try {

			DataSource dataSource = getDataSource(request, "longConPool");
			connection = DBUtil.getConnection(dataSource, dbMethodName, HttpUtil.getTarget(request));
			connection.setAutoCommit(false);

			s_log.debug("execute() using longConPool:" + dataSource);

			LoginDb loginDb = new LoginDb(connection);

			AntwebMgr.incrementSpecimenUploadId(connection);


			String downTimeMessage = ServerDb.isDownTime(action, connection);
			if (!"".equals(downTimeMessage)) {
				request.setAttribute("message", downTimeMessage);
				return mapping.findForward("message");
			}


			//A.log("execute() action:" + action);
			if (action != null) {

				// specimenUpload, taxonWorksUpload and fetchAndReloadWorldants run in the background
				// (submitSpecimenUpload, submitWorldantsImport) and never reach this point.
				if (action.equals("museumCalc")) {
					Map<String, Integer> museumMap = (Map<String, Integer>) session.getAttribute("museumMap");

					// Curators recount only the museums of their own last upload (set in this session by
					// that upload); admins too, or any upload they made in this session.
					if (museumMap == null || !mayRecalcMuseums(accessLogin, session.getAttribute("museumMapLoginId"))) {
						request.setAttribute("message", "Upload a specimen file first: this recounts the museums of your last upload.");
						return mapping.findForward("message");
					}
					MuseumDb museumDb = new MuseumDb(connection);
					museumDb.populate(museumMap);

					request.setAttribute("message", "Museums recounted: " + Html.esc(String.join(", ", new java.util.TreeSet<>(museumMap.keySet()))));
					return mapping.findForward("message");

				} else {

					ActionForward forward = doAction(action, connection, request, mapping, accessLogin);
					if (forward != null) {
						s_log.info("execute() forward:" + forward);
						return forward;
					} else {
						request.setAttribute("message", "Action not found.");
						return mapping.findForward("message");
					}
				}
			}

			// Delete Project
			if (!theForm.getDeleteProject().equals("none")) {
				String deleteProject = theForm.getDeleteProject();

				// Only the administrators' form on projectMgr.do offers this.
				String refused = deleteProjectRefusal(accessLogin, deleteProject, ProjectMgr.getAllProjectsHash());
				if (refused != null) {
					s_log.warn("execute() deleteProject refused for login:" + accessLogin.getId());
					request.setAttribute("message", refused);
					return mapping.findForward("message");
				}

				ProjectDb projectDb = new ProjectDb(connection);
				projectDb.deleteSpeciesList(deleteProject);

				loginDb.refreshLogin(session);

				String message = "Species List deleted:" + Html.esc(deleteProject) + ".";
				s_log.info("execute() " + message);

				ProjectMgr.populate(connection, true);

				request.setAttribute("message", message);
				return mapping.findForward("message");
			}

			// The old "Create Project" step (createProject=..., a "click Here" page) was removed on
			// 2026-10-21; projects are created in the Project Manager. The field is ignored.

			if (!theForm.getEditSpeciesList().equals("none")) {
				String editSpeciesList = theForm.getEditSpeciesList();
				s_log.info("execute() editSpeciesList:" + editSpeciesList);
				request.setAttribute("mapSpeciesList1Name", editSpeciesList);
				request.setAttribute("isFreshen", "true");
				return mapping.findForward("speciesListTool");
			}

			// The developer "Download Species List" form was removed (Curate page cleanup); the
			// downloadSpeciesList field is ignored and ends as "Action not found." below.

			// "Upload a File to Folder" (theFile2) is no longer offered; refuse it.
			FormFile file2 = theForm.getTheFile2();
			if (file2 != null) {
				request.setAttribute("message", "Must select a file for uploading.");
				return mapping.findForward("message");
			}

			// Nothing else is done by upload.do ("Upload a Data File" and the world authority
			// file generation were removed in 2026-10: no form used them).
			if (action == null) {
				request.setAttribute("message", "Action not found.");
				return mapping.findForward("message");
			}

			uploadDetails.finish(accessLogin, request, connection);

			connection.commit();

			AntwebUtil.logCount();

			if (runCountCrawls) {
				uploadDetails.setOfferRunCountCrawlLink(true);
			}
			return mapping.findForward(uploadDetails.getForwardPage());

		} catch (SQLException | IOException e) {
			return handleException(e, action, connection, mapping, request);
		} catch (Exception e) {
			s_log.error("execute() e:" + e + " " + AntwebUtil.getShortStackTrace(e));
			return handleException(e, action, connection, mapping, request);
		} finally {
			String finishMessage = "Completion of the Upload Process.";
			if (errorMessage != null) finishMessage = errorMessage;
			s_log.warn("execute() finished action:" + action + " group:" + accessGroup.getAbbrev()
					+ " in " + AntwebUtil.getMinsPassed(uploadDetails.getStartTime()));
			releaseUploadProcess(processName);

			UploadMgr.populate(connection, true); // Repopulate UploadMgr.

			DBUtil.close(connection, this, dbMethodName);

			Profiler.profile("uploadAction", uploadDetails.getStartTime());
			Profiler.report();
		}
	}


	/** The status page of a background job (jobStatus.do), by redirect. */
	static ActionForward statusPage(int jobId, boolean already) {
		return new ActionForward("/jobStatus.do?id=" + jobId + (already ? "&already=1" : ""), true);
	}

	/**
	 * Specimen upload (AntWeb file or TaxonWorks archive): the quick checks here, then the file is
	 * saved and the upload runs in the background (SpecimenUploadJob). The answer is a redirect to
	 * the job's status page, well within Cloudflare's 100 seconds. While an upload of the same
	 * group, or by the same login, is waiting or running, no second one is started: the curator is
	 * sent to that upload's status page.
	 */
	private ActionForward submitSpecimenUpload(UploadForm theForm, Login accessLogin, HttpServletRequest request, ActionMapping mapping) {
		String action = theForm.getAction();
		String curateAs = theForm.getUploadAs();
		if (!isAllowedUploadAs(accessLogin, curateAs)) {
			// Refused, never uploaded to this login's own group instead: the upload replaces the group's specimens.
			s_log.warn("submitSpecimenUpload() uploadAs:" + curateAs + " not allowed for login:" + accessLogin.getId());
			request.setAttribute("message", "You may not upload as that curator. Nothing was uploaded. Ask an AntWeb administrator if you should.");
			return mapping.findForward("message");
		}
		Curator curator = LoginMgr.getCurator(curateAs);
		if (curator == null) curator = LoginMgr.getCurator(accessLogin);
		if (curator == null || curator.getGroup() == null) {
			request.setAttribute("message", "This login has no curator group to upload to.");
			return mapping.findForward("message");
		}
		Group group = curator.getGroup();

		FormFile specimenFile = theForm.getTheFile();
		if (specimenFile == null || specimenFile.getFileName() == null || specimenFile.getFileName().isEmpty()) {
			request.setAttribute("message", "Must choose a specimen file for upload.");
			return mapping.findForward("message");
		}
		String originalName = specimenFile.getFileName();
		boolean isTaxonWorks = "taxonWorksUpload".equals(action);

		BackgroundJobs.Job job = new BackgroundJobs.Job(BackgroundJobs.UPLOAD, action,
				(isTaxonWorks ? "TaxonWorks upload" : "Specimen upload") + " for " + group.getName() + " (" + originalName + ")",
				accessLogin.getId(), accessLogin.getName(), group.getId());
		BackgroundJobs.Job running = BackgroundJobs.get().findDuplicate(job);
		if (running != null) {
			s_log.warn("submitSpecimenUpload() login:" + accessLogin.getId() + " group:" + group.getId() + " already has job " + running.getId());
			return statusPage(running.getId(), true);
		}

		// Save the file (Struts deletes its own copy when this request ends). Only a zip is unpacked,
		// and only on the job's thread; here its list of entries is checked (SafeUnzip.check).
		File saved = null;
		try {
			File dir = new File(AntwebProps.getWorkingDir() + "jobs");
			if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("could not create " + dir);
			boolean isZip = originalName.toLowerCase().endsWith(".zip");
			saved = File.createTempFile("upload-group" + group.getId() + "-", isZip ? ".zip" : ".txt", dir);
			if (!Utility.copyFormFile(specimenFile, saved.getPath())) throw new IOException("could not save the uploaded file");
			if (isZip) {
				if (isTaxonWorks) SafeUnzip.check(saved, SafeUnzip.named("data.tsv"), "data.tsv file");
				else SafeUnzip.check(saved, SafeUnzip.ONE_TXT, ".txt file");
			}
		} catch (SafeUnzip.Refused r) {
			deleteSaved(saved);
			request.setAttribute("message", Html.esc(r.getMessage()));
			return mapping.findForward("message");
		} catch (IOException e) {
			s_log.error("submitSpecimenUpload() e:" + e);
			deleteSaved(saved);
			request.setAttribute("message", "The uploaded file could not be saved. Please try again.");
			return mapping.findForward("message");
		}

		SpecimenUploadJob work = new SpecimenUploadJob(action, saved, originalName, curator, accessLogin, request.getSession(),
				getDataSource(request, "longConPool"), request.getHeader("User-Agent"), "UTF-8");   // UTF-8 only (2026-10-28): an encoding sent with the form is ignored
		BackgroundJobs.Submitted submitted = BackgroundJobs.get().submit(job, work);
		if (submitted.job == null) {
			deleteSaved(saved);
			request.setAttribute("message", Html.esc(submitted.refused));
			return mapping.findForward("message");
		}
		if (!submitted.isNew) {
			deleteSaved(saved);
			return statusPage(submitted.job.getId(), true);
		}
		s_log.warn("submitSpecimenUpload() job " + submitted.job.getId() + " action:" + action + " group:" + group.getId()
				+ " login:" + accessLogin.getId() + " file:" + originalName + " bytes:" + saved.length());
		return statusPage(submitted.job.getId(), false);
	}

	private static void deleteSaved(File saved) {
		if (saved != null && saved.exists() && !saved.delete()) s_log.warn("deleteSaved() could not delete " + saved);
	}

	/** "Import AntCat names now" on the Curate page (admins): the nightly AntCat import, in the background. */
	private ActionForward submitWorldantsImport(Login accessLogin, HttpServletRequest request, ActionMapping mapping) {
		BackgroundJobs.Job job = new BackgroundJobs.Job(BackgroundJobs.DATA, "fetchAndReloadWorldants", "Import AntCat names now",
				accessLogin.getId(), accessLogin.getName(), 0);
		final DataSource dataSource = getDataSource(request, "longConPool");
		BackgroundJobs.Submitted submitted = BackgroundJobs.get().submit(job, j -> runWorldantsImport(dataSource, accessLogin));
		if (submitted.job == null) {
			request.setAttribute("message", Html.esc(submitted.refused));
			return mapping.findForward("message");
		}
		return statusPage(submitted.job.getId(), !submitted.isNew);
	}

	/** The work of "Import AntCat names now" (was the fetchAndReloadWorldants branch of execute()). */
	static BackgroundJobs.Outcome runWorldantsImport(DataSource dataSource, Login accessLogin) throws Exception {
		String processName = accessLogin.getName() + ":fetchAndReloadWorldants";
		while (!claimUploadProcess(processName)) {
			BackgroundJobs.step("Waiting for an upload to finish. The import starts right after it.");
			Thread.sleep(SpecimenUploadJob.CLAIM_WAIT_MS);
		}
		BackgroundJobs.step("Downloading the AntCat export and loading the names (several minutes).");
		Connection connection = null;
		String dbMethodName = DBUtil.getDbMethodName("UploadAction.runWorldantsImport()");
		try {
			connection = DBUtil.getConnection(dataSource, dbMethodName);
			connection.setAutoCommit(false);
			UploadDetails uploadDetails = new SpeciesListUploader(connection).worldantsFetchAndReload();
			String message = uploadDetails.getMessage();
			if (message != null && !"success".equals(message)) {
				DBUtil.rollback(connection);
				// The alert is written after the rollback, in its own committed statement.
				try { connection.setAutoCommit(true); } catch (SQLException e) { s_log.warn("runWorldantsImport() autocommit e:" + e); }
				AdminAlertMgr.add("AntCat import failed: " + message, connection);
				return BackgroundJobs.Outcome.failed(message);
			}
			BackgroundJobs.step("Saving a statistics snapshot and writing the report.");
			new StatisticsDb(connection).populateStatistics("fetchAndReloadWorldants", accessLogin.getId(),
					uploadDetails.getExecTime() == null ? "N/A" : uploadDetails.getExecTime(), AntwebProps.getDocRoot());
			uploadDetails.finish(accessLogin, null, connection);
			connection.commit();
			return BackgroundJobs.Outcome.ok("AntCat names imported in " + Html.esc(String.valueOf(uploadDetails.getExecTime())) + ".",
					SpecimenUploadJob.reportUrl(uploadDetails), "Import report");
		} catch (Exception e) {
			int caseNumber = AntwebUtil.getCaseNumber();
			s_log.error("runWorldantsImport() case:" + caseNumber + " e:" + e);
			DBUtil.rollback(connection);
			return BackgroundJobs.Outcome.failed("Error. Case number:" + caseNumber + ". No changes made. e:" + Html.esc(e.toString()));
		} finally {
			releaseUploadProcess(processName);
			UploadMgr.populate(connection, true);
			DBUtil.close(connection, UploadAction.class, dbMethodName);
		}
	}

	/**
	 * Emails the uploader that the specimen upload is complete or did not complete (UploadNotice).
	 * failureMessage: what the upload page used to show when it did not complete.
	 */
	static void sendUploadNotice(UploadNotice.Facts notice, Group noticeGroup, UploadDetails uploadDetails,
										 Login accessLogin, Object failureMessage) {
		try {
			notice.when = new Date();
			String reportPath = uploadDetails.getLogDirFile();
			boolean hasReport = reportPath != null && new File(LogMgr.getWebLogRoot() + reportPath).isFile();
			if (hasReport) notice.reportUrl = AntwebProps.getDomainApp() + "/web/log/" + reportPath;
			if (notice.complete) {
				notice.specimensLoaded = uploadDetails.getInsertedSpecimenCount();
				notice.redFlagged = uploadDetails.getMessageMgr().getRedFlagCount();
			} else {
				notice.failure = UploadNotice.failureText(failureMessage);
				notice.uploadPageUrl = AntwebProps.getDomainApp() + "/curate.do";
			}
			UploadNotice.send(accessLogin, accessLogin != null, noticeGroup, notice);
		} catch (Exception e) {
			s_log.error("sendUploadNotice() e:" + e);
		}
	}

	private ActionForward handleException(Exception e, String action, Connection connection, ActionMapping mapping, HttpServletRequest request) {

		int caseNumber = AntwebUtil.getCaseNumber();
		String errorMessage = null;

		if (errorMessage == null) {
			errorMessage = "Error. Case number:" + caseNumber + ". No changes made. e:" + Html.esc(e.toString());
	    }

		s_log.error("execute() errorMessage:" + errorMessage + " action:" + action + " e:" + e);
		DBUtil.rollback(connection);
		request.setAttribute("message", errorMessage);
		return mapping.findForward("message");
	}

	/** The database work after a specimen upload. Also used by the nightly TaxonWorks job (no web request). */
	public static void postProcessSpecimenUpload(Connection connection, Curator curator, UploadDetails uploadDetails) throws IOException, SQLException {
		Group group = curator.getGroup();

		SpecimenDb specimenDb = new SpecimenDb(connection);
		specimenDb.updateSpecimenStatus(group.getId());
		s_log.debug("execute() updateSpecimenStatus:");

		GroupDb groupDb = new GroupDb(connection);
		groupDb.updateUploadSpecimens(group);
		s_log.debug("specimenPostProcess() updateUploadSpecimens");

		ObjectMapDb objectMapDb = new ObjectMapDb(connection);
		objectMapDb.genGroupObjectMap(group);
		s_log.debug("specimenPostProcess() genGroupObjectMap");

		// The upload's row in the upload table: throws when it cannot be written (the upload then rolls back).
		SpecimenUploadDb uploadDb = new SpecimenUploadDb(connection);
		uploadDb.recordUpload(curator, uploadDetails);
		uploadDb.updateGroup(group);
		s_log.debug("specimenPostProcess() updateUpload");

		if (!AntwebProps.isDevModeSkipping()) {
			String execTime = uploadDetails.getExecTime() == null ? "N/A" : uploadDetails.getExecTime();
			new StatisticsDb(connection).populateStatistics(uploadDetails.getAction(), curator.getId(), execTime, AntwebProps.getDocRoot());
		} else {
			A.log("execTime:" + uploadDetails.getExecTime());
			s_log.warn("execute() DEV MODE SKIPPING runStatistics");
		}

		// Would like to, but it takes a 3min for CAS data. Too expensive?
		//specimenDb.calcCaste(groupId);
	}

	public static void specimenPostProcessGlobal(Connection connection) throws SQLException {
		A.log("start specimenPostProcessGlobal");
		new GeolocaleDb(connection).calcEndemic();

		new TaxonDb(connection).removeIndetWithoutSpecimen();
		A.log("end specimenPostProcessGlobal");
	}

	private ActionForward doAction(String action, Connection connection, HttpServletRequest request
			, ActionMapping mapping, Login accessLogin)
			throws SQLException, IOException {


		int loginId = 0;
		if (accessLogin != null) loginId = accessLogin.getId();

		if (action.equals("runStatistics")) {
			runStatistics(action, connection, request, loginId);
			//return (mapping.findForward("statisticsStr"));
			return mapping.findForward("statisticsDo");
		}
		if (action.equals("runCountCrawls")) {
			s_log.error("doAction() Run Count Crawl should be done through UtilData.do.");
			//runCountCrawls(connection);
			//return (mapping.findForward("success"));
		}

		if (action.equals("genRecentDescEdits")) {
			try {
				AntwebFunctions.genRecentDescEdits(connection);
				return mapping.findForward("success");
			} catch (IOException e) {
				String message = e.toString();
				s_log.error("doAction() " + message);
				request.setAttribute("message", message);
				return mapping.findForward("message");
			}
		}
		s_log.error("doAction() action not found:" + action);
		return null;
	}


	public static final String DELETE_PROJECT_ADMIN_ONLY = "Only an AntWeb administrator may delete a species list.";
	public static final String DELETE_PROJECT_NOT_FOUND = "Species list not found.";

	/** Null if accessLogin may delete the species list projectName (an existing one, admins only), otherwise why not. */
	public static String deleteProjectRefusal(Login accessLogin, String projectName, Map<String, ?> existing) {
		if (accessLogin == null || !Boolean.TRUE.equals(accessLogin.isAdmin())) return DELETE_PROJECT_ADMIN_ONLY;
		if (projectName == null || existing == null || !existing.containsKey(projectName)) return DELETE_PROJECT_NOT_FOUND;
		return null;
	}

    public Project getProjectForDirectory(Login login, String directory, Connection connection) {
      Project returnVal = null;
      String thisProjName = "";
      ArrayList<SpeciesListable> speciesLists = login.getProjects();
      for (SpeciesListable speciesList : speciesLists) {
         Project project = ProjectMgr.getProject(speciesList.getName());
         Project thisProj = new ProjectDb(connection).getProject(project.getName());
         s_log.debug("getProjectForDirectory() proj:" + thisProj + " name:" + project.getName() + " root:" + thisProj.getRoot() + " dir:" + directory);
         if (thisProj != null && thisProj.getRoot() != null && directory.contains(thisProj.getRoot())) {
             returnVal = thisProj;
             break;
         }
      }
      if (returnVal == null) s_log.info("getProjectForDirectory() returnVal is null for directory:" + directory);
      return returnVal;
    }

    private void runStatistics(String action, Connection connection, HttpServletRequest request, int loginId)
      throws SQLException, IOException {
      runStatistics(action, connection, request, loginId, null);
    }

    private void runStatistics(String action, Connection connection, HttpServletRequest request, int loginId, String execTime)
     throws SQLException, IOException {

        if (false && AntwebProps.isDevOrStageMode()) {
          s_log.warn("runStatistics() not executing because of test mode.");
          return;
        }

        s_log.info("runStatistics()");

        //String docBase = request.getRealPath("/");
		String docBase = AntwebProps.getDocRoot();

		if (execTime == null) execTime = "N/A";
		/*
        String execTime = "N/A";
        if (execTime !)
        if (uploadDetails != null) execTime = uploadDetails.getExecTime();
        */

        new StatisticsDb(connection).populateStatistics(action, loginId, execTime, docBase);
    }
}
