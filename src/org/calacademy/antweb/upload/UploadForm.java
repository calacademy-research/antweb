package org.calacademy.antweb.upload;

import org.apache.struts.action.ActionForm;
import org.apache.struts.upload.FormFile;

public class UploadForm extends ActionForm {
  protected FormFile theFile;
  protected FormFile theFile2;
  protected FormFile biota;
  protected FormFile taxonWorks;
  protected FormFile testFile;
  protected String projectFile = "";
  protected String homePageDirectory = "";
  protected String ancFileDirectory = "";
  protected String images = "no";
  protected String updateAdvanced = "no";
  protected String updateSpecimenStatus = "no";  
  protected String updateFieldGuide = "none";
  protected String downloadSpeciesList = "none";
  protected String reloadSpeciesList = "none";
  protected String deleteSpeciesList = "none";
  protected String deleteProject = "none";
  protected String createProject = "none";
  protected String editSpeciesList = "none";
  protected String whole = "false";
  protected String encoding;  
  protected String outputFileName = "";
  protected String successKey;
  protected String action;
  private String specimenUploadType = "full";
  protected String recrawl = "true";

  /*
  private boolean isUp = false;
  public boolean isUp() {
    return isUp;
  }
  public void setIsUp(boolean isUp) {
    this.isUp = isUp;
  }
*/
  public int groupId;
  
  public int getGroupId() {
      return groupId;
  } 
  public void setGroupId(int groupId) {
      this.groupId = groupId;
  }
    
  public String getSpecimenUploadType() {
        return this.specimenUploadType;
  }
  public void setSpecimenUploadType(String specimenUploadType) {
        this.specimenUploadType = specimenUploadType;
  }

  public String getSuccessKey() {
	return successKey;
  }
  public void setSuccessKey(String successKey) {
	this.successKey = successKey;
  }

  public String getOutputFileName() {
	return outputFileName;
  }
  public void setOutputFileName(String outputFileName) {
	this.outputFileName = outputFileName;
  }


  public String getUpdateFieldGuide() {
	return updateFieldGuide;
  }
  public void setUpdateFieldGuide(String updateFieldGuide) {
	this.updateFieldGuide = updateFieldGuide;
  }

  public String getDownloadSpeciesList() {
	return downloadSpeciesList;
  }
  public void setDownloadSpeciesList(String downloadSpeciesList) {
	this.downloadSpeciesList = downloadSpeciesList;
  }
  
  public String getReloadSpeciesList() {
	return reloadSpeciesList;
  }
  public void setReloadSpeciesList(String reloadSpeciesList) {
	this.reloadSpeciesList = reloadSpeciesList;
  }

  public String getDeleteSpeciesList() {
	return deleteSpeciesList;
  }
  public void setDeleteSpeciesList(String deleteSpeciesList) {
	this.deleteSpeciesList = deleteSpeciesList;
  }

  public String getDeleteProject() {
	return deleteProject;
  }
  public void setDeleteProject(String deleteProject) {
	this.deleteProject = deleteProject;
  }
  public String getCreateProject() {
	return createProject;
  }
  public void setCreateProject(String createProject) {
	this.createProject = createProject;
  }

  public String getEditSpeciesList() {
	return editSpeciesList;
  }
  public void setEditSpeciesList(String editSpeciesList) {
	this.editSpeciesList = editSpeciesList;
  }
  
  public void setImages(String images) {
    this.images = images;
  }
  public String getImages() {
    return images;
  }

  public void setUpdateAdvanced(String updateAdvanced) {
    this.updateAdvanced = updateAdvanced;
  }
  public String getUpdateAdvanced() {
    return updateAdvanced;
  }

  public void setUpdateSpecimenStatus(String updateSpecimenStatus) {
    this.updateSpecimenStatus = updateSpecimenStatus;
  }
  public String getUpdateSpecimenStatus() {
    return updateSpecimenStatus;
  }

  public void setTheFile(FormFile file) {
    this.theFile = file;
  }
  public FormFile getTheFile() {
    return theFile;
  }

  public void setTheFile2(FormFile file) {
    this.theFile2 = file;
  }
  public FormFile getTheFile2() {
    return theFile2;
  }

  public void setTestFile(FormFile testFile) {
    this.testFile = testFile;
  }
  public FormFile getTestFile() {
    return testFile;
  }

  public void setProjectFile(String projectFile) {
    this.projectFile = projectFile;
  }
  public String getProjectFile() {
    return projectFile;
  }

  public void setHomePageDirectory(String homePageDirectory) {
    this.homePageDirectory = homePageDirectory;
  }
  public String getHomePageDirectory() {
    return homePageDirectory;
  }

  public void setBiota(FormFile file) {
    biota = file;
  }
  public FormFile getBiota() {
    return biota;
  }

  public void setTaxonWorks(FormFile file) {
    taxonWorks = file;
  }
  public FormFile getTaxonWorks() {
    return taxonWorks;
  }

  public String getWhole() {
	return whole;
  }
  public void setWhole(String whole) {
	this.whole = whole;
  }

  public String getEncoding() {
	return encoding;
  }
  public void setEncoding(String encoding) {
	this.encoding = encoding;
  }
  
  public String getAncFileDirectory() {
	return ancFileDirectory;
  }
  public void setAncFileDirectory(String ancFileDirectory) {
	this.ancFileDirectory = ancFileDirectory;
  }
  
  public String getAction() {
	return action;
  }
  public void setAction(String action) {
	this.action = action;
  }

  public String getRecrawl() {
	return recrawl;
  }
  public void setRecrawl(String recrawl) {
	this.recrawl = recrawl;
  }
  
}


