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

import java.util.HashMap;

//=============================================================================
//=============================================================================
public class JfifBlock extends JpegBlock {
  public final static int UNKNOWN_VALUE = -1;

  public final static int UNITS_UNKNOWN = -1;
  public final static int UNITS_PIXELS = 0;
  public final static int UNITS_DPI = 1;
  public final static int UNITS_DPCM = 2;
  
  protected int jfifVersionMajor;
  protected int jfifVersionMinor;
  protected int jfifUnits;
  protected int jfifXDensity;
  protected int jfifYDensity;
  protected int jfifThumbnailWidth;
  protected int jfifThumbnailHeight;
  protected byte[] jfifThumbnailRGB;
  
  //===========================================================================
  public JfifBlock() {
    this(null);
  }

  //===========================================================================
  public JfifBlock(Jpeg jpeg) {
    super(Jpeg.MARKER_APP0, jpeg);
    
    this.jfifVersionMajor = 1;
    this.jfifVersionMinor = 2;
    this.jfifUnits = JfifBlock.UNITS_DPI;
    this.jfifXDensity = 72;
    this.jfifYDensity = 72;
    this.jfifThumbnailWidth = 0;
    this.jfifThumbnailHeight = 0;
    this.jfifThumbnailRGB = null;
    
    this.dataInSync = false;
  }
  
  //===========================================================================
  public JfifBlock(byte[] data, Jpeg jpeg) throws JpegException {
    super(Jpeg.MARKER_APP0, data, jpeg);
  }
  
  //===========================================================================
  public JfifBlock(int marker, byte[] data, Jpeg jpeg) throws JpegException {
    super(marker, data, jpeg);
  }
  
  //===========================================================================
  public HashMap getInformation() {
    HashMap info = super.getInformation();
    
    info.put("Jpeg Block", "JFIF (APP0)");
    //info.remove("Jpeg Block Length");
    if (this.jfifVersionMinor < 10) {
      info.put("JFIF Version", "" + this.jfifVersionMajor + ".0" 
        + this.jfifVersionMinor);
    }
    else {
      info.put("JFIF Version", "" + this.jfifVersionMajor + "." 
        + this.jfifVersionMinor);
    }

    switch (this.jfifUnits) {
    case 0:
      info.put("X Density", "" + this.jfifXDensity + " px");
      info.put("Y Density", "" + this.jfifXDensity + " px");
      break;
    case 1:
      info.put("X Density", "" + this.jfifXDensity + " dpi");
      info.put("Y Density", "" + this.jfifXDensity + " dpi");
      break;
    case 2:
      info.put("X Density", "" + this.jfifXDensity + " dpcm");
      info.put("Y Density", "" + this.jfifXDensity + " dpcm");
      break;
    default:
      info.put("X Density", "" + this.jfifXDensity + " ?");
      info.put("Y Density", "" + this.jfifXDensity + " ?");
      break;
    }
    if (this.jfifThumbnailWidth > 0) {
      info.put("Thumbnail Width", Integer.valueOf(this.jfifThumbnailWidth));
      info.put("Thumbnail Height", Integer.valueOf(this.jfifThumbnailHeight));
      info.put("Thumbnail Data Length", "" + this.jfifThumbnailRGB.length 
        + " bytes");
    }

    return info;
  }

  //===========================================================================
  public int getJFIFVersionMajor() {
    return this.jfifVersionMajor;
  }

  //===========================================================================
  public int getJFIFVersionMinor() {
    return this.jfifVersionMinor;
  }
  
  //===========================================================================
  public int getJFIFUnits() {
    return this.jfifUnits;
  }

  //===========================================================================
  public int getJFIFXDensity() {
    return this.jfifXDensity;
  }

  //===========================================================================
  public int getJFIFYDensity() {
    return this.jfifYDensity;
  }

  //===========================================================================
  public int getJFIFThumbnailWidth() {
    return this.jfifThumbnailWidth;
  }

  //===========================================================================
  public int getJFIFThumbnailHeight() {
    return this.jfifThumbnailHeight;
  }

  //===========================================================================
  public void parseData() throws JpegException {
    if (this.getFixedString(0, 4).equals("JFIF")) {
      this.jfifVersionMajor = this.getByte(5);   
      this.jfifVersionMinor = this.getByte(6);
    
      this.jfifUnits = this.getByte(7);
      
      this.jfifXDensity = this.getShort(8);
      this.jfifYDensity = this.getShort(10);
  
      this.jfifThumbnailWidth = this.getByte(12);
      this.jfifThumbnailHeight = this.getByte(13);
      if ((this.jfifThumbnailWidth > 0) && (this.jfifThumbnailHeight > 0)) {
        this.jfifThumbnailRGB = this.getByteArray(14, 
          this.jfifThumbnailWidth * this.jfifThumbnailHeight * 3);
      }
    }
    else {
      throw new JpegException("Not a JFIF block");
    }
  }

  //===========================================================================
  protected void createData() throws JpegException {
    this.newData();
    
    this.putString(0, "JFIF\0");
    this.putByte(5, this.jfifVersionMajor);
    this.putByte(6, this.jfifVersionMinor);

    this.putByte(7, this.jfifUnits);
    this.putShort(8, this.jfifXDensity);
    this.putShort(10, this.jfifYDensity);

    if (this.jfifThumbnailRGB == null) {
      this.putByte(12, 0);
      this.putByte(13, 0);
    }
    else {    
      this.putByte(12, this.jfifThumbnailWidth);
      this.putByte(13, this.jfifThumbnailHeight);
      int rgblen = this.jfifThumbnailWidth * this.jfifThumbnailHeight * 3;
      if (this.jfifThumbnailRGB.length != rgblen) {
        this.putByteArray(14, new byte[rgblen]);
      }
      else {
        this.putByteArray(14, this.jfifThumbnailRGB);
      }
    }
    
    this.trimData();
    this.dataInSync = true;
  }

//=============================================================================
//=============================================================================
}
