

<script type="text/javascript">
 
function selectAll(thisForm) {
  var count = thisForm.adm2s.length;
  var checkedVal = thisForm.selectall.checked;
  for (var loop = 0; loop < count; loop++) {
    thisForm.adm2s[loop].checked = checkedVal;
  }
}
</script>


<!-- div class="right" -->

<p>
<div class=green_module><span class=module_header>SEARCH BAY AREA ANTS:</span></div>
<div class=module_contents>

<html:form method="POST" action="bayAreaSearch"> <!--  -->
<input type="hidden" name="searchMethod" value="bayAreaSearch"> <!-- was:  -->

<html:checkbox property="adm2s" value="alameda"/> Alameda<br>
<html:checkbox property="adm2s" value="contra costa"/> Contra Costa<br>
<html:checkbox property="adm2s" value="marin"/> Marin<br>
<html:checkbox property="adm2s" value="napa"/> Napa<br>
<html:checkbox property="adm2s" value="sacramento"/> Sacramento<br>
<html:checkbox property="adm2s" value="san francisco"/> San Francisco<br>
<html:checkbox property="adm2s" value="san joaquin"/> San Joaquin<br>
<html:checkbox property="adm2s" value="san mateo"/> San Mateo<br>
<html:checkbox property="adm2s" value="santa clara"/> Santa Clara<br>
<html:checkbox property="adm2s" value="santa cruz"/> Santa Cruz<br>
<html:checkbox property="adm2s" value="solano"/> Solano<br>
<html:checkbox property="adm2s" value="sonoma"/> Sonoma<br>
<html:checkbox property="adm2s" value="yolo"/> Yolo<br>
<input type="checkbox" name="selectall" onClick="selectAll(document.bayAreaSearchForm);"> Select All <br>
<p align="center"><input type="submit" class=submit value="Search &#187"></p>
</html:form>
</div>

<!-- /div -->