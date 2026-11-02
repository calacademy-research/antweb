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

package com.zonageek.jpeg.codec;


//=============================================================================
//=============================================================================
/**
 * Part of the bundled third-party JPEG/EXIF reader com.zonageek.jpeg (Sebastian Delmont, 2002, BSD-style licence in
 * the comment at the top of this file, kept as it is). AntWeb uses the package only to read EXIF data: util/Exif
 * calls Jpeg.readHeaders() and getExifBlock() for the image page (bigPicture-body.jsp: the EXIF section for
 * administrators, and the photographer when the image has no artist); SpecimenImage.setMetadata() and
 * ImageDb.getExifData() also read it, but nothing calls them (2026-11-02). One colour channel of the codec
 * (JpegCodec): its id, sampling factors and the ids of its quantization and Huffman tables. Made only by JpegCodec,
 * which AntWeb never activates. Docs: docs/flows/specimen-and-image-pages.md (EXIF for admins). Tests: ExifTextTest,
 * tests/e2e/test_exif_view.py.
 */
public class JpegChannel {
  protected JpegCodec codec;
  protected int id;
  protected int hSamples;
  protected int vSamples;
  protected int qTableId;
  protected int dcTableId;
  protected int acTableId;
  protected int[] qTable;

  //===========================================================================
  public JpegChannel(int id, JpegCodec codec) {
    this.id = id;
    this.codec = codec;
    this.hSamples = 0;
    this.vSamples = 0;
    this.qTableId = 0;
    this.dcTableId = 0;
    this.acTableId = 0;
    this.qTable = null;
  }
  
//=============================================================================
//=============================================================================
}