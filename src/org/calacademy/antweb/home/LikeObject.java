package org.calacademy.antweb.home;
    
/**
 * One liked image (image id, number of likes, shot, specimen code and number, taxon name), a plain holder
 * filled by ImageDb.getLikesObjectList for the favourite images list (list.do?action=likes,
 * web/imageLikes-body.jsp).
 */
public class LikeObject {
    public String imageId;
    public String likes;
    public String shot;
    public String code;
    public String number;
    public String taxonName;

    public String getCode() { return code; }

}    
