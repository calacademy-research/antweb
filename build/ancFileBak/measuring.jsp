<%@ page errorPage = "/error.jsp" %>
\<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
<%@ page import="org.calacademy.antweb.Login" %>
<%@ page import="org.calacademy.antweb.Utility" %>
<%@ page import="org.calacademy.antweb.AncFile" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<% String domainApp = (new Utility()).getDomainApp(); %>
<%@include file="/common/antweb-defs.jsp" %>
<tiles:insert beanName="antweb.default" beanScope="request" flush="true">	
<tiles:put name="title" value="Measuring Ants" />	
<tiles:put name="body-content" type="string">
<div id="page_contents">	   
    <h1>Measuring Ants</h1>
    <div class="clear"></div>
    <div class="page_divider"></div>
</div>
<div id="page_data">
<div id="overview_data">	   	   
    <p align="center">
        <br />
        <strong>Guide to Measuring the Ant Genera of Madagascar</strong></p>
    <p align="center">
        <strong>Brian Fisher and Kelly Herbinson</strong></p>
    <p style="&gt;
        &lt;strong&gt;How to use this guide&lt;/strong&gt;: This guide was created to assist in the process of measuring ants for morphometric analyses.&nbsp; It contains detailed descriptions and figures for each measurement, and lists which measurements are recommended for each Malagasy ant genus. The ?Basic Measurements? section lists measurements that should be used for all ant genera.&nbsp; The ?Additional Measurements? section lists supplementary measurements that may be useful for a specific genus.&nbsp; The Malagasy Genera table lists which supplementary measurements may be useful for each genus.&nbsp; In addition, references are provided for each genus (when available) for further information.&lt;/p&gt;
    &lt;p style=">
        &nbsp;</p>
    <p>
        <b>&nbsp;<a href="http://www.antweb.org/madagascar/Measuring%20figures.pdf" target="_blank">Measurement Figures&nbsp;</a></b></p>
    <p>
        &nbsp;</p>
    <p>
        <strong>Basic Measurements for Workers, Males and Queens</strong></p>
    <p>
        &nbsp;</p>
    <table border="1" cellpadding="0" cellspacing="0" width="624">
        <tbody>
            <tr>
                <td style="width:47px;height:12px;">
                    <p align="center">
                        <strong>EL</strong></p>
                </td>
                <td style="width:107px;height:12px;">
                    <p>
                        Eye Length</p>
                </td>
                <td style="width:467px;height:12px;">
                    <p>
                        Maximum diameter of eye (Onoyama 1989). <strong>Figure 1</strong></p>
                </td>
            </tr>
            <tr>
                <td style="width:47px;height:28px;">
                    <p align="center">
                        <strong>FL</strong></p>
                </td>
                <td style="width:107px;height:28px;">
                    <p>
                        Femur Length</p>
                </td>
                <td style="width:467px;height:28px;">
                    <p>
                        Length of the profemur measured along its long axis in posterior view (Ward). Taken only from hind leg. <strong>Figure 2</strong></p>
                </td>
            </tr>
            <tr>
                <td style="width:47px;height:27px;">
                    <p align="center">
                        <strong>HL</strong></p>
                </td>
                <td style="width:107px;height:27px;">
                    <p>
                        Head Length</p>
                </td>
                <td style="width:467px;height:27px;">
                    <p>
                        In a straight line from anterior clypeal margin to the mid-point of the occipital margin (Bolton).&nbsp; <strong>Figure 3</strong></p>
                </td>
            </tr>
            <tr>
                <td style="width:47px;height:13px;">
                    <p align="center">
                        <strong>HW</strong></p>
                </td>
                <td style="width:107px;height:13px;">
                    <p>
                        Head Width</p>
                </td>
                <td style="width:467px;height:13px;">
                    <p>
                        Maximum width behind the eyes in full-face view (Bolton).&nbsp; <strong>Figure 3</strong></p>
                </td>
            </tr>
            <tr>
                <td style="width:47px;height:26px;">
                    <p align="center">
                        <strong>HW (</strong><strong>male</strong><strong>)</strong></p>
                </td>
                <td style="width:107px;height:26px;">
                    <p>
                        Head Width (male)</p>
                </td>
                <td style="width:467px;height:26px;">
                    <p>
                        FOR MALES: Maximum width INCLUDING the eyes in full-face view. <strong>Figure 5</strong></p>
                </td>
            </tr>
            <tr>
                <td style="width:47px;height:42px;">
                    <p align="center">
                        <strong>ML</strong></p>
                </td>
                <td style="width:107px;height:42px;">
                    <p>
                        Mandible Length</p>
                </td>
                <td style="width:467px;height:42px;">
                    <p>
                        The straight-line length of the mandible at full closure from the mandibular apex to the clypeal margin, or to the transverse line connecting the anterior-most points in those taxa where the margin is concave medially. Males excepted.&nbsp; <strong>Figure 3</strong></p>
                </td>
            </tr>
            <tr>
                <td style="width:47px;height:13px;">
                    <p align="center">
                        <strong>PW</strong></p>
                </td>
                <td style="width:107px;height:13px;">
                    <p>
                        Pronotal Width</p>
                </td>
                <td style="width:467px;height:13px;">
                    <p>
                        Maximum width in dorsal view (Bolton).&nbsp; Usually for workers only.&nbsp;&nbsp; <strong>Figure 2</strong></p>
                </td>
            </tr>
            <tr>
                <td style="width:47px;height:27px;">
                    <p align="center">
                        <strong>SL</strong></p>
                </td>
                <td style="width:107px;height:27px;">
                    <p>
                        Scape Length</p>
                </td>
                <td style="width:467px;height:27px;">
                    <p>
                        Straight line length from base to apex, excluding any basal constriction (Bolton). <strong>Figure 3</strong></p>
                </td>
            </tr>
            <tr>
                <td style="width:47px;height:28px;">
                    <p align="center">
                        <strong>WL</strong></p>
                </td>
                <td style="width:107px;height:28px;">
                    <p>
                        Weber&rsquo;s Length</p>
                </td>
                <td style="width:467px;height:28px;">
                    <p>
                        Maximum diagonal distance from base of anterior slope of pronotum (excluding cervix) to metapleural lobe (Onoyama 1989). <strong>Figure 4</strong></p>
                </td>
            </tr>
        </tbody>
    </table>
    <p align="center">
        <strong>Additional Measurements</strong></p>
    <table border="1" cellpadding="0" cellspacing="0" width="625">
        <tbody>
            <tr>
                <td style="width:60px;height:26px;">
                    <p align="center">
                        <st1:stockticker w:st="on"><strong>AEP</strong></st1:stockticker></p>
                </td>
                <td style="width:95px;height:26px;">
                    <p>
                        Anterior Eye Position</p>
                </td>
                <td style="width:468px;height:26px;">
                    <p>
                        Distance from the anterior most point of the eye to the anterior clypeal margin (Bolton unpublished) . <strong>Figure 6</strong></p>
                </td>
            </tr>
            <tr>
                <td style="width:60px;height:13px;">
                    <p align="center">
                        <strong>EM</strong></p>
                </td>
                <td style="width:95px;height:13px;">
                    <p>
                        Eye to Mandible</p>
                </td>
                <td style="width:468px;height:13px;">
                    <p>
                        Distance from base of compound eye to the mandibular insertion. <strong>Figure 7</strong></p>
                </td>
            </tr>
            <tr>
                <td style="width:60px;height:26px;">
                    <p align="center">
                        <strong>FCD1</strong></p>
                </td>
                <td style="width:95px;height:26px;">
                    <p>
                        Frontal Carinal Distance (one)</p>
                </td>
                <td style="width:468px;height:26px;">
                    <p>
                        Maximum distance between frontal carinae in full face view (Ward 2005). <strong>Figure 8</strong></p>
                </td>
            </tr>
            <tr>
                <td style="width:60px;height:26px;">
                    <p align="center">
                        <strong>FCD2</strong></p>
                </td>
                <td style="width:95px;height:26px;">
                    <p>
                        Frontal Carinal Distance (Two)</p>
                </td>
                <td style="width:468px;height:26px;">
                    <p>
                        Minimum distance between frontal carinae in full face view (Sandor and Balint 2004). <strong>Figure 9</strong></p>
                </td>
            </tr>
            <tr>
                <td style="width:60px;height:12px;">
                    <p align="center" style="margin-left:.5in;">
                        <strong>FW</strong></p>
                </td>
                <td style="width:95px;height:12px;">
                    <p>
                        Femur Width</p>
                </td>
                <td style="width:468px;height:12px;">
                    <p>
                        Maximum width of the hind femur in lateral view.&nbsp; <strong>Figure 10</strong></p>
                </td>
            </tr>
            <tr>
                <td style="width:60px;height:41px;">
                    <p align="center">
                        <strong>HW (upper)</strong></p>
                </td>
                <td style="width:95px;height:41px;">
                    <p>
                        Upper head width</p>
                </td>
                <td style="width:468px;height:41px;">
                    <p>
                        In full face view, the widest part of the upper portion (occipital lobes) of the head. <strong>Figure 11</strong></p>
                </td>
            </tr>
            <tr>
                <td style="width:60px;height:34px;">
                    <p align="center">
                        <strong>IOD</strong></p>
                </td>
                <td style="width:95px;height:34px;">
                    <p>
                        Inter Ocular Distance</p>
                </td>
                <td style="width:468px;height:34px;">
                    <p>
                        The distance between the inner margins of the eyes at their mid-length in full-face view (Bolton 1975).&nbsp; <strong>Figure 12</strong></p>
                </td>
            </tr>
            <tr>
                <td style="width:60px;height:68px;">
                    <p align="center">
                        <strong>LS4</strong></p>
                </td>
                <td style="width:95px;height:68px;">
                    <p>
                        Length of Abdominal Sternite Four</p>
                </td>
                <td style="width:468px;height:68px;">
                    <p>
                        Maximum longitudinal length of the fourth abdominal sternum, measured in lateral view from the anterior margin to the posterolateral extremity.&nbsp; This measurement is taken perpendicular to a line drawn through the anteroventral and anterodorsal margins of the fourth abdominal segment, excluding the acrosclerites.&nbsp; (Ward 1988).&nbsp; <strong>Figure 13</strong></p>
                </td>
            </tr>
            <tr>
                <td style="width:60px;height:13px;">
                    <p align="center">
                        <strong>LT4</strong></p>
                </td>
                <td style="width:95px;height:13px;">
                    <p>
                        Length of Abdominal Tergum Four.</p>
                </td>
                <td style="width:468px;height:13px;">
                    <p>
                        Length of fourth abdominal tergum, measured in lateral view, from the anterior margin (excluding the acrotergite) to the posterior extremity (Ward 1988). <strong>Figure 13</strong></p>
                </td>
            </tr>
            <tr>
                <td style="width:60px;height:27px;">
                    <p align="center">
                        <strong>MM</strong></p>
                </td>
                <td style="width:95px;height:27px;">
                    <p>
                        Mesonotal Hairs</p>
                </td>
                <td style="width:468px;height:27px;">
                    <p>
                        Number of erect macrochaetae on mesonotum, left of sagittal plane (Trager 1983)</p>
                </td>
            </tr>
            <tr>
                <td style="width:60px;height:13px;">
                    <p align="center">
                        <strong>MTL</strong></p>
                </td>
                <td style="width:95px;height:13px;">
                    <p>
                        Left Mandible Teeth</p>
                </td>
                <td style="width:468px;height:13px;">
                    <p>
                        Number of teeth and denticles on the left mandible.</p>
                </td>
            </tr>
            <tr>
                <td style="width:60px;height:12px;">
                    <p align="center">
                        <st1:stockticker w:st="on"><strong>MTR</strong></st1:stockticker></p>
                </td>
                <td style="width:95px;height:12px;">
                    <p>
                        Right Mandible Teeth</p>
                </td>
                <td style="width:468px;height:12px;">
                    <p>
                        Number of teeth and denticles on the right mandible.</p>
                </td>
            </tr>
            <tr>
                <td style="width:60px;height:26px;">
                    <p align="center">
                        <st1:stockticker w:st="on"><strong>PEP</strong></st1:stockticker></p>
                </td>
                <td style="width:95px;height:26px;">
                    <p>
                        Posterior Eye Position</p>
                </td>
                <td style="width:468px;height:26px;">
                    <p>
                        Distance from the posterior most point of the eye to the posterior clypeal margin (Bolton unpublished). <strong>Figure 6</strong></p>
                </td>
            </tr>
            <tr>
                <td style="width:60px;height:26px;">
                    <p align="center">
                        <strong>PM</strong></p>
                </td>
                <td style="width:95px;height:26px;">
                    <p>
                        Pronotal Hairs</p>
                </td>
                <td style="width:468px;height:26px;">
                    <p>
                        Number of erect macrochaetae on pronotum, left of sagittal plane (Trager 1983)</p>
                </td>
            </tr>
            <tr>
                <td style="width:60px;height:27px;">
                    <p align="center">
                        <strong>PML</strong></p>
                </td>
                <td style="width:95px;height:27px;">
                    <p>
                        Promesonotal Length</p>
                </td>
                <td style="width:468px;height:27px;">
                    <p>
                        Dorsal length of promesonotum excluding anterior declivity (Eguchi 1998).&nbsp; <strong>Figure 14</strong></p>
                </td>
            </tr>
            <tr>
                <td style="width:60px;height:26px;">
                    <p align="center">
                        <strong>PMW</strong></p>
                </td>
                <td style="width:95px;height:26px;">
                    <p>
                        Promesonotal Width</p>
                </td>
                <td style="width:468px;height:26px;">
                    <p>
                        Dorsal width of promesonotum excluding mesothoracic spiracles (Eguchi 1998).&nbsp; (Use instead of Pronotal Width).&nbsp; <strong>Figure 14</strong></p>
                </td>
            </tr>
            <tr>
                <td style="width:60px;height:41px;">
                    <p align="center">
                        <st1:stockticker w:st="on"><strong>PPL</strong></st1:stockticker></p>
                </td>
                <td style="width:95px;height:41px;">
                    <p>
                        Postpetiole Length</p>
                </td>
                <td style="width:468px;height:41px;">
                    <p>
                        Length of the postpetiole, measured in lateral view, from the anterior peduncle of the postpetiole to the point of contact with the fourth abdominal tergum, excluding the pretergite (Ward) <strong>Figure 15</strong></p>
                </td>
            </tr>
            <tr>
                <td style="width:60px;height:26px;">
                    <p align="center">
                        <strong>PPW</strong></p>
                </td>
                <td style="width:95px;height:26px;">
                    <p>
                        Postpetiolar Width</p>
                </td>
                <td style="width:468px;height:26px;">
                    <p>
                        Maximum width of the postpetiole (third abdominal segment) in dorsal view (Ward 2005).&nbsp; <strong>Figure 16</strong></p>
                </td>
            </tr>
            <tr>
                <td style="width:60px;height:67px;">
                    <p align="center">
                        <strong>PSL</strong></p>
                </td>
                <td style="width:95px;height:67px;">
                    <p>
                        Promesonotal Shield Length</p>
                </td>
                <td style="width:468px;height:67px;">
                    <p>
                        Promesonotal Shield Length; overall maximum length of shield, dorsal view, measured on axis of midline, between transverse level of anteriormost extremity to transverse level of posteriormost extrem-ity, including spines and spine-like or lamellar ex-tensions (measured to level of apex of pronotal collar when it is the anterior extremity). <em>Only Meranoplus</em> (Taylor 2006) <strong>Figure 17</strong></p>
                </td>
            </tr>
            <tr>
                <td style="width:60px;height:55px;">
                    <p align="center">
                        <strong>PSW</strong></p>
                </td>
                <td style="width:95px;height:55px;">
                    <p>
                        Promesonotal Shield Width</p>
                </td>
                <td style="width:468px;height:55px;">
                    <p>
                        &nbsp;Promesonotal Shield Width; overall maximum width of shield, dorsal view, measured along right-trans-verse axis spanning its lateral extremities (usually the apices of opposite bilateral spines, or spine-like or lamellar extensions). <em>Only Meranoplus</em>.&nbsp; (Taylor 2006) <strong>Figure 17</strong></p>
                </td>
            </tr>
            <tr>
                <td style="width:60px;height:12px;">
                    <p align="center">
                        <strong>PTL</strong></p>
                </td>
                <td style="width:95px;height:12px;">
                    <p>
                        Petiole Length</p>
                </td>
                <td style="width:468px;height:12px;">
                    <p>
                        Midline length of petiolar node in dorsal view (Onoyama 1999). <strong>Figures 18, 20</strong></p>
                </td>
            </tr>
            <tr>
                <td style="width:60px;height:13px;">
                    <p align="center">
                        <strong>PTW</strong></p>
                </td>
                <td style="width:95px;height:13px;">
                    <p>
                        Petiole Width</p>
                </td>
                <td style="width:468px;height:13px;">
                    <p>
                        Maximum width of petiole in dorsal view (Onoyama 1999).&nbsp; <strong>Figure 19</strong></p>
                </td>
            </tr>
            <tr>
                <td style="width:60px;height:13px;">
                    <p align="center">
                        <strong>SM</strong></p>
                </td>
                <td style="width:95px;height:13px;">
                    <p>
                        Scape Hairs</p>
                </td>
                <td style="width:468px;height:13px;">
                    <p>
                        Number of erect macrochaetae on scape (Trager 1983)</p>
                </td>
            </tr>
            <tr>
                <td style="width:60px;height:26px;">
                    <p align="center">
                        <strong>SPD</strong></p>
                </td>
                <td style="width:95px;height:26px;">
                    <p>
                        Distance between Spines</p>
                </td>
                <td style="width:468px;height:26px;">
                    <p>
                        Distance between the tips of the two propodeal spines (Umphrey 1996). <strong>Figure 21</strong></p>
                </td>
            </tr>
            <tr>
                <td style="width:60px;height:26px;">
                    <p align="center">
                        <strong>SPL</strong></p>
                </td>
                <td style="width:95px;height:26px;">
                    <p>
                        Propodeal Spine Length</p>
                </td>
                <td style="width:468px;height:26px;">
                    <p>
                        Length of propodeal spine, measured from center of the propodeal spiracle to the tip of the spine (Umphrey 1996).&nbsp; <strong>Figure 22</strong></p>
                </td>
            </tr>
            <tr>
                <td style="width:60px;height:27px;">
                    <p align="center" style="margin-left:.5in;">
                        <strong>SPL2</strong></p>
                </td>
                <td style="width:95px;height:27px;">
                    <p>
                        Pronotal Spine Length</p>
                </td>
                <td style="width:468px;height:27px;">
                    <p>
                        Vertical distance between base of spine on its mesial side, and the level of its apex.&nbsp; (<em>Pristomyrmex</em>) (No Figure Available)</p>
                </td>
            </tr>
            <tr>
                <td style="width:60px;height:12px;">
                    <p align="center" style="margin-left:.5in;">
                        <strong>TL</strong></p>
                </td>
                <td style="width:95px;height:12px;">
                    <p>
                        Tibia Length</p>
                </td>
                <td style="width:468px;height:12px;">
                    <p>
                        Maximum length of hind tibia (Umphrey 1996). <strong>Figure 10</strong></p>
                </td>
            </tr>
            <tr>
                <td style="width:60px;height:27px;">
                    <p align="center">
                        <strong>TPH</strong></p>
                </td>
                <td style="width:95px;height:27px;">
                    <p>
                        Taylor&#39;s Height of Petiole</p>
                </td>
                <td style="width:468px;height:27px;">
                    <p>
                        Max. height of petiole in side view from summit of node to extreme lowermost part of subpetiolar process (Taylor 1967)(Onoyama 1989). <strong>Figures 20, 23</strong></p>
                </td>
            </tr>
        </tbody>
    </table>
    <p align="center">
        <strong>Malagasy Genera Table</strong></p>
    <table border="1" cellpadding="0" cellspacing="0" style="width:624px;" width="625">
        <tbody>
            <tr>
                <td style="width:107px;height:17px;">
                    <p>
                        <strong>Genus</strong></p>
                </td>
                <td style="width:312px;height:17px;">
                    <p>
                        <strong>Recommended Additional Measurements</strong></p>
                </td>
                <td style="width:203px;height:17px;">
                    <p>
                        <strong>References</strong></p>
                </td>
            </tr>
            <tr>
                <td style="width:107px;height:18px;">
                    <p>
                        Adetomyrma</p>
                </td>
                <td style="width:312px;height:18px;">
                    <p>
                        PTW, TL&nbsp;</p>
                </td>
                <td style="width:203px;height:18px;">
                    <p>
                        Ward 1994</p>
                </td>
            </tr>
            <tr>
                <td style="width:107px;height:16px;">
                    <p>
                        Amblyopone</p>
                </td>
                <td style="width:312px;height:16px;">
                    <p>
                        PTW, PTL</p>
                </td>
                <td style="width:203px;height:16px;">
                    <p>
                        Onoyama 1999</p>
                </td>
            </tr>
            <tr>
                <td style="width:107px;height:15px;">
                    <p>
                        Anochetus</p>
                </td>
                <td style="width:312px;height:15px;">
                    <p>
                        None</p>
                </td>
                <td style="width:203px;height:15px;">
                    <p>
                        Fisher 2008</p>
                </td>
            </tr>
            <tr>
                <td style="width:107px;height:16px;">
                    <p>
                        Anoplolepis</p>
                </td>
                <td style="width:312px;height:16px;">
                    <p>
                        TL</p>
                </td>
                <td style="width:203px;height:16px;">
                    <p>
                        Agosti 1990</p>
                </td>
            </tr>
            <tr>
                <td style="width:107px;height:15px;">
                    <p>
                        Aphaenogaster</p>
                </td>
                <td style="width:312px;height:15px;">
                    <p>
                        FW, TL, SPD, SPL&nbsp; Also: PTW, PTL, PPW, <st1:stockticker w:st="on">PPL</st1:stockticker></p>
                </td>
                <td style="width:203px;height:15px;">
                    <p>
                        Umphrey 1996</p>
                </td>
            </tr>
            <tr>
                <td style="width:107px;height:16px;">
                    <p>
                        Brachymyrmex</p>
                </td>
                <td style="width:312px;height:16px;">
                    <p>
                        None</p>
                </td>
                <td style="width:203px;height:16px;">
                    <p>
                        Quiran 2004</p>
                </td>
            </tr>
            <tr>
                <td style="width:107px;height:15px;">
                    <p>
                        Camponotus</p>
                </td>
                <td style="width:312px;height:15px;">
                    <p>
                        TL, FCD1</p>
                </td>
                <td style="width:203px;height:15px;">
                    <p>
                        Shattuck 2005, Ward 2005</p>
                </td>
            </tr>
            <tr>
                <td style="width:107px;height:16px;">
                    <p>
                        Cardiocondyla</p>
                </td>
                <td style="width:312px;height:16px;">
                    <p>
                        FCD1, FCD2, PTW, PTL, PPW, <st1:stockticker w:st="on">PPL</st1:stockticker>, TPH, SPD, SPL</p>
                </td>
                <td style="width:203px;height:16px;">
                    <p>
                        Seifert 2003</p>
                </td>
            </tr>
            <tr>
                <td style="width:107px;height:15px;">
                    <p>
                        Carebara</p>
                </td>
                <td style="width:312px;height:15px;">
                    <p>
                        PTW, PTL, PPW, <st1:stockticker w:st="on">PPL</st1:stockticker>, SPL</p>
                </td>
                <td style="width:203px;height:15px;">
                    <p>
                        None</p>
                </td>
            </tr>
            <tr>
                <td style="width:107px;height:16px;">
                    <p>
                        Cataulacus</p>
                </td>
                <td style="width:312px;height:16px;">
                    <p>
                        TL, IOD</p>
                </td>
                <td style="width:203px;height:16px;">
                    <p>
                        Bolton 1975</p>
                </td>
            </tr>
            <tr>
                <td style="width:107px;height:15px;">
                    <p>
                        Cerapachys</p>
                </td>
                <td style="width:312px;height:15px;">
                    <p>
                        PTW, PTL, PPW, <st1:stockticker w:st="on">PPL</st1:stockticker></p>
                </td>
                <td style="width:203px;height:15px;">
                    <p>
                        Brown 1975</p>
                </td>
            </tr>
            <tr>
                <td style="width:107px;height:15px;">
                    <p>
                        Crematogaster</p>
                </td>
                <td style="width:312px;height:15px;">
                    <p>
                        PTW, PTL, PPW, <st1:stockticker w:st="on">PPL</st1:stockticker>, SPL, TPH (see Fig. 20)</p>
                </td>
                <td style="width:203px;height:15px;">
                    <p>
                        Longino 2003</p>
                </td>
            </tr>
            <tr>
                <td style="width:107px;height:16px;">
                    <p>
                        Discothyrea</p>
                </td>
                <td style="width:312px;height:16px;">
                    <p>
                        PTW, PTL, TPH, LS4, LT4</p>
                </td>
                <td style="width:203px;height:16px;">
                    <p>
                        Zacharias 2004, Ward 1988</p>
                </td>
            </tr>
            <tr>
                <td style="width:107px;height:15px;">
                    <p>
                        Eutetramorium</p>
                </td>
                <td style="width:312px;height:15px;">
                    <p>
                        FCD2, PTW, PTL, PPW, TPH, SPD, SPL</p>
                </td>
                <td style="width:203px;height:15px;">
                    <p>
                        None</p>
                </td>
            </tr>
            <tr>
                <td style="width:107px;height:16px;">
                    <p>
                        Hypoponera</p>
                </td>
                <td style="width:312px;height:16px;">
                    <p>
                        PTW, PTL, PPW, TPH</p>
                </td>
                <td style="width:203px;height:16px;">
                    <p>
                        Onoyama 1989</p>
                </td>
            </tr>
            <tr>
                <td style="width:107px;height:15px;">
                    <p>
                        Leptogenys</p>
                </td>
                <td style="width:312px;height:15px;">
                    <p>
                        PTW, PTL</p>
                </td>
                <td style="width:203px;height:15px;">
                    <p>
                        Bolton 1975</p>
                </td>
            </tr>
            <tr>
                <td style="width:107px;height:16px;">
                    <p>
                        Melissotarsus</p>
                </td>
                <td style="width:312px;height:16px;">
                    <p>
                        PTW, PTL</p>
                </td>
                <td style="width:203px;height:16px;">
                    <p>
                        Pers. Op.&nbsp; Bolton 1981</p>
                </td>
            </tr>
            <tr>
                <td style="width:107px;height:15px;">
                    <p>
                        Meranoplus</p>
                </td>
                <td style="width:312px;height:15px;">
                    <p>
                        PTW, PTL, PPW, TPH, SPD, SPL, PSL, PSW</p>
                </td>
                <td style="width:203px;height:15px;">
                    <p>
                        Schodl 1998, Taylor 2006</p>
                </td>
            </tr>
            <tr>
                <td style="width:107px;height:16px;">
                    <p>
                        Metapone</p>
                </td>
                <td style="width:312px;height:16px;">
                    <p>
                        PTW, PPW, PML, PMW</p>
                </td>
                <td style="width:203px;height:16px;">
                    <p>
                        Alpert 2007</p>
                </td>
            </tr>
            <tr>
                <td style="width:107px;height:15px;">
                    <p>
                        Monomorium</p>
                </td>
                <td style="width:312px;height:15px;">
                    <p>
                        None, PTW, PTL, PPW, <st1:stockticker w:st="on">PPL</st1:stockticker></p>
                </td>
                <td style="width:203px;height:15px;">
                    <p>
                        Heterick 2006, (pers. op.)</p>
                </td>
            </tr>
            <tr>
                <td style="width:107px;height:16px;">
                    <p>
                        Mystrium</p>
                </td>
                <td style="width:312px;height:16px;">
                    <p>
                        None, PTL, PTW</p>
                </td>
                <td style="width:203px;height:16px;">
                    <p>
                        Bihn et al. 2007, (pers. op.)</p>
                </td>
            </tr>
            <tr>
                <td style="width:107px;height:15px;">
                    <p>
                        Nesomyrmex</p>
                </td>
                <td style="width:312px;height:15px;">
                    <p>
                        PTW, PTL, PPW, <st1:stockticker w:st="on">PPL</st1:stockticker>, SPL</p>
                </td>
                <td style="width:203px;height:15px;">
                    <p>
                        None, (pers. op.)</p>
                </td>
            </tr>
            <tr>
                <td style="width:107px;height:16px;">
                    <p>
                        Ochetellus</p>
                </td>
                <td style="width:312px;height:16px;">
                    <p>
                        None</p>
                </td>
                <td style="width:203px;height:16px;">
                    <p>
                        None</p>
                </td>
            </tr>
            <tr>
                <td style="width:107px;height:15px;">
                    <p>
                        Odontomachus</p>
                </td>
                <td style="width:312px;height:15px;">
                    <p>
                        HW (upper), MTL, <st1:stockticker w:st="on">MTR</st1:stockticker></p>
                </td>
                <td style="width:203px;height:15px;">
                    <p>
                        Fisher 2008</p>
                </td>
            </tr>
            <tr>
                <td style="width:107px;height:15px;">
                    <p>
                        Pachycondyla</p>
                </td>
                <td style="width:312px;height:15px;">
                    <p>
                        PTW, PTL, TPH, TL</p>
                </td>
                <td style="width:203px;height:15px;">
                    <p>
                        Wild 2005</p>
                </td>
            </tr>
            <tr>
                <td style="width:107px;height:16px;">
                    <p>
                        Paratrechina, Nylanderia, Paraparatrechina</p>
                </td>
                <td style="width:312px;height:16px;">
                    <p>
                        SM, PM, MM</p>
                </td>
                <td style="width:203px;height:16px;">
                    <p>
                        Trager 1983</p>
                </td>
            </tr>
            <tr>
                <td style="width:107px;height:15px;">
                    <p>
                        Pheidole</p>
                </td>
                <td style="width:312px;height:15px;">
                    <p>
                        None</p>
                </td>
                <td style="width:203px;height:15px;">
                    <p>
                        Eguchi 2001</p>
                </td>
            </tr>
            <tr>
                <td style="width:107px;height:16px;">
                    <p>
                        Pilotrochus</p>
                </td>
                <td style="width:312px;height:16px;">
                    <p>
                        TL, PTL</p>
                </td>
                <td style="width:203px;height:16px;">
                    <p>
                        Brown 1978</p>
                </td>
            </tr>
            <tr>
                <td style="width:107px;height:15px;">
                    <p>
                        Plagiolepis</p>
                </td>
                <td style="width:312px;height:15px;">
                    <p>
                        None</p>
                </td>
                <td style="width:203px;height:15px;">
                    <p>
                        None</p>
                </td>
            </tr>
            <tr>
                <td style="width:107px;height:16px;">
                    <p>
                        Platythyrea</p>
                </td>
                <td style="width:312px;height:16px;">
                    <p>
                        EM, PTW, PTL</p>
                </td>
                <td style="width:203px;height:16px;">
                    <p>
                        Brown 1975</p>
                </td>
            </tr>
            <tr>
                <td style="width:107px;height:15px;">
                    <p>
                        Polyrhachis</p>
                </td>
                <td style="width:312px;height:15px;">
                    <p>
                        TL</p>
                </td>
                <td style="width:203px;height:15px;">
                    <p>
                        Dorow 1995</p>
                </td>
            </tr>
            <tr>
                <td style="width:107px;height:16px;">
                    <p>
                        Prionopelta</p>
                </td>
                <td style="width:312px;height:16px;">
                    <p>
                        PTW, PTL (no literature, used Amblyopone)</p>
                </td>
                <td style="width:203px;height:16px;">
                    <p>
                        None</p>
                </td>
            </tr>
            <tr>
                <td style="width:107px;height:15px;">
                    <p>
                        Pristomyrmex</p>
                </td>
                <td style="width:312px;height:15px;">
                    <p>
                        SPL2, SPL, PTL, TPH</p>
                </td>
                <td style="width:203px;height:15px;">
                    <p>
                        Taylor 1965a</p>
                </td>
            </tr>
            <tr>
                <td style="width:107px;height:16px;">
                    <p>
                        Probolomyrmex</p>
                </td>
                <td style="width:312px;height:16px;">
                    <p>
                        TL, PTW, TPH, PTL</p>
                </td>
                <td style="width:203px;height:16px;">
                    <p>
                        Fisher 2007</p>
                </td>
            </tr>
            <tr>
                <td style="width:107px;height:15px;">
                    <p>
                        Proceratium</p>
                </td>
                <td style="width:312px;height:15px;">
                    <p>
                        LS4, LT4</p>
                </td>
                <td style="width:203px;height:15px;">
                    <p>
                        Fisher 2005, Ward 1988</p>
                </td>
            </tr>
            <tr>
                <td style="width:107px;height:16px;">
                    <p>
                        Pseudolasius</p>
                </td>
                <td style="width:312px;height:16px;">
                    <p>
                        None</p>
                </td>
                <td style="width:203px;height:16px;">
                    <p>
                        LaPolla 2004</p>
                </td>
            </tr>
            <tr>
                <td style="width:107px;height:15px;">
                    <p>
                        Pyramica</p>
                </td>
                <td style="width:312px;height:15px;">
                    <p>
                        PTL, <st1:stockticker w:st="on">PPL</st1:stockticker></p>
                </td>
                <td style="width:203px;height:15px;">
                    <p>
                        Deyrup 2006, Zheng-Hui 2004</p>
                </td>
            </tr>
            <tr>
                <td style="width:107px;height:16px;">
                    <p>
                        Simopone</p>
                </td>
                <td style="width:312px;height:16px;">
                    <p>
                        PTL, PTW, <st1:stockticker w:st="on">PPL</st1:stockticker>, PPW</p>
                </td>
                <td style="width:203px;height:16px;">
                    <p>
                        Taylor 1965b</p>
                </td>
            </tr>
            <tr>
                <td style="width:107px;height:15px;">
                    <p>
                        Solenopsis</p>
                </td>
                <td style="width:312px;height:15px;">
                    <p>
                        None</p>
                </td>
                <td style="width:203px;height:15px;">
                    <p>
                        Trager 1991</p>
                </td>
            </tr>
            <tr>
                <td style="width:107px;height:15px;">
                    <p>
                        Strumigenys</p>
                </td>
                <td style="width:312px;height:15px;">
                    <p>
                        None</p>
                </td>
                <td style="width:203px;height:15px;">
                    <p>
                        Yoshimura et al. 2007</p>
                </td>
            </tr>
            <tr>
                <td style="width:107px;height:16px;">
                    <p>
                        Tapinoma</p>
                </td>
                <td style="width:312px;height:16px;">
                    <p>
                        <st1:stockticker w:st="on">AEP</st1:stockticker>, <st1:stockticker w:st="on">PEP</st1:stockticker></p>
                </td>
                <td style="width:203px;height:16px;">
                    <p>
                        (?)</p>
                </td>
            </tr>
            <tr>
                <td style="width:107px;height:15px;">
                    <p>
                        Technomyrmex</p>
                </td>
                <td style="width:312px;height:15px;">
                    <p>
                        None</p>
                </td>
                <td style="width:203px;height:15px;">
                    <p>
                        None</p>
                </td>
            </tr>
            <tr>
                <td style="width:107px;height:16px;">
                    <p>
                        Terataner</p>
                </td>
                <td style="width:312px;height:16px;">
                    <p>
                        None</p>
                </td>
                <td style="width:203px;height:16px;">
                    <p>
                        None</p>
                </td>
            </tr>
            <tr>
                <td style="width:107px;height:15px;">
                    <p>
                        Tetramorium</p>
                </td>
                <td style="width:312px;height:15px;">
                    <p>
                        FCD2, PTW, PTL, PPW, <st1:stockticker w:st="on">PPL</st1:stockticker>, SPD, SPL, PMW, TPH</p>
                </td>
                <td style="width:203px;height:15px;">
                    <p>
                        Sandor et al. 2004, Gusten 2006</p>
                </td>
            </tr>
            <tr>
                <td style="width:107px;height:16px;">
                    <p>
                        Tetraponera</p>
                </td>
                <td style="width:312px;height:16px;">
                    <p>
                        FW, TL, PTL, PTW, TPH</p>
                </td>
                <td style="width:203px;height:16px;">
                    <p>
                        Ward 2001</p>
                </td>
            </tr>
            <tr>
                <td style="width:107px;height:15px;">
                    <p>
                        Vollenhovia</p>
                </td>
                <td style="width:312px;height:15px;">
                    <p>
                        PTL, PTW, <st1:stockticker w:st="on">PPL</st1:stockticker>, PPW</p>
                </td>
                <td style="width:203px;height:15px;">
                    <p>
                        None</p>
                </td>
            </tr>
            <tr>
                <td style="width:107px;height:16px;">
                    <p>
                        &ldquo;Vitsika&rdquo;</p>
                </td>
                <td style="width:312px;height:16px;">
                    <p>
                        FW, TL, SPD, SPL</p>
                </td>
                <td style="width:203px;height:16px;">
                    <p>
                        None</p>
                </td>
            </tr>
        </tbody>
    </table>
    <p>
        <strong>References</strong></p>
    <p style="margin-left:.25in;">
        Agosti,&nbsp; D.&nbsp; (1990).&nbsp; Review and reclassification of <em>Cataglyphis</em> (Hymenoptera, Formicidae). <em>Journal of Natural History</em>, 24, 1457-1505.&nbsp;</p>
    <p style="margin-left:.25in;">
        Alpert, G.D. (2007).&nbsp;&nbsp; A review of the ant genus <em>Metapone</em> Forel from Madagascar, pp. 8-18.&nbsp; In Snelling, R.R., B.L. Fisher, and P.S. Ward (eds).&nbsp; Advances in ant systematics (Hymenoptera: Formicidae): Homage to E.O. Wilson &ndash; 50 years of contributions.&nbsp; <em>Memoirs of the American Entomological Institute, 80.</em></p>
    <p style="margin-left:.25in;">
        Bihn, J.H. and M. Verhaagh.&nbsp; (2007).&nbsp; A review of the genus Mystrium (Hymenoptera: Formicidae) in the Indo_Australian region.&nbsp; Zootaxa, 1642, 1-12.</p>
    <p style="margin-left:.25in;">
        Brown, W.&nbsp; (1975).&nbsp; Contributions toward a reclassification of the Formicidae.&nbsp; V. Ponerinae, tribes Platythyreini, Cerapachyini, Cylindromyrmecini, Acanthostichini and Aenictogitini.&nbsp; Search Agriculture. 5 (1).</p>
    <p style="margin-left:.25in;">
        Brown, W. L. (1978). An aberrant new genus of myrmicine ant from Madagascar. <em>Psyche</em> 84, 218-224.</p>
    <p style="margin-left:.25in;">
        Dorow, W.H.O, Kohout, R.J.&nbsp; (1995).&nbsp; A review of the subgenus <em>Hemioptica</em> Roger of the genus <em>Polyrhachis</em> Fr. Smith with description of a new species (Hymenoptera: Formicidae: Formicinae).&nbsp; <em>Zool. Med. Leiden</em>, 69, 93-104.</p>
    <p style="margin-left:.25in;">
        Deyrup, M.&nbsp; (2006).&nbsp; Pyramica boltoni, a new species of leaf0litter inhabiting ant from Florida (Hymenoptera: Formicidae: Dacetimi).&nbsp; Florida Entomologist.&nbsp; 89(1), 1-5.</p>
    <p style="margin-left:.25in;">
        Eguchi, K. (2001).&nbsp; A revision of the Bornean species of ant genus <em>Pheidole</em> (Insecta: Hymenoptera: Formicidae: Myrmicinae).&nbsp; <em>Tropics Monograph.</em> Series No. 2.&nbsp;&nbsp;</p>
    <p style="margin-left:.25in;">
        Fisher, B.&nbsp; (2005).&nbsp; A new species of <em>Discothyrea</em> Roger from Mauritius and a new species of <em>Proceratium</em> Roger from Madagascar (Hymenoptera: Formicidae).&nbsp; <em>Proc.Calif. Acad. Sci. </em>56(35), 657-667.</p>
    <p style="margin-left:.25in;">
        Fisher, B. (2007). A new species of <em>Probolomyrmex</em> from Madagascar.&nbsp; Advances in Ant Systematics: Homage to EO Wilson.&nbsp; Eds. Snelling, R., Fisher, B., Ward, P.</p>
    <p style="margin-left:.25in;">
        Fisher, B.L. and M.A. Smith.&nbsp; (2008).&nbsp; A revision of Malagasy species of Anochetus Mayr and Odontomachus Latreille (Hymenoptera: Formicidae).&nbsp; PLoS <st1:stockticker w:st="on">ONE</st1:stockticker>, 3(5), e1787.</p>
    <p style="margin-left:.25in;">
        Gusten, R., Schulz, A. and Sanettra, M.&nbsp; 2006. Redescription of Tetramorium forte Forel, 1904 (Insecta: Hymenoptera: Formicidae), a western Mediterranean ant species.&nbsp; Zootaxa. No. 1310: 1-35.</p>
    <p style="margin-left:.25in;">
        Heterick, B.&nbsp; (2006).&nbsp; A revision of the Malagasy ants belonging to genus Monomorium Mayr, &nbsp;&nbsp;&nbsp;1855 (Hymenoptera: Formicidae).&nbsp; &nbsp;&nbsp;Proc. Calif. Acad. Sci., 57 (3), 69-202.</p>
    <p style="margin-left:.25in;">
        LaPolla, J.S.&nbsp; (2004).&nbsp; Taxonomic review of the ant genus <em>Pseudolasius</em> (Formicidae: Formicinae) in the afrotropical region.&nbsp; <em>J. New York Entomol. Soc. </em>&nbsp;112(2-3), 97-105.&nbsp;</p>
    <p style="margin-left:.25in;">
        Longino, J. (2003). The <em>Crematogaster</em> (Hymenoptera, Formicidae, Myrmicinae) of Costa Rica.&nbsp; <em>Zootaxa,</em> 151, 1-150.</p>
    <p style="margin-left:.25in;">
        Onoyama, K. (1989).&nbsp; Notes on the ants of the genus <em>Hypoponera</em> in Japan (Hymenoptera: Formicidae).&nbsp; <em>Edaphologia,</em> 41, 1-10.</p>
    <p style="margin-left:.25in;">
        Onoyama, K. (1999).&nbsp; A new and newly recorded species of the ant genus <em>Amblyopone</em> (Hymenoptera: Formicidae) from Japan.&nbsp; <em>Entomological Science</em>,&nbsp; 2(1), 157-161.</p>
    <p style="margin-left:.25in;">
        Quiran, E., Martinez, J. and A. Bachmann. (2004). The neotropical genus <em>Brachymyrmex</em> Mayr, 1868 (Hymenoptera: Formicidae) in Argentina.&nbsp; Redescription of the type species, <em>B. patagonicus</em> Mayr, 1868; <em>B. bruchi</em> Forel, 1912 and <em>B. oculatus</em> Santschi, 1919.&nbsp; <em>Acta Zoologca Mexicana</em> (n.s.) 20 (1), 273-285.</p>
    <p style="margin-left:.25in;">
        Sandor, C. and M. Balint. (2004).&nbsp; Redescription of Tetramorium hungaricum Roszler, 1935, a related species of T. caespitum (Linnaeus, 1758) (Hymenoptera: Formicidae). Myrmecologische Nachrichten, 6, 49-59.</p>
    <p style="margin-left:.25in;">
        Schodl, S.&nbsp; (1998).&nbsp; Taxonomic revision of Oriental <em>Meranoplus</em> F. Smith, 1853 (Insecta: Hymenoptera: Formicidae: Myrmicinae).&nbsp; <em>Ann. Naturhist. Mus. Wien</em>.&nbsp; 100b: 361-394.</p>
    <p style="margin-left:.25in;">
        Schodl, S. (2007).&nbsp; Revision of Australian <em>Meranoplus</em>: The Meranoplus Diversus group.&nbsp; <em>Advances in Ant Systematics: Homage to EO Wilson</em>.&nbsp; Eds. Snelling, R., Fisher, B., Ward, P.</p>
    <p style="margin-left:.25in;">
        Seifert, B. (2003).&nbsp; The ant genus <em>Cardiocondyla</em> (Insecta: Hymenoptera: Formicidae)- a taxonomic revision of <em>C. elegans, C. bulgarica, C. batesii, C. nuda, C. shuckardi, C. stambuloffi, C. wroughtonii, C. emeryi</em> and <em>C. minutior</em> species groups.&nbsp; <em>Ann. Naturhist. Mus. Wien, </em>&nbsp;104b, 203-338.&nbsp;</p>
    <p style="margin-left:.25in;">
        Shattuck, S. (2005).&nbsp; Review of the<em> Camponotus aureopilus </em>species-group (Hymenoptera: Formicidae), including a second <em>Camponotus</em> with a metapleural gland. <em>&nbsp;Zootaxa</em> , 903, 1-20.&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</p>
    <p style="margin-left:.25in;">
        Taylor, R.W.&nbsp; (1965a).&nbsp; The Australian ants of the genus <em>Pristomyrmex</em>, with a case of apparent character displacement. <em>&nbsp;Psyche</em>, 72, 35-54.&nbsp; &nbsp;&nbsp;&nbsp;&nbsp;</p>
    <p style="margin-left:.25in;">
        Taylor, R.W. (1965b).&nbsp; Notes on the Indo-Australian ants of the genus <em>Simopone</em> Forel (Hymenoptera:-Formicidae).&nbsp; <em>Psyche</em>, 72, 287-290.</p>
    <p style="margin-left:.25in;">
        Taylor, R.W. (2006).&nbsp; Ants of the genus <em>Meranoplus</em> F. Smith, 1853 (Hymenoptera: Formicidae): Three new species and others from northeastern Australian rainforests.&nbsp; <em>Myrmecologische Nachrichten</em>,&nbsp; 8, 21-29.</p>
    <p style="margin-left:.25in;">
        Trager, J.C.&nbsp; (1991).&nbsp; A revision of the fire ants, <em>Solenopsis geminata</em> group (Hymenoptera: Formicidae: Myrmicinae).&nbsp; <em>J. New York Entomol. Soc. </em>&nbsp;99(2), 141-198.</p>
    <p style="margin-left:.25in;">
        Umphrey, G.&nbsp; (1996).&nbsp; Morphometric discrimination among sibling species in the fulva-rudis-texana complex of the ant genus Aphaenogaster (Hymenoptera: Formicidae).&nbsp; <em>Canadian journal of zoology</em> 74(33), 528-559, National Research Council of Canada.</p>
    <p style="margin-left:.25in;">
        Ward, P.S.&nbsp; (1988).&nbsp; Mesic elements in the western nearctic ant fauna: taxonomic and biological notes on <em>Amblyopone, Proceratium</em>, and <em>Smithistruma</em> (Hymenoptera: Formicidae).&nbsp; <em>J. Kans. Ento. Soc.</em>&nbsp; 61(1), 102-124.&nbsp;</p>
    <p style="margin-left:.25in;">
        Ward, P. S. (1994). <em>Adetomyrma</em>, an enigmatic new ant genus from Madagascar (Hymenoptera: Formicidae), and its implications for ant phylogeny. <em>Systematic Entomology,</em> 19, 159-175.</p>
    <p style="margin-left:.25in;">
        Ward, P. S. &ldquo;Ants of the subfamily Pseudomyrmecinae: Use of morphometric measurements&rdquo; on&nbsp;<a href="http://wardlab.wordpress.com/research/pseudomyrmecinae/pseudomyrmex/measurements/">http://wardlab.wordpress.com/research/pseudomyrmecinae/pseudomyrmex/measurements/</a></p>
    <p style="margin-left:.25in;">
        Ward, P.S. (2001).&nbsp; Taxonomy, phylogeny and biogeography of the ant genus <em>Tetraponera </em>(Hymenoptera: Formicidae) in the Oriental and Australian regions.&nbsp; <em>Invertebrate Taxonomy</em>, 15, 589-665.&nbsp;</p>
    <p style="margin-left:.25in;">
        Ward, P.S. (2005).&nbsp; A synoptic review of the ants of California (Hymenoptera: Formicidae). <em>Zootaxa, </em>&nbsp;936, 1-68.</p>
    <p style="margin-left:.25in;">
        Wild, A.L.&nbsp; (2005).&nbsp; Taxonomic revision of the Pachycondyla apicalis species complex (Hymenoptera: Formicidae).&nbsp; <em>Zootaxa</em>, 834, 1-25.&nbsp;</p>
    <p style="margin-left:.25in;">
        Yoshimura, M. and K. Onoyama.&nbsp; (2007).&nbsp; A new sibling species of the genus <em>Strumigenys</em>, with a redefinition of <em>S. lewisi </em>Cameron.&nbsp; <em>Advances in Ant Systematics: Homage to EO Wilson.</em>&nbsp; Eds. Snelling, R., Fisher, B., Ward, P.</p>
    <p style="margin-left:.25in;">
        Zheng-Hui, X. and Xing-Guo, Z.&nbsp; (2004).&nbsp; Systematic study on the ant genus <em>Pyramica</em> Roger (Hymenoptera: Formicidae) of China.&nbsp; <em>Ata. Zootaxonomica Sinica</em>.&nbsp; 29(3), 440-450.&nbsp;</p>
    <p style="margin-left:.25in;">
        &nbsp;</p>
	   <%        
	   AncFile ancFile = (AncFile) session.getAttribute("ancFile");	           
	   Login accessLogin = LoginMgr.getAccessLogin(request);        
	   if (accessLogin != null) {          
           String requestURL = request.getRequestURL().toString();          
           String accessIdStr = "/" + (new Integer(accessLogin.getId())).toString() + "/";	      
           if ( (accessLogin.isAdmin())	|| (accessLogin.getProjects().contains("madants")) || (requestURL.contains(accessIdStr)) ) {	   %>	   
               <form method="POST" action="<%= domainApp %>/ancPageEdit.do?id=49" />	   		
                <input type="submit" value="Edit Page">	   	
               </form>	   	 
               <% if (!(session.getAttribute("ancFile") == null)) { %>	   	
               <form method="POST" action="<%= domainApp %>/ancPageSave.do"> 	   		
                <input type="submit" value="Save Page">	   	
               </form>         
               <% } %>       
        <% } %>	   		
    <% } %>	   		   
    </div>	   	
</div>	
</tiles:put>
</tiles:insert>