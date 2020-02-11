/**
 * --------------------------------------------------------------------------
 * Copyright (c) 2002, Sebastian Delmont <sdelmont@zonageek.com> 
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * - Redistributions  of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer. 
 * 
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * 
 * - Neither the name of Sebastian Delmont nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * ---------------------------------------------------------------------------
 */

package com.zonageek.jpeg;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Vector;
import java.util.Iterator;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
        
public class JpegTest {

  private static Log s_log = LogFactory.getLog(JpegTest.class);

  public static void main(String[] argv) {

    String inFileName = "./test/images/test.jpg";
    String outFileName = "./test/images/test-out.jpg";

    s_log.info("JPEG Tests"); 
    
    try {
      Jpeg jpeg = new Jpeg();
      Jpeg jpeg2 = new Jpeg();
      
      s_log.info("Reading...");
//      jpeg.activateCodec();
      jpeg.read(new FileInputStream(inFileName));
      
      Vector blocks = jpeg.getBlocks();
      HashMap info;
      for (int i = 0; i < blocks.size(); i++) {
        JpegBlock block = (JpegBlock) blocks.elementAt(i);
        info = block.getInformation();
        
        s_log.info("Original Block at position " + i + ": ");
        s_log.info("  Marker " + info.get("Jpeg Block"));
        for (Iterator keys = info.keySet().iterator(); keys.hasNext(); ) {
          Object key = keys.next();
          s_log.info("  - " + key + ": [" + info.get(key) + "]");
        }
      }

//      info = jpeg.getCodec().getInformation();
//      
//      s_log.info("CODEC INFORMATION");
//      for (Iterator keys = info.keySet().iterator(); keys.hasNext(); ) {
//        Object key = keys.next();
//        s_log.info("  - " + key + ": [" + info.get(key) + "]");
//      }

            
      AdobeBlock adobe = jpeg.getAdobeBlock();
//      if (adobe != null) {
//        adobe.setIptcField("Caption", "This is my new caption");
//        adobe.addIptcField("Keywords", "Funny");
//        
//        s_log.info("Caption:" + adobe.getIptcField("Caption"));
//      }

      s_log.info("Writing...");
      jpeg2.copyMinimumBlocksFrom(jpeg);
      adobe = new AdobeBlock();
      adobe.setIptcField("Caption", "New IPTC");
      adobe.addIptcField("Keywords", "Even funnier");
      ExifBlock exif = new ExifBlock();
      exif.copyData(jpeg.getExifBlock());

      
      jpeg2.addJpegBlock(adobe);
      jpeg2.addJpegBlock(exif);
      jpeg2.write(new FileOutputStream(outFileName));

      
      Jpeg jpeg3 = new Jpeg();
      jpeg3.read(new FileInputStream(outFileName));
      blocks = jpeg3.getBlocks();
      for (int i = 0; i < blocks.size(); i++) {
        JpegBlock block = (JpegBlock) blocks.elementAt(i);
        info = block.getInformation();
        
        s_log.info("Block at position " + i + ": ");
        s_log.info("Marker " + info.get("Jpeg Block"));
      }

    }
    catch (Exception ex) {
      s_log.error("main() e:" + ex);
      ex.printStackTrace();
    }
  }
}
