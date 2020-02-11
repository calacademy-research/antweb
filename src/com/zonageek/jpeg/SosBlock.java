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
public class SosBlock extends JpegBlock {
  //===========================================================================
  public SosBlock() throws JpegException {
    super(Jpeg.MARKER_SOS);
    throw new JpegException("Can't create a new SOS block");
  }
  
  //===========================================================================
  public SosBlock(byte[] data, Jpeg jpeg) throws JpegException {
    super(Jpeg.MARKER_SOS, data, jpeg);
  }

  //===========================================================================
  public HashMap getInformation() {
    HashMap info = super.getInformation();
    
    info.put("Jpeg Block", "SOS");
    //info.remove("Jpeg Block Length");

    return info;
  }

  //===========================================================================
  public void parseData() throws JpegException {
    JpegCodec codec = this.getCodec();
    if (codec != null) {
      int pos = 0;
      int i;
      int aux;
  
      int partialLength = this.getShort(pos);
      pos += 2;
      
      int channelCount = this.getByte(pos);
      pos += 1;
      
      for (i = 0; i < channelCount; i++) {
        int channelId = this.getByte(pos);
        pos += 1;
        aux = this.getByte(pos);
        pos += 1;
        int dcTable = (aux & 0xF0) >> 4;
        int acTable = (aux & 0x0F) >> 0;
        
        codec.setChannelTables(channelId, dcTable, acTable);
      }
  
      int ss = this.getByte(pos);
      pos += 1;
      
      int se = this.getByte(pos);
      pos += 1;
      
      int ah = this.getByte(pos);
      pos += 1;
      int al = (ah & 0x0F) >> 0;
      ah = (ah & 0xF0) >> 4;
      codec.setParameters(ss, se, ah, al);
      
      if (codec.getEncoding() == JpegCodec.ENCODING_BASELINE) {
        
      }
      else {
        // TODO read data in other encodings, progressive in particular
      }
    }
  }

  //===========================================================================
/*  public void createData() throws JpegException {
    this.newData();
    int pos = 0;
    
    this.trimData();
    this.dataInSync = true;
  }
*/
//=============================================================================
//=============================================================================
}
