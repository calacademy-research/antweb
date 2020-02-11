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
import com.zonageek.jpeg.codec.JpegCodec;

//=============================================================================
//=============================================================================
public class DqtBlock extends JpegBlock {
  //===========================================================================
  public DqtBlock() throws JpegException {
    super(Jpeg.MARKER_DQT);
    throw new JpegException("Can't create a new DQT block");
  }
  
  //===========================================================================
  public DqtBlock(byte[] data, Jpeg jpeg) throws JpegException {
    super(Jpeg.MARKER_DQT, data, jpeg);
  }

  //===========================================================================
  public HashMap getInformation() {
    HashMap info = super.getInformation();
    
    info.put("Jpeg Block", "DQT");
    //info.remove("Jpeg Block Length");
    
    return info;
  }

  //===========================================================================
  public void parseData() throws JpegException {
    int pos = 0;

    JpegCodec codec = this.getCodec();
    if (codec != null) {
      while (pos < this.length) {    
        int index;
        boolean useBytes;
      
        index = this.getByte(pos);
        pos += 1;
        if (index >= 16) {
          useBytes = false;
        }
        else {
          useBytes = true;
        }
        index = index & 0x0F;    
  
        int[] table = new int[JpegCodec.DCT_ELEMENT_COUNT];
        
        for (int i = 0; i < JpegCodec.DCT_ELEMENT_COUNT; i++) {
          if (useBytes) {
            table[JpegCodec.JPEG_NATURAL_ORDER[i]] = this.getByte(pos);
            pos += 1;
          }
          else {
            table[JpegCodec.JPEG_NATURAL_ORDER[i]] = this.getShort(pos);
            pos += 2;
          }
        }
  
        codec.setQTable(index, table);
//      this.invertTableV(index);
      }
//    this.dataInSync = false;
    }
  }

  //===========================================================================
  public void createData() throws JpegException {
    JpegCodec codec = this.getCodec();
    if (codec != null) {
      this.newData();
      int pos = 0;
    
      for (int id = 0; id < JpegCodec.MAX_QTABLES; id++) {
        int[] table = (int[]) codec.getQTable(id);
        if (table != null) {
          boolean useBytes = true;
          int i;
          for (i = 0; i < table.length; i++) {
            if (table[i] > 255)
              useBytes = false;
          }
  
          if (useBytes)
            this.putByte(pos, id);
          else
            this.putByte(pos, id + 16);
          pos += 1;
            
          for (i = 0; i < JpegCodec.DCT_ELEMENT_COUNT; i++) {
            if (useBytes) {
              this.putByte(pos, table[JpegCodec.JPEG_NATURAL_ORDER[i]]);
              pos += 1;
            }
            else {
              this.putShort(pos, table[JpegCodec.JPEG_NATURAL_ORDER[i]]);
              pos += 2;
            }
          }
        }
      }
    
      this.trimData();
    }
    this.dataInSync = true;
  }

//=============================================================================
//=============================================================================
}
