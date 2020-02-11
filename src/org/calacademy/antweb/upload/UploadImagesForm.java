package org.calacademy.antweb.upload;

import org.calacademy.antweb.*;
import org.apache.struts.action.ActionForm;

public class UploadImagesForm extends ActionForm {

  protected String copyright = "";
  protected String artist ="";
  protected String license = "";
  protected String action = "";
  
  public String getAction() {
    return action;
  }
  public void setAction(String action) {
    this.action = action;
  }

  public String getLicense() {
    return license;
  }
  public void setLicense(String string) {
    license = string;
  }

  public String getArtist() {
    return artist;
  }
  public void setArtist(String string) {
    artist = string;
  }

  public String getCopyright() {
    return copyright;
  }
  public void setCopyright(String string) {
    copyright = string;
  }

}


