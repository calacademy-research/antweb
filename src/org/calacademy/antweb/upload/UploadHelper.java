package org.calacademy.antweb.upload;

import org.calacademy.antweb.*;
import org.calacademy.antweb.upload.*;

public class UploadHelper {
  static Group group;
  static String source;
  static UploadFile uploadFile;
          
  public static void init(UploadFile uploadFile, Group group) {
    setGroup(group);
    setUploadFile(uploadFile);
  }             
             
// ------- Convenience Methods -----------

  public static void setGroup(Group thisGroup) {
    group = thisGroup;
  }
  public static Group getGroup() {
    return group;
  }

  public static void setUploadFile(UploadFile thisUploadFile) {
    uploadFile = thisUploadFile;
  }
  public static UploadFile getUploadFile() {
    return uploadFile;
  }    
  
  public static void setSource(String thisSource) {
    source = thisSource;
  }
  public static String getSource() {
    return source;
  }    

}