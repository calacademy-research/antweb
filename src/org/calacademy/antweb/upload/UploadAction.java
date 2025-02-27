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
import org.calacademy.antweb.data.Adm1LoadAction;
import org.calacademy.antweb.data.AntWikiData;
import org.calacademy.antweb.data.AntWikiDataAction;
import org.calacademy.antweb.home.*;
import org.calacademy.antweb.util.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
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

public class UploadAction extends Action {

	private static final Log s_log = LogFactory.getLog(UploadAction.class);

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

		if (accessLogin == null && !"genAll".equals(theForm.getAction())) {
			s_log.info("execute() login failure.  No accessGroup.");
			return mapping.findForward("goToLogin");
		}


		//A.log("execute() AntwebMgr.init:" + AntwebMgr.isInitialized() + " postInit:" + AntwebMgr.isPostInitialized() + " curator:" + LoginMgr.getCurator("9716"));

		if (!AntwebMgr.isPostInitialized()) {
			ActionForward af = AntwebUtil.returnMessage("AntwebMgr not yet initialized.", mapping, request);
			//A.log("execute() postInit:" + AntwebMgr.isPostInitialized() + " af:" + af);
            return af;
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
		try {
			setIsInUploadProcess(accessLogin.getName() + ":" + accessGroup.getName());

			DataSource dataSource = getDataSource(request, "longConPool");
			connection = DBUtil.getConnection(dataSource, dbMethodName, HttpUtil.getTarget(request));
			connection.setAutoCommit(false);

			s_log.debug("execute() using longConPool:" + dataSource);

			LoginDb loginDb = new LoginDb(connection);

			AntwebMgr.incrementSpecimenUploadId(connection);


			if (!"toggleDownTime".equals(action)) {
				String downTimeMessage = ServerDb.isDownTime(action, connection);
				if (!"".equals(downTimeMessage)) {
					request.setAttribute("message", downTimeMessage);
					return mapping.findForward("message");
				}
			}

			//if (AntwebProps.isDevOrStageMode()) httpPostOK = true;
			if (!(HttpUtil.isPost(request) || httpPostOK) && !(
					"runStatistics".equals(action)
							|| "specimenTest".equals(action)
							|| "reloadSpecimenList".equals(action)
							|| "genRecentDescEdits".equals(action)
			)) {
				String message = "action:" + action + " must be submitted through an http post.";
				s_log.info(message);
				request.setAttribute("message", message);
				return mapping.findForward("message");
			}

			//A.log("execute() action:" + action);
			if (action != null) {

				if ("specimenUpload".equals(action) || "taxonWorksUpload".equals(action) || "GBIFUpload".equals(action)) {

					String curateAs = theForm.getUploadAs();
					Curator curator = LoginMgr.getCurator(curateAs);
					if (curator == null) curator = LoginMgr.getCurator(accessLogin);
					A.log("execute action:" + action + " curateAs:" + curateAs + " curator:" + curator + " login:" + accessLogin);

					boolean abort = AntwebProps.isDevMode();

					FormFile specimenFile = theForm.getTheFile();
					if (specimenFile != null && !specimenFile.getFileName().equals("")) {

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

						} else if ("GBIFUpload".equals(action)) {
							String theFileName = accessGroup.getAbbrev() + "GBIFSpecimenList";
							A.log("GBIFUpload specimenFile:" + specimenFile.getFileName() + " fileName:" + theFileName);

							if (abort) AntwebUtil.returnMessage("abort GBIF specimen upload", mapping, request);

							uploadDetails = new GBIFUploader(connection).uploadSpecimenFile(theForm, curator, request.getHeader("User-Agent"), theForm.getEncoding());
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

				} else if (action.equals("uploadSpeciesList")) {
					// This functionality should probably be inside of SpeciesListUploader.java

					try {
						TaxonMgr.setIsInWorldants(true);

						//s_log.debug("setIsInWorldants(true) + root:" + AntwebProps.getDocRoot());

						// Do we need to create /data/antweb/web/workingdir if it does not exist?
						// Worldants should not be hardcoded. Get from file... (2 times)
						UploadFile uploadFile = new UploadFile(AntwebProps.getDocRoot() + "web/workingdir/", "worldants.txt", request.getHeader("User-Agent"), null);
						uploadFile.setRoot(AntwebProps.getDocRoot());

						Date startTime = new Date();

						uploadDetails = new SpeciesListUploader(connection).uploadWorldants(theForm.getTheFile(), uploadFile, accessGroup);

						uploadDetails.setStartTime(startTime);
						if (uploadDetails.isErrorForward()) {
							request.setAttribute("message", uploadDetails.getMessage());
							return uploadDetails.getErrorForward(mapping);
						}

						if ("on".equals(theForm.getRecrawl())) {
							runCountCrawls = true;
						}

						runStatistics(action, connection, request, accessLogin.getId(), uploadDetails.getExecTime());
						//runStatistics(action, dataSource, request, accessLogin.getId(), uploadDetails);

						s_log.debug("uploadSpeciesList uploadDetails:" + uploadDetails);
					} finally {
						TaxonMgr.setIsInWorldants(false);
						s_log.debug("setIsInWorldants(false)");
					}
                /*
 			  } else if (action.equals("reloadSpeciesLists")) {
				util.deleteFile(AntwebProps.getWebDir() + "log/passBoltonSpeciesCheck.txt");
				SpeciesListUpload speciesListUpload = new SpeciesListUpload(connection);
				speciesListUpload.reloadSpeciesLists();
				uploadDetails = speciesListUpload.getUploadDetails();

				logFileName += "allAuthFiles" + UploadDetails.appendLogExt;
				runStatistics(action, connection, request, accessLogin.getId(), uploadDetails);
				httpPostOK = true;
                runCountCrawls = true;

				//runCountCrawls(connection);
*/

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

				} else if (action.equals("removeSpecimenList")) {

					SpecimenUploadDb specimenUploadDb = new SpecimenUploadDb(connection);
					specimenUploadDb.dropSpecimens(accessGroup);

				} else if (action.equals("reloadSpecimenList")) {

					/*
					AccessGroup is the group that is logged in.
					SubmitGroup is the effective group. Actions are taken as... can be passed in with the form.
					Not all actions consider the submitGroup (currently).
					*/
					Curator submitCurator = LoginMgr.getCurator(accessLogin.getId());
					int groupId = theForm.getGroupId();
					if (groupId > 0) {
						Group group = GroupMgr.getGroup(theForm.getGroupId());
						if (group != null) {
     						int groupAdminId = group.getAdminLogin().getId();
							submitCurator = LoginMgr.getCurator(groupAdminId);
						}
					}

					String abbrev = submitCurator.getGroup().getAbbrev();

					s_log.info("action:" + action + " submitGroup:" + submitCurator.getGroup());

					// Happening in Uploader now.
					// LogMgr.logQuery(connection, "Before specimen upload Proj_taxon worldants counts", "select project_name, source, count(*) from proj_taxon where source = 'worldants' group by project_name, source");
					// LogMgr.logAntQuery(connection, "projectTaxaCountByProjectRank", "Before specimen upload Proj_taxon worldants counts");

					String theFileName = abbrev + "SpecimenList";
					action = "reload:" + theFileName;
					// logFileName += theFileName + UploadDetails.getLogExt();
					String formFileName = new Date() + "reloadSpecimen" + submitCurator.getGroup() + ".txt";

					boolean isUpload = false; // It is a reload.
					uploadDetails = new SpecimenUploader(connection).uploadSpecimenFile(theFileName, formFileName
							, submitCurator, request.getHeader("User-Agent"), theForm.getEncoding(), isUpload);

					if (AntwebProps.isDevMode()) {
						s_log.debug("DEV SKIPPING, post specimen processing aborted.");
						uploadDetails.finish(accessLogin, request, connection);
						return uploadDetails.findForward(mapping, request);
					}

					ActionForward af = uploadDetails.returnForward(mapping, request);
					if (af != null) return af;

					//LogMgr.logQuery(connection, "After specimen upload Proj_taxon worldants counts", "select project_name, source, count(*) from proj_taxon where source = 'worldants' group by project_name, source");
					// LogMgr.logAntQuery(connection, "projectTaxaCountByProjectRank", "After specimen upload Proj_taxon worldants counts");

					specimenPostProcess(request, connection, submitCurator, uploadDetails);
					specimenPostProcessGlobal(connection);

					httpPostOK = true;

					uploadDetails.finish(accessLogin, request, connection);

					ActionForward forward = uploadDetails.findForward(mapping, request);
					s_log.debug("execute() action:" + action + " uploadDetails:" + uploadDetails + " forward:" + forward);
					if (forward != null) return forward;

				} else if (action.equals("specimenTest")) {
					// This will reload the last specimen upload of the login listed below.
					// First log in as them and upload as them, then subsequents to
					// http://localhost/antweb/upload.do?action=specimenTest from any admin
					// will reload.
					//String testAccessLoginName = "cmoreau@fieldmuseum.org";
					//String testAccessLoginName = "psward";
					//String testAccessLoginName = "SShattuck";
					String testAccessLoginName = "testLogin";
					Login testLogin = loginDb.getLoginByName(testAccessLoginName);
					Curator testCurator = LoginMgr.getCurator(testLogin);
					accessGroup = testLogin.getGroup();

					// Fetched from AppResources site.inputfilehome=/Users/mark/dev/calAcademy/workingdir/
					// In this test case, no file is uploaded, but the server side workingdir copy is used. (specimen21.txt).
					// UploadFile - backup() /antweb/workingdir/specimen21.txt to /antweb/web/upload/20131112-21:28:53-specimen21.txt
					String formFileName = new Date() + "specimenTest" + accessGroup + ".txt";

					//logFileName += accessGroup.getAbbrev() + "SpecimenTest" + UploadDetails.getLogExt();
					s_log.info("execute() specimenTest");
					boolean isUpload = false; // This is a reload
					uploadDetails = new SpecimenUploader(connection).uploadSpecimenFile(accessGroup.getAbbrev() + "specimenTest", formFileName
							, testCurator, request.getHeader("User-Agent"), theForm.getEncoding(), isUpload);

					ActionForward af = uploadDetails.returnForward(mapping, request);
					if (af != null) return af;

					specimenPostProcess(request, connection, testCurator, uploadDetails);
					specimenPostProcessGlobal(connection);

					return uploadDetails.findForward(mapping, request);

					//  Yes it should, unless there are errors.  s_log.info("execute() specimenTest.  This should not happen");
				} else if ("allSpecimenFiles".equals(action)) {
					/*
					// All specimen*.txt files should be downloaded to the machine from the production site, prior to this command.
					cd /antweb/workingdir/
					scp mjohnson@antweb-prod:/antweb/workingdir/specimen*.txt .
					*/
					//logFileName += "allSpecimenFiles" + UploadDetails.getLogExt();
					for (int i = 0; i < 100; ++i) {
						String formFileName = AntwebProps.getWorkingDir() + "specimen" + i + ".txt";

						if (new File(formFileName).exists()) {

							s_log.info("-+-+-+-+-+-+-+-+-+ reloading:" + formFileName + "+-+-+-+-+-+-+-+-+-+-+-+");

							boolean beAbbreviated = false;
							if (AntwebProps.isDevMode()) beAbbreviated = true;
							if (!(beAbbreviated && (i == 1 || i == 2 || i == 25))) {  // Testing.  Do not run the big ones...

								Group submitAsGroup = GroupMgr.getGroup(i);
								Login submitAsLogin = submitAsGroup.getAdminLogin();
								Curator submitAsCurator = LoginMgr.getCurator(submitAsLogin.getId());

								if (submitAsGroup.getAdminLoginId() == 0) {
									s_log.info("allSpecimenFiles accessLoginId == 0.  Update database.");
									// Perhaps the most recent database snapshot has not been loaded?
								}

								// Testing required.
								DBUtil.close(connection, this, dbMethodName);
								dataSource = getDataSource(request, "longConPool");
								connection = DBUtil.getConnection(dataSource, dbMethodName, HttpUtil.getTarget(request));  // must keep the same name to close at the end.
								connection.setAutoCommit(false);

								//s_log.info("formFileName:" + formFileName + " accessGroup:" + accessGroup + " logFileName:" + logFileName);

								boolean isUpload = false; // This is a reload
								uploadDetails = new SpecimenUploader(connection).uploadSpecimenFile("allSpecimenFiles", formFileName
										, submitAsCurator, request.getHeader("User-Agent"), theForm.getEncoding(), isUpload);

								ActionForward af = uploadDetails.returnForward(mapping, request);
								if (af != null) return af;

								specimenPostProcess(request, connection, submitAsCurator, uploadDetails);

								connection.commit();
							} else {
								//s_log.info("execute() not exists:" + formFileName);
							}
						}
					}

					specimenPostProcessGlobal(connection);

					httpPostOK = true;
				} else if (action.equals("speciesTest")) {
					//String message = "Species List test not yet implemented.";

					SpeciesListUpload speciesListUpload = new SpeciesListUpload(connection);
					uploadDetails = speciesListUpload.importSpeciesList("worldants", "/Users/mark/dev/calAcademy/workingdir/worldants.txt"
							, "worldants.txt", "UTF-8", accessGroup.getId());
					if (uploadDetails.getErrorForward(mapping) != null) return uploadDetails.getErrorForward(mapping);

				} else if (action.equals("museumCalc")) {
					Map<String, Integer> museumMap = (Map<String, Integer>) session.getAttribute("museumMap");

					if (museumMap == null) {
						String message = "Museum Map not found in session";
						request.setAttribute("message", message);
						return mapping.findForward("message");
					}
					MuseumDb museumDb = new MuseumDb(connection);
					museumDb.populate(museumMap);

					request.setAttribute("message", "Museum recalculation:" + museumMap.keySet());
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

				ProjectDb projectDb = new ProjectDb(connection);
				projectDb.deleteSpeciesList(deleteProject);

				loginDb.refreshLogin(session);

				String message = "Species List deleted:" + deleteProject + ".";
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

				String url = AntwebProps.getDomainApp() + "/editProject.do?projectName=" + createProject;
				String anchorTag = "<a href='" + url + "'>Here</a>";
				String message = "To create project <b>" + createProject + "</b> click: <b>" + anchorTag + "</b>. <br><br>&nbsp;&nbsp;&nbsp;Once you click 'Done', the project will be created.";
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

			if (!HttpUtil.isPost(request) && !httpPostOK) {
				String message = "Must use Http Post.  Group:" + accessGroup.getId();
				s_log.info("execute() " + message);
				request.setAttribute("message", message);
				return mapping.findForward("message");
			}

			if (!theForm.getDownloadSpeciesList().equals("none")) {
				String downloadSpeciesList = theForm.getDownloadSpeciesList();
				//String dir = downloadSpeciesList.substring(0, downloadSpeciesList.indexOf("ants"));
				//if (dir.equals("mad")) dir = "madagascar";
				//if (dir.equals("cal")) dir = "california";
				String url = domainApp + "/speciesListDownload.do?projectName=" + downloadSpeciesList;
				//String url = domainApp + "/web/speciesList/" + dir + "/" + downloadSpeciesList + UploadFile.getSpeciesListTail();  //"_project.txt";
				String message = "<b>'Right-click' and 'Save Link As' to download:</b> <a href=\"" + url + "\">" + url + "</a>";
				s_log.info(message);

				request.setAttribute("message", message);
				return mapping.findForward("message");
			}

			// Upload a File to Folder:
			FormFile file2 = theForm.getTheFile2();
			//A.log("upload file:" + file2);
			if (file2 != null) {
				if (!file2.getFileName().equals("")) {


					request.setAttribute("message", "Must select a file for uploading.");
					return mapping.findForward("message");

/*
				action = "upload:" + file2.getFileName();

				s_log.info("file upload:" + action);
				uploadDetails = uploadFileToFolder(theForm, request, accessLogin);

                ActionForward forward = uploadDetails.findForward(mapping, request);
                A.log("upload a file forward:" + forward);
                return forward;
*/
				} else {
					request.setAttribute("message", "Must select a file for uploading.");
					return mapping.findForward("message");
				}
			}

			// Upload a Data File:
			FormFile testFile = theForm.getTestFile();
			//A.log("upload a data file:" + testFile);
			if (testFile != null) {
				if (!testFile.getFileName().equals("")) {
					//action = "upload:" + testFile.getFileName();
					//s_log.info("execute() Upload a test file logFileName:" + logFileName);
					//s_log.info("execute() testFile:" + testFile.getFileName());

					uploadDetails = uploadDataFile(theForm, request, mapping, connection);
                    if (uploadDetails != null) {
						return uploadDetails.findForward(mapping, request);
					} else {
						request.setAttribute("message", "Upload unsuccessful.");
						return mapping.findForward("message");
					}
				} else {
                    String message = getUploadDataFileDoc(accessLogin);
					request.setAttribute("message", message);
					return mapping.findForward("message");
				}
			}

			if (theForm.getSuccessKey() != null && theForm.getSuccessKey().equals("worldAuthorityFiles")) {
				s_log.debug("execute() worldauth successKey:" + theForm.getSuccessKey());
				worldAuthGen(request);
				uploadDetails.setForwardPage(theForm.getSuccessKey());
			}

			uploadDetails.finish(accessLogin, request, connection);

			connection.commit();

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

			Profiler.profile("uploadAction", uploadDetails.getStartTime());
			Profiler.report();
		}
	}


	private ActionForward handleException(Exception e, String action, Connection connection, ActionMapping mapping, HttpServletRequest request) {

		int caseNumber = AntwebUtil.getCaseNumber();
		String errorMessage = null;

		if (errorMessage == null && ("GBIFUpload").equals(action) && e.toString().contains("occurrence.txt")) {
			errorMessage = "occurrence.txt should be uploaded as TaxonWorks.";
		}

		if (errorMessage == null) {
			errorMessage = "Error. Case number:" + caseNumber + ". No changes made. e:" + e.toString();
	    }

		s_log.error("execute() errorMessage:" + errorMessage + " action:" + action + " e:" + e);
		DBUtil.rollback(connection);
		request.setAttribute("message", errorMessage);
		return mapping.findForward("message");
	}

	private void specimenPostProcess(HttpServletRequest request, Connection connection, Curator curator, UploadDetails uploadDetails) throws IOException, SQLException {
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
			runStatistics(uploadDetails.getAction(), connection, request, curator.getId(), uploadDetails.getExecTime());
		} else {
			A.log("execTime:" + uploadDetails.getExecTime());
			s_log.warn("execute() DEV MODE SKIPPING runStatistics");
		}

		//A.log("execute() populate Museum map:" + uploadDetails.getMuseumMap());
		// We will hold this for a separate curator driven museum calculation
		HttpSession session = request.getSession();
		try {
			session.setAttribute("museumMap", uploadDetails.getMuseumMap());
		} catch (IllegalStateException e) {
			s_log.info("execute() handled:" + e);
		}

		// Would like to, but it takes a 3min for CAS data. Too expensive?
		//specimenDb.calcCaste(groupId);
	}

	private void specimenPostProcessGlobal(Connection connection) throws SQLException {
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

		if (action.equals("genRecentDescEdits") || action.equals("genAll")) {
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


	private static UploadDetails uploadFileToFolder(UploadForm theForm, HttpServletRequest request
			, Login accessLogin) throws IOException {

		String messageStr;

		FormFile file2 = theForm.getTheFile2();
		Utility util = new Utility();
		String dir = theForm.getHomePageDirectory();

		String docBase = AntwebProps.getDocRoot();

		String fileName = theForm.getTheFile2().getFileName();

		if (HttpUtil.isDisallowedFileType(fileName)) {
			messageStr = "Unsupported file upload type for file:" + fileName;
			return new UploadDetails("uploadFile", messageStr, "message");
		}

		String serverDir = null;

		s_log.debug("uploadFileToFolder() serverDir:" + serverDir + " dir:" + dir);

		// Create the curator's directory if it does not already exist.
		if (dir.equals("curator")) {
			serverDir = "curator/" + accessLogin.getId();
			dir = "web/" + serverDir;
			String fullDir = AntwebProps.getDocRoot() + dir;
			s_log.debug("uploadFileToFolder() mk:" + fullDir);
			Utility.makeDirTree(fullDir);
		} else if (!"homepage".equals(dir)) {
			dir = Project.getSpeciesListDir() + dir;
//		} else if ("homepage".equals(dir)) {
//		  dir = "web/homepage";
//		  serverDir = "homepage";
		}

		String dirFileName = dir + "/" + fileName;
		String outputFileName = theForm.getOutputFileName();

		String outputFileName2 = docBase + dirFileName;

		if (outputFileName != null
				&& !outputFileName.equals("")) {
			outputFileName2 = docBase + dir + "/" + outputFileName;
		}

		String logMessage = "uploadFileToFolder() upload file"
				+ " docBase:" + docBase
				+ " dir:" + dir
				+ " fileName:" + fileName
				//+ " dirFileName:" + dirFileName
				+ " outputFileName:" + outputFileName
				+ " outputFileName2:" + outputFileName2;
		s_log.info(logMessage);

		Utility.backupFile(outputFileName2);
		boolean isSuccess = Utility.copyFile(theForm.getTheFile2(), outputFileName2);
		if (!isSuccess) {
			messageStr = "uploadFileToFolder() copyFile failure";
			return new UploadDetails("uploadFile", messageStr, "message");
		}

		s_antwebEventLog.info(logMessage);

		if (outputFileName2.contains(".zip")) {
			// If the uploaded file is a zip file, then unzip it.  (Completed?)
			if (outputFileName2.contains(" ")) {
				messageStr = "Space not allowed in uploaded zip filename.";
				//request.setAttribute("message", "Space not allowed in uploaded zip filename.");
				//return mapping.findForward("message");
			} else {

				String newDirName = fileName.substring(0, fileName.indexOf(".zip"));
				String fileLoc = dir + "/" + newDirName; // was "/uploaded"
				String unzipDir = docBase + fileLoc;
				String command = util.unzipFile(outputFileName2, unzipDir);
				s_log.info("uploadFileToFolder() unzipping: " + outputFileName2);

				String url = AntwebProps.getDomainApp() + "/" + fileLoc;

				messageStr = "Your file has been uploaded and unzipped here:<br><br>"
						+ "  &nbsp;&nbsp;&nbsp;<a href=\"" + url + "\">" + url + "</a>";

				//request.setAttribute("message", messageStr);
				//return mapping.findForward("message");
			}
			return new UploadDetails("uploadFile", messageStr, "message");
		}

		String url = AntwebProps.getDomainApp() + "/" + dirFileName;

//		UploadFile uploadFile = new UploadFile(docBase + dir, fileName, request.getHeader("User-Agent"), null);
//		uploadFile.setRoot( request.getSession().getServletContext().getRealPath("") + "/" );
//		A.log("uploadFileToFolder() serverDir:" + serverDir);
//		uploadFile.setServerDir(serverDir);

		messageStr = "Your uploaded file is here: <a href=\"" + url + "\">" + url + "</a>";

		if (fileName.contains("jpg")
				|| fileName.contains("JPG")
				|| fileName.contains("png")
				|| fileName.contains("PNG")) {
			messageStr += "<br><br>You may embed this image in a web page with this html tag: "
					+ "<pre>&lt;img src=\"" + url + "\"&gt;</pre>";
		}

		// runStatistics = true;
		s_log.info("uploadFileToFolder() done importing file:" + outputFileName2);

		s_log.info("uploadFileToFolder() " + messageStr);

		UploadDetails uploadDetails = new UploadDetails(file2.getFileName(), messageStr, request);
		uploadDetails.setForwardPage("adminMessage");

		return uploadDetails;
	}

	public String getUploadDataFileDoc(Login accessLogin) {
		String message = "";
		String delimitNote = "";
		if (accessLogin.isAdmin()) delimitNote = "tab-delimited ";
	    message = "<h2>Upload Data</h2><br> requires a " + delimitNote + "text file with...";

		if (accessLogin.isAdmin()) {
			message +=
					"<br><br>'<b>valid_species_list</b>' in the filename and has column headers: subfamily, genus, genus_species, ... - other columns irrelevant."
							+ "<br><br>or"
							+ "<br><br>'<b>valid</b>' in the filename and has column headers: subfamily, genus, species, subspecies - delete other columns."
							+ "<br><br>or"
							+ "<br><br>'<b>fossil</b>' in the filename and has column headers: subfamily, genus, species, subspecies - delete other columns."
							+ "<br><br>or"
							+ "<br><br>'<b>synonym</b>' in the filename that has columns subfamily, genus, species, [subspecies], current_valid_name (Genus species [subspecies]) - delete other columns."
							+ "<br><br>or"
							+ "<br><br>'<b>Regional_Taxon_List</b>' in the filename."
							+ "<br>File can be downloaded from http://www.antwiki.org/wiki/images/0/0c/AntWiki_Regional_Taxon_List.txt."
							+ "<br>File likely to require editing/massage.  If file is 'binary', select * in BBEdit and save in a new file (with a name that contains 'Regional_Taxon_List')."
							+ "<br>After the upload is complete, execute "
							+ "<a href='" + AntwebProps.getDomainApp() + "/utilData.do?action=populateFromAntwikiData'>" + AntwebProps.getDomainApp() + "/utilData.do?action=populateFromAntwikiData'</a>"
							+ "<br>in order to push data from the antwiki_taxon_country into the geolocale_taxon table with source = 'antwiki'."
							+ "<br><br>or"
							+ "<br><br>'<b>ngc_species</b>' or '<b>NGC Species</b>' in the filename where the contents are the Bolten New Genera Catalog saved as plain text."
							+ "<br><br>or";
		}
		if (accessLogin.isCurator()) {
			message +=
					"<br><br>'<b>specimen</b>' in the filename and just one specimen code per line (no tabs required) to get a tab-delimited file of specimen details.";
		}
   	    return message;
    }

    // Can run valid test, synonym test, or load a regional taxon list (from Antwiki).
    public UploadDetails uploadDataFile(UploadForm theForm, HttpServletRequest request, ActionMapping mapping, Connection connection)
      throws IOException {

        UploadDetails uploadDetails = null;
        String dir = theForm.getHomePageDirectory();

        FormFile testFile = theForm.getTestFile();
        String operation = testFile.getFileName();

		String docBase = AntwebProps.getDocRoot();
        //String docBase = request.getRealPath("/");
        //if (AntwebProps.isDevMode()) docBase = "/Users/mark/dev/calacademy/workingdir/";
        docBase += "web/workingdir/";
        Utility.makeDirTree(docBase);
        //A.log("uploadDataFile() docBase:" + docBase);
		String fileName = testFile.getFileName();
        String filePath = docBase + testFile.getFileName();
        boolean isSuccess = Utility.copyFile(theForm.getTestFile(), filePath);

        String UNMATCHED_FUNCTION = "UNMATCHED_FUNCTION";
		String messageStr = UNMATCHED_FUNCTION;
		if (!isSuccess) {
          messageStr = "copyFile failure";

          s_log.error("uploadDataFile() messageStr:" + messageStr + " does docBase exist:" + docBase);
          return new UploadDetails(operation, messageStr, request);
        }

		s_log.debug("uploadDataFile() dir:" + dir + " docBase:" + docBase + " filePath:" + filePath);
        // project:worldants fileName:/Users/mark/dev/calAcademy/workingdir/worldants.txt shortFileName:worldants.txt encoding:UTF-8 isBioRegion:true

		BufferedReader in = null;
        try {

            String encoding = "UTF-8";

			in = Files.newBufferedReader(Paths.get(filePath), Charset.forName(encoding));

            if (in == null) {
                messageStr = "uploadDataFile() BufferedReader is null for file:" + filePath;
				return new UploadDetails(operation, messageStr, request);
            }

            // parse the header
            String theLine = in.readLine();
            if (theLine == null) {
                messageStr = "uploadDataFile() null line.  Perhaps empty file:" + fileName + "?";
            } else {
                A.log("uploadDataFile() 1 fileName:" + fileName + " header:" + theLine);
            }
            // File comes from: http://antwiki.org/wiki/images/9/9e/AntWiki_Valid_Species.txt
            // Notice the version number inside. Link to current is here:
            //    http://antwiki.org/wiki/Species_Accounts under: List of valid fossil species (names in use)
            // AntwikiDataAction checks for updates.

            if (fileName.contains("valid_species_list")) {
              messageStr = testValidSpeciesList(in, connection);
            } else if (fileName.contains("valid") || fileName.contains("Valid")) {
              messageStr = testFileValid(in, connection);
            } else if (fileName.contains("fossil") || fileName.contains("Fossil")) {
              // File comes from: http://www.antwiki.org/wiki/images/7/7b/AntWiki_Fossil_Species.txt
              // Notice the version number inside. Link to current is here:
              //    http://antwiki.org/wiki/Species_Accounts under: List of valid fossil species (names in use)
              // AntwikiDataAction checks for updates.
              messageStr = testFileFossil(in, connection);
            } else if (fileName.contains("synonym")) {
              messageStr = testFileSynonyms(in, connection);
            } else if (fileName.contains("Regional_Taxon_List")) {
              messageStr = "<br><br>In order to push antwiki data on to geolocale_taxon, click ";
              messageStr += "<a href='" + AntwebProps.getDomainApp() + "/utilData.do?action=populateFromAntwikiData'>"
					  + AntwebProps.getDomainApp() + "/utilData.do?action=populateFromAntwikiData'</a><br><br>";
              messageStr += new AntWikiDataAction().loadRegionalTaxonList(in, connection);
            } else if (fileName.contains("fips-414")) {  // Adm1 load file
              messageStr = new Adm1LoadAction().loadList(in, connection);
            } else if (fileName.contains("ngc_species") || fileName.contains("NGC Species")) { // Bolton New Genera Catalog
              messageStr = boltonNewGeneraCatalog(in, connection);
            } else if (fileName.contains("specimen")) {
				messageStr = getSpecimenList(fileName, theLine, in, connection);
			} else if (UNMATCHED_FUNCTION.equals(messageStr)) {
            	messageStr = "Upload file function not recognized:" + filePath + ". Submit for documention.";
			}

			s_log.info("uploadDataFile() fileName:" + fileName + " messageStr:" + messageStr);
            request.setAttribute("message", messageStr);
        } catch (FileNotFoundException e) {
            messageStr = "uploadDataFile() e" + e;
            s_log.error(messageStr);
        } catch (IndexOutOfBoundsException e) {
            messageStr = "uploadDataFile() e" + e;
            AntwebUtil.logStackTrace(e);
            s_log.error(messageStr);
        } catch (Exception e) {
            AntwebUtil.logStackTrace(e);
            messageStr = "uploadDataFile() e" + e;
            s_log.error(messageStr);
        } finally {
            in.close();
        }

		uploadDetails = new UploadDetails(operation, messageStr, request);
		uploadDetails.setForwardPage("adminMessage");

        return uploadDetails;
    }


	private String getSpecimenList(String fileName, String firstLine, BufferedReader in, Connection connection)
			throws IOException, SQLException {
		String messageStr;
		String line = firstLine;

		int lineNum = 0;
		String dir = "/tmp";
		String fullDir = AntwebProps.getDocRoot() + dir;
		FileUtil.makeDir(fullDir);
		String outputPath = "/specimenList.txt";
		String fullPath = fullDir + outputPath;
		String fullUrl = AntwebProps.getDomainApp() + dir + outputPath;
		A.log("getSpecimenList() outputPath is:" + fullPath + " fullUrl:" + fullUrl);

		SpecimenDb specimenDb = new SpecimenDb(connection);
		//TaxonDb taxonDb = new TaxonDb(connection);

		LogMgr.emptyFile(fullPath);
		LogMgr.appendFile(fullPath, Specimen.getTabDelimHeader());

		while (line != null) {
            //A.log("getSpecimenList() lineNum:" + lineNum + " line:" + line);

			// If the file contains simply "*", generate a file with all specimen data.
			if ("*".equals(line) && 0 == lineNum) {
				ArrayList<String> allSpecimen = specimenDb.getAntwebSpecimenCodes(Family.FORMICIDAE);
				A.log("logAll() size:" + allSpecimen.size());
				for (String code : allSpecimen) {
					String outputLine = specimenDb.getSpecimen(code).getTabDelimString();
					LogMgr.appendFile(fullPath, outputLine);
				}
				break;
			}

			++lineNum;
			if (line == null) continue;
			boolean parseLine = true;
            String specimenCode = line;
            if (specimenCode == null || specimenCode.contains(" ")) {
            	messageStr = "<h1>Specimen List</h1><br><br>There seems to be a file format error in file: " + fileName;
            	s_log.error(messageStr);
            	return messageStr;
			}
			//if (lineNum < 5) A.log("output specimen:" + specimenCode);

			Specimen specimen = specimenDb.getSpecimen(specimenCode);
			//String taxonName = specimen.getTaxonName();
			//Taxon taxon = taxonDb.getTaxon(taxonName);
            String outputLine = specimen.getTabDelimString();
            LogMgr.appendFile(fullPath, outputLine);

			line = in.readLine();
		} // end while loop through lines

		A.log("getSpecimenList() lineNum:" + lineNum + " line:" + line);
		//http://localhost/antweb//tmp/specimenList.txt

		messageStr = "<h2>Specimen List</h2><br><br>";
		messageStr += "<b>'Right-click' and 'Save Link As' to download:</b><br> <a href=\"" + fullUrl + "\">" + outputPath + "</a>";
		messageStr += "<br><br>";

		return messageStr;
	}

    private String  boltonNewGeneraCatalog(BufferedReader in, Connection connection)
      throws IOException {
        String messageStr;
        String line = "";

        StringBuffer content = new StringBuffer();

        int lineNum = 0;
        int parsedLines = 0;

        String dir = "/tmp";
        String fullDir = AntwebProps.getDocRoot() + dir;
        FileUtil.makeDir(fullDir);
        String outputPath = "/parsedBoltonNGC.txt";
        String fullPath = fullDir + outputPath;
        String fullUrl = AntwebProps.getDomainApp() + dir + outputPath;
        s_log.debug("boltonNewGeneraCatalog() outputPath is:" + fullPath + " fullUrl:" + fullUrl);

        while (line != null) {
          line = in.readLine();
          ++lineNum;

          if (line == null) continue;

          boolean parseLine = true;

          // if it follows the pattern of one word, followed by a period, no *
          if (line.length() > 0 && line.charAt(0) == '*') parseLine = false;
          if (!line.contains("(")) parseLine = false;
          int periodIndex = line.indexOf(".");
          if (periodIndex < 0) continue;
          if (line.indexOf(" ") < periodIndex) parseLine = false;

          if (parseLine) {
            String p1 = line.substring(0, periodIndex);
            int parenIndex = line.indexOf("(");
            if (parenIndex < 0 || parenIndex < periodIndex) continue;
            String p2 = line.substring(periodIndex + 2, parenIndex);

            ++parsedLines;

            if (parsedLines < 20)
              content.append(p1 + "\t" + p2 + "<br>\n");

            if (p1.equals("striatus")) s_log.debug("striatus " + p2);

            LogMgr.appendFile(fullPath, p1 + "\t" + p2);

          }

        } // end while loop through lines

        s_log.debug("boltonNewGeneraCatalog() lineNum:" + lineNum + " parsedLines:" + parsedLines);

        messageStr = "<h3>Parsed Bolton New Genera Catalog</h3><br><br>";

        messageStr += "<b>'Right-click' and 'Save Link As' to download:</b><br> <a href=\"" + fullUrl + "\">" + outputPath + "</a>";

//http://localhost/antweb//tmp/parsedBoltonNGC.txt

        messageStr += "<br><br>" + content;

        return messageStr;
    }

    private String testFileValid(BufferedReader in, Connection connection)
      throws IOException, SQLException {
        String messageStr;
        String theLine = "";

        //try {

            AntwikiTaxonCountryDb antwikiTaxonCountryDb = new AntwikiTaxonCountryDb(connection);
            antwikiTaxonCountryDb.deleteValidTaxa();

            StringBuffer content = new StringBuffer();

            int validTaxonCount = 0;
            int notValidTaxonCount = 0;
            int lineNum = 0;
            TaxonDb taxonDb = new TaxonDb(connection);
            while (theLine != null) {
              theLine = in.readLine();
              ++lineNum;

              if (theLine == null) continue;

              //String taxonName = (new AntWikiData(theLine)).getShortTaxonName();
              String taxonName = new AntWikiData(theLine).getTaxonName();
              antwikiTaxonCountryDb.insertValidTaxa(taxonName);
              // insert taxonName into Antwiki_valid_taxa
              // then report on select valid taxa from taxon where not in (select taxon_name from antwiki_valid_taxa.

              Taxon dummyTaxon = taxonDb.getTaxon(taxonName); //TEMP08
              String validStr = "null";
              if (dummyTaxon != null) validStr = dummyTaxon.getStatus();
              //A.log("testFileValid() taxonName:" + taxonName + " valid:" + validStr);
              if (dummyTaxon != null) {
                if (dummyTaxon.getStatus().equals(Status.VALID) || dummyTaxon.getStatus().equals(Status.UNIDENTIFIABLE)) {
                  ++validTaxonCount;
                } else {
                  ++notValidTaxonCount;
                  content.append("line:" + lineNum + " <a href='" + AntwebProps.getDomainApp() + "/description.do?taxonName=" + taxonName + "'>" + taxonName + "</a> - " + dummyTaxon.getStatus() + " <br>\n");
                }
              } else {
                ++notValidTaxonCount;
                content.append("line:" + lineNum + " " + taxonName + " <br>\n");
              }

            } // end while loop through lines

            s_log.debug("testFileValid() validTaxonCount:" + validTaxonCount + " notValidTaxonCount:" + notValidTaxonCount);
            messageStr = "<h3>AntWiki Regional Taxon List Load</h3><br><br>"
              + "validTaxonCount:" + validTaxonCount + " notValidTaxonCount:" + notValidTaxonCount + "\n"
              + "<br><br>&nbsp;&nbsp;&nbsp;&nbsp;Report: <a href='" + AntwebProps.getDomainApp() + "/query.do?action=curiousQuery&name=antwebUniqueValidTaxa'>Antweb Unique Valid Taxa</a>"
              + "<br><br>" + content
              ;

        return messageStr;
    }

    private String testFileFossil(BufferedReader in, Connection connection)
      throws IOException, SQLException {
        String messageStr = null;
        String theLine = "";

        try {

            AntwikiTaxonCountryDb antwikiTaxonCountryDb = new AntwikiTaxonCountryDb(connection);
            antwikiTaxonCountryDb.deleteFossilTaxa();

            RE tab = new RE("\t");

            String[] components;
            StringBuffer content = new StringBuffer();

            int fossilTaxonCount = 0;
            int notFossilTaxonCount = 0;
            int lineNum = 0;
            TaxonDb taxonDb = new TaxonDb(connection);
            while (theLine != null) {
              theLine = in.readLine();
              ++lineNum;

              if (theLine == null) continue;

              components = tab.split(theLine);
              String subfamily = components[0].toLowerCase();
              if ("uncertain".equals(subfamily)) subfamily = "incertae_sedis";
              String genus = components[1].toLowerCase();
              String species = components[2];
              String subspecies = null;
              if (components.length > 3) {
                  subspecies = components[3];
              }

              //subfamily = subfamily.trim();
              //genus = genus.trim();

              String taxonName = subfamily + genus + " " + species;
              if (subspecies != null) taxonName += " " + subspecies;

              //A.log("testFileFossil() subfamily:" + subfamily + ". genus:" + genus + ". taxonName:" + taxonName);


              antwikiTaxonCountryDb.insertFossilTaxa(taxonName);
              // insert taxonName into Antwiki_fossil_taxa
              // then report on select fossil taxa from taxon where not in (select taxon_name from antwiki_fossil_taxa.

              Taxon dummyTaxon = taxonDb.getTaxon(taxonName); //TEMP08
              if (dummyTaxon != null) {
                if (dummyTaxon.getIsFossil()) {
                  ++fossilTaxonCount;
                } else {
                  ++notFossilTaxonCount;

//??
                  content.append("line:" + lineNum + " <a href='" + AntwebProps.getDomainApp() + "/description.do?taxonName=" + taxonName + "'>" + taxonName + "</a> - not fossil<br>\n");
                }
              } else {
                ++notFossilTaxonCount;
                content.append("line:" + lineNum + " " + taxonName + " <br>\n");
              }

            } // end while loop through lines

            s_log.debug("testFileFossil() fossilTaxonCount:" + fossilTaxonCount + " notFossilTaxonCount:" + notFossilTaxonCount);
            messageStr = "<h3>Fossil Test</h3><br><br>"
              + "These taxa were found in the file but not on Antweb, or they were found but were not flagged as a fossil...<br><br><br>"
              + "fossilTaxonCount:" + fossilTaxonCount + " notFossilTaxonCount:" + notFossilTaxonCount + "\n"

              + "<br><br>&nbsp;&nbsp;&nbsp;&nbsp;Report: <a href='" + AntwebProps.getDomainApp() + "/query.do?action=curiousQuery&name=antwebUniqueFossilTaxa'>Antweb Unique Fossil Taxa</a>"

              + "<br><br>" + content
              ;

        } catch (RESyntaxException e) {
          s_log.info("testFileFossil() e:" + e);
        }
        return messageStr;
    }

    private String testValidSpeciesList(BufferedReader in, Connection connection)
      throws IOException, SQLException {
        String messageStr = null;
        String theLine = "";
        try {
            //RE tab = new RE("\t");
            RE comma = new RE(",");

            String[] components;
            //StringBuffer cvnfContent = new StringBuffer();
            StringBuffer tnfContent = new StringBuffer(96); // Taxa Not Found
            StringBuffer awContent = new StringBuffer(64); // Taxa Not Found

            int currentValidFound = 0;
            int currentValidNotFound = 0;
            int taxonNotFound = 0;
            int lineNum = 0;
            TaxonDb taxonDb = new TaxonDb(connection);

            ArrayList<Taxon> validSpecies = taxonDb.getTaxa("species", "valid");
            HashMap<String, Taxon> validSpeciesMap = new HashMap<>();
            for (Taxon taxon : validSpecies) {
              validSpeciesMap.put(taxon.getTaxonName(), taxon);
            }
            int antwebValidSpeciesCount = validSpeciesMap.size();

            int c = 0;
            while (theLine != null) {
              theLine = in.readLine();
              ++lineNum;

              if (theLine == null) continue;

              //components = tab.split(theLine);
              components = comma.split(theLine);
              String subfamily = components[0].toLowerCase();
              String genus = components[1].toLowerCase();
              String species = components[2];
/*(
              String subspecies = null;
              if (components.length > 3) {
                  subspecies = components[3];
              }
              String currentValidName = null;
              if (components.length > 4) {
                  currentValidName = subfamily + components[4].toLowerCase();
              }
*/
              if (subfamily == null || "".equals(subfamily)) subfamily = "incertae_sedis";
              String taxonName = subfamily + species;
              taxonName = taxonName.toLowerCase();
//              if (subspecies != null) taxonName += " " + subspecies;

              validSpeciesMap.remove(taxonName);

              Taxon dummyTaxon = taxonDb.getTaxon(taxonName);
              String antwebCurrentValidName = null;
              if (dummyTaxon != null) {
/*
                antwebCurrentValidName = dummyTaxon.getCurrentValidName();
                if (currentValidName != null && currentValidName.equals(antwebCurrentValidName)) {
                  ++currentValidFound;
                } else {
                  cvnfContent.append("line:" + lineNum + " <a href='" + AntwebProps.getDomainApp() + "/description.do?taxonName=" + taxonName + "'>" + taxonName + "</a> refers:" + currentValidName + " antwebCurrentValidName:" + antwebCurrentValidName + "<br>\n");
                  ++currentValidNotFound;
                }
*/
              } else {
                ++c;
                tnfContent.append(c + ". line:" + lineNum + " <a href='" + AntwebProps.getDomainApp() + "/description.do?taxonName=" + taxonName + "'>" + species +  "</a><br>&nbsp;&nbsp;" + theLine + "<br>\n"); //" refers:" + currentValidName +
                ++taxonNotFound;
              }
            } // end while loop through lines

            int c2 = 0;
            for (Taxon taxon : validSpeciesMap.values()) {
              ++c2;
              awContent.append("<br>" + c2 + ". <a href='" + AntwebProps.getDomainApp() + "/description.do?taxonName=" + taxon.getTaxonName() + "'>" + Taxon.getPrettyTaxonName(taxon.getTaxonName()) + "</a>");
            }

            messageStr = "<h2>Valid Species List</h2>"
              + "<br>Lines in Antcat valid species file: " + (lineNum - 1)  // minus 1 for the header
              + "<br>Antcat valid species Count not in Antweb: " + c
              + "<br><br>Antweb valid species count: " + antwebValidSpeciesCount
              + "<br>Antweb valid species count not in file: " + validSpeciesMap.size()
             // + " taxonNotFound:" + taxonNotFound + "\n"
             // + "<br><br><h3>Current Valid Name in Question:</h3><br><br>" + cvnfContent.toString()
              + "<br><br><h3>Antcat taxa not found in Antweb</h3><br>" + tnfContent
              + "<br><br><h3>Antweb taxa not found in file</h3>" + awContent;


        } catch (RESyntaxException e) {
          s_log.error("testValidSpeciesList() e:" + e);
        }
        return messageStr;
    }

    private String testFileSynonyms(BufferedReader in, Connection connection)
      throws IOException, SQLException {
        String messageStr = null;
        String theLine = "";
        try {
            RE tab = new RE("\t");

            String[] components;
            StringBuffer cvnfContent = new StringBuffer(96);
            StringBuffer tnfContent = new StringBuffer();

            int currentValidFound = 0;
            int currentValidNotFound = 0;
            int taxonNotFound = 0;
            int lineNum = 1;
            TaxonDb taxonDb = new TaxonDb(connection);
            while (theLine != null) {
              theLine = in.readLine();
              ++lineNum;

              if (theLine == null) continue;

              components = tab.split(theLine);
              String subfamily = components[0].toLowerCase();
              String genus = components[1].toLowerCase();
              String species = components[2];
              String subspecies = null;
              if (components.length > 3) {
                  subspecies = components[3];
              }
              String currentValidName = null;
              if (components.length > 4) {
                  currentValidName = subfamily + components[4].toLowerCase();
              }

              String taxonName = subfamily + genus + " " + species;
              if (subspecies != null) taxonName += " " + subspecies;

              Taxon dummyTaxon = taxonDb.getTaxon(taxonName);
              String antwebCurrentValidName;
              if (dummyTaxon != null) {
                antwebCurrentValidName = dummyTaxon.getCurrentValidName();
                if (currentValidName != null && currentValidName.equals(antwebCurrentValidName)) {
                  ++currentValidFound;
                } else {
                  cvnfContent.append("line:" + lineNum + " <a href='" + AntwebProps.getDomainApp() + "/description.do?taxonName=" + taxonName + "'>" + taxonName + "</a> refers:" + currentValidName + " antwebCurrentValidName:" + antwebCurrentValidName + "<br>\n");
                  ++currentValidNotFound;
                }
              } else {
                tnfContent.append("line:" + lineNum + " " + taxonName + " refers:" + currentValidName + "<br>\n");
                ++taxonNotFound;
              }
            } // end while loop through lines

            messageStr = "<h3>Synonym Test</h3><br><br>"
              + " currentValidFound:" + currentValidFound + " currentValidNotFound:" + currentValidNotFound + " taxonNotFound:" + taxonNotFound + "\n"
              + "<br><br><h3>Current Valid Name in Question:</h3><br><br>" + cvnfContent
              + "<br><br><h3>Taxa Not Found</h3><br><br>" + tnfContent;

        } catch (RESyntaxException e) {
          s_log.error("testFileSynonyms() e:" + e);
        }
        return messageStr;
    }


    private String worldAuthGen(HttpServletRequest request) {

        HttpSession session = request.getSession();

        s_log.info("worldAuthGen() generating world authority files");
        WorldAuthorityGenerator worldGen = new WorldAuthorityGenerator();

        ArrayList<HashMap<String, String>> extinctSubfamilies = worldGen.getSubfamilies("extinct");
        ArrayList<HashMap<String, String>> extantSubfamilies = worldGen.getSubfamilies("extant");

        s_log.info("worldAuthGen() Extinct subfamilies:" + extinctSubfamilies.size());
        s_log.info("worldAuthGen() Extant subfamilies:" + extantSubfamilies.size());
        s_antwebEventLog.info("import worldAuthorityFiles");

        ArrayList<HashMap<String, String>> allSubfamilies = new ArrayList<>();
        Iterator<HashMap<String,String>> iter = extinctSubfamilies.iterator();
        while (iter.hasNext()) {
          allSubfamilies.add(iter.next());
        }
        iter = extantSubfamilies.iterator();
        while (iter.hasNext()) {
           allSubfamilies.add(iter.next());
        }
        HashMap<String, ArrayList<String>> genusToSubfamily = worldGen.getSubfamilyLookup(allSubfamilies);
        // do this 3 times because sometimes there are chains of synonyms - this is sort of a hack
        // but should be ok...
        for (int loop = 0; loop < 3; loop++) {
          extinctSubfamilies = worldGen.getSubfamiliesForSynonyms(extinctSubfamilies, genusToSubfamily);
          extantSubfamilies = worldGen.getSubfamiliesForSynonyms(extantSubfamilies, genusToSubfamily);
        }

        extantSubfamilies =  worldGen.addSynposisInfo(extantSubfamilies,"extant");
        extinctSubfamilies =  worldGen.addSynposisInfo(extinctSubfamilies,"extinct");

        // for testing purposes
        //ArrayList<String> extant = worldGen.generateTSVSynopsisOnly("extant", allSubfamilies, extantSubfamilies);
        //ArrayList<String> extinct = extant;

        ArrayList<String> extinct = worldGen.generateTSV("extinct", allSubfamilies, extinctSubfamilies);
        s_log.info("worldAuthGen() done with extinct");
        ArrayList<String> extant = worldGen.generateTSV("extant", allSubfamilies, extantSubfamilies);

        s_log.info("worldAuthGen() about to make problems");
        String problems = "<h2>Problems with extinct taxa</h2>" + extinct.get(0) + " <h2>Problems with extant taxa</h2>" + extant.get(0);

        session.setAttribute("extinct", extinct.get(1));
        session.setAttribute("extant", extant.get(1));
        session.setAttribute("problems",problems);
        s_log.info("worldAuthGen() done generating world authority files");

        s_log.info("World Authority File upload not running statistics.  Correct?  Struts Forward correct?");
        return "worldAuthorityFiles";
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
