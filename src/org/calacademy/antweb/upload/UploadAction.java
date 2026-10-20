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
	private static String s_isInProcess;

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

	public ActionForward execute(ActionMapping mapping, ActionForm form,
								 HttpServletRequest request, HttpServletResponse response) {

		ActionForward f = Check.upload(request, mapping);
		if (f != null) return f;
		ActionForward g = Check.compute(request, mapping);
		if (g != null) return g;

		HttpSession session = request.getSession();
		UploadForm theForm = (UploadForm) form;
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
		// Set once a specimen file upload starts; the uploader is emailed when it ends (see the finally block).
		UploadNotice.Facts notice = null;
		Group noticeGroup = null;
		try {
			setIsInUploadProcess(accessLogin.getName() + ":" + accessGroup.getName());

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

				if ("specimenUpload".equals(action) || "taxonWorksUpload".equals(action)) {

					String curateAs = theForm.getUploadAs();
					if (!isAllowedUploadAs(accessLogin, curateAs)) {
						s_log.warn("execute() uploadAs:" + curateAs + " not allowed for login:" + accessLogin.getId());
						curateAs = null;
					}
					Curator curator = LoginMgr.getCurator(curateAs);
					if (curator == null) curator = LoginMgr.getCurator(accessLogin);
					A.log("execute action:" + action + " curateAs:" + curateAs + " curator:" + curator + " login:" + accessLogin);

					boolean abort = AntwebProps.isDevMode();

					FormFile specimenFile = theForm.getTheFile();
					if (specimenFile != null && !specimenFile.getFileName().equals("")) {

						notice = new UploadNotice.Facts();
						notice.fileName = specimenFile.getFileName();
						notice.fileType = "taxonWorksUpload".equals(action) ? "TaxonWorks file" : "AntWeb specimen file";
						noticeGroup = curator != null ? curator.getGroup() : accessGroup;

						if ("specimenUpload".equals(action)) {
							String theFileName = accessGroup.getAbbrev() + "SpecimenList";
							A.log("specimenUpload specimenFile:" + specimenFile.getFileName() + " fileName:" + theFileName);

							s_log.info("execute() type:" + theForm.getSpecimenUploadType() + " encoding:" + theForm.getEncoding());

							if (abort) AntwebUtil.returnMessage("abort Antweb specimen upload", mapping, request);

							uploadDetails = new SpecimenUploader(connection).uploadSpecimenFile(theForm, curator, request.getHeader("User-Agent"), theForm.getEncoding());

						} else if ("taxonWorksUpload".equals(action)) {
							String theFileName = accessGroup.getAbbrev() + "TWSpecimenList";
							A.log("specimenFile:" + specimenFile + " fileName:" + theFileName);

							if (abort) AntwebUtil.returnMessage("abort TaxonWorks specimen upload", mapping, request);

							uploadDetails = new TaxonWorksUploader(connection).uploadSpecimenFile(theForm, curator, request.getHeader("User-Agent"), theForm.getEncoding());

						}

						ActionForward af = uploadDetails.returnForward(mapping, request);
						if (af != null) return af;

						specimenPostProcess(request, connection, curator, uploadDetails);
						specimenPostProcessGlobal(connection);

						runCountCrawls = true;

					} else {
						request.setAttribute("message", "Must choosen specimen file for upload");
						return uploadDetails.getErrorForward(mapping);
					}


					//if (!curator.equals(accessLogin)) uploadDetails.setCurator(accessLogin);

				} else if (action.equals("fetchAndReloadWorldants")) {
					SpeciesListUploader speciesListUploader = new SpeciesListUploader(connection);
					uploadDetails = speciesListUploader.worldantsFetchAndReload();

					String message = uploadDetails.getMessage();
					if (message != null && !"success".equals(message)) {
						AdminAlertMgr.add(message, connection);
						request.setAttribute("message", message);
						return mapping.findForward("message");
					}

					//runCountCrawls(connection);

					runStatistics(action, connection, request, accessLogin.getId(), uploadDetails.getExecTime());

					uploadDetails.finish(accessLogin, request, connection);

				} else if (action.equals("museumCalc")) {
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

			// Create Project
			if (!theForm.getCreateProject().equals("none")) {
				String createProject = theForm.getCreateProject();

				if (!Project.isProjectName(createProject)) {
					request.setAttribute("message", "Project name must be lowercased, have no spaces, and end with 'ants'");
					return mapping.findForward("message");
				}

				String url = AntwebProps.getDomainApp() + "/editProject.do?projectName=" + Html.url(createProject);
				String anchorTag = "<a href='" + Html.escAttr(url) + "'>Here</a>";
				String message = "To create project <b>" + Html.esc(createProject) + "</b> click: <b>" + anchorTag + "</b>. <br><br>&nbsp;&nbsp;&nbsp;Once you click 'Done', the project will be created.";
				//s_log.into("execute() " + message);

				request.setAttribute("message", message);
				return mapping.findForward("message");
			}

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
			if (notice != null) {
				notice.complete = true;
				notice.uploadId = AntwebMgr.getNextSpecimenUploadId();
			}

			AntwebUtil.logCount();

			if (runCountCrawls) {
				uploadDetails.setOfferRunCountCrawlLink(true);
			}
			return mapping.findForward(uploadDetails.getForwardPage());

		} catch (TestException | SQLException | IOException | AntwebException | RESyntaxException e) {
			return handleException(e, action, connection, mapping, request);
		} catch (Exception e) {
			s_log.error("execute() e:" + e + " " + AntwebUtil.getShortStackTrace(e));
			return handleException(e, action, connection, mapping, request);
		} finally {
			String finishMessage = "Completion of the Upload Process.";
			if (errorMessage != null) finishMessage = errorMessage;
			s_log.warn("execute() finished action:" + action + " group:" + accessGroup.getAbbrev()
					+ " in " + AntwebUtil.getMinsPassed(uploadDetails.getStartTime()));
			setIsInUploadProcess(null);

			UploadMgr.populate(connection, true); // Repopulate UploadMgr.

			DBUtil.close(connection, this, dbMethodName);

			if (notice != null) sendUploadNotice(notice, noticeGroup, uploadDetails, accessLogin, request);

			Profiler.profile("uploadAction", uploadDetails.getStartTime());
			Profiler.report();
		}
	}


	/** Emails the uploader that the specimen upload is complete or did not complete (UploadNotice). */
	private static void sendUploadNotice(UploadNotice.Facts notice, Group noticeGroup, UploadDetails uploadDetails,
										 Login accessLogin, HttpServletRequest request) {
		try {
			notice.when = new Date();
			String reportPath = uploadDetails.getLogDirFile();
			boolean hasReport = reportPath != null && new File(LogMgr.getWebLogRoot() + reportPath).isFile();
			if (hasReport) notice.reportUrl = AntwebProps.getDomainApp() + "/web/log/" + reportPath;
			if (notice.complete) {
				notice.specimensLoaded = uploadDetails.getInsertedSpecimenCount();
				notice.redFlagged = uploadDetails.getMessageMgr().getRedFlagCount();
			} else {
				notice.failure = UploadNotice.failureText(request.getAttribute("message"));
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

	private void specimenPostProcess(HttpServletRequest request, Connection connection, Curator curator, UploadDetails uploadDetails) throws IOException, SQLException {
		postProcessSpecimenUpload(connection, curator, uploadDetails);

		//A.log("execute() populate Museum map:" + uploadDetails.getMuseumMap());
		// We will hold this for a separate curator driven museum calculation
		HttpSession session = request.getSession();
		try {
			session.setAttribute("museumMap", uploadDetails.getMuseumMap());
			Login uploader = LoginMgr.getAccessLogin(request);
			session.setAttribute("museumMapLoginId", uploader == null ? null : uploader.getId());
		} catch (IllegalStateException e) {
			s_log.info("execute() handled:" + e);
		}
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

		SpecimenUploadDb uploadDb = new SpecimenUploadDb(connection);
		uploadDb.updateUpload(curator, uploadDetails);
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
