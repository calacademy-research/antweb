/*
======================================================================
Pausing RSS scroller JavaScript engine- © Dynamic Drive (http://www.dynamicdrive.com)
Docs: http://www.dynamicdrive.com/dynamicindex17/rsspausescroller/
Last modified: March 16th, 2006.
======================================================================
*/

//URL to "scrollerbridge.php" on your server:
// THIS DOES NOT SEEM TO EXIST ON OUR SERVER.  PERHAPS THIS SCRIPT IS NOT USED AT ALL.
var bridgepath="https://www.antweb.org:8080/antweb/lastrss/scrollerbridge.php"

//Advanced users: Edit below function to format the RSS feed output as desired
//formatrssmessage(divid, message_index_within_array, linktarget, logicswitch)

function formatrssmessage(divid, msgnumber, linktarget, logicswitch){
var rsscontent=rsscontentdata[divid][msgnumber]
var this_title = unescape(rsscontent.title);
if (this_title.indexOf("CDATA") != -1) {
  var last_open = this_title.lastIndexOf("[");
  var first_close = this_title.indexOf("]");
  this_title = this_title.substring(last_open+1, first_close);
}
var linktitle='<span class="rsstitle"><a href="'+unescape(rsscontent.link)+'" target="'+linktarget+'">'+this_title+'</a></span>'
var description='<div class="rssdescription">'+unescape(rsscontent.description)+'</div>'
var feeddate='<span class="rssdate">'+unescape(rsscontent.date)+'</span>'
if (logicswitch.indexOf("description")!=-1 && logicswitch.indexOf("date")!=-1) //Logic switch- Show description and date
return linktitle+"<br />"+feeddate+description
else if (logicswitch.indexOf("description")!=-1) //Logic switch- Show just description
return linktitle+"<br />"+description
else if (logicswitch.indexOf("date")!=-1) //Logic switch- Show just date
return linktitle+"<br />"+feeddate
else
return linktitle //Default- Just return hyperlinked RSS title
}

//////NO NEED TO EDIT BEHIND HERE///////////////////////////

var rsscontentdata=new Array() //global array to hold RSS feeds contents

//rsspausescroller(RSS_id, divId, divClass, delay, linktarget, optionalswitch)

function rsspausescroller(RSS_id, divId, divClass, delay, linktarget, logicswitch){
this.tickerid=divId //ID of ticker div to display information
this.delay=delay //Delay between msg change, in miliseconds.
this.linktarget=(typeof linktarget!="undefined")? linktarget : ""
this.logicswitch=(typeof logicswitch!="undefined")? logicswitch : ""
this.mouseoverBol=0 //Boolean to indicate whether mouse is currently over scroller (and pause it if it is)
this.hiddendivpointer=1 //index of message array for hidden div
this.js_is_loaded=0
this.number_of_tries=0
document.write('<div id="'+divId+'" class="'+divClass+'" style="position: relative; overflow: hidden"><div class="innerDiv" style="position: absolute; width: 100%" id="'+divId+'1"><span style="position: absolute">Initializing RSS scroller...</span></div><div class="innerDiv" style="position: absolute; width: 100%; visibility: hidden" id="'+divId+'2"></div></div>')
if (document.getElementById){ //perform basic DOM browser support
var parameters="id="+encodeURIComponent(RSS_id)+"&divid="+divId+"&bustcache="+new Date().getTime()
rsspausescroller.getRSScontentJS(bridgepath+"?"+parameters)
this.do_onjsload()
}
}

// -------------------------------------------------------------------
// do_onjsload()- Checks if external JS containing RSS feed is loaded yet
// -If not, continue to check until yes, or abort after certain tries.
// -------------------------------------------------------------------

rsspausescroller.prototype.do_onjsload=function(){
var scrollerinstance=this
if (typeof rsscontentdata[this.tickerid]=="undefined" && this.number_of_tries<40){ //if JS array holding RSS content not yet loaded
this.number_of_tries++
setTimeout(function(){scrollerinstance.do_onjsload()}, 200) //recheck
}
else if (typeof rsscontentdata[this.tickerid]!="undefined"){ //if JS array has loaded
this.tickerdiv=document.getElementById(this.tickerid)
this.visiblediv=document.getElementById(this.tickerid+"1")
this.hiddendiv=document.getElementById(this.tickerid+"2")
this.visibledivtop=parseInt(rsspausescroller.getCSSpadding(this.tickerdiv))
//set width of inner DIV to outer DIV width minus padding (padding assumed to be top padding x 2)
this.visiblediv.style.width=this.hiddendiv.style.width=this.tickerdiv.offsetWidth-(this.visibledivtop*2)+"px"
this.visiblediv.innerHTML=formatrssmessage(this.tickerid, 0, this.linktarget, this.logicswitch)
this.hiddendiv.innerHTML=formatrssmessage(this.tickerid, 1, this.linktarget, this.logicswitch)
this.do_ondivsinitialized()
}
else
document.getElementById(this.tickerid).innerHTML=rsscontentdata+"<br />I give up trying to fetch RSS feed."
}

// -------------------------------------------------------------------
// do_ondivsinitialized()- Checks if two divs of scroller is each populated with RSS message yet
// -If not, continue to check until yes, or abort after certain tries.
// -------------------------------------------------------------------

rsspausescroller.prototype.do_ondivsinitialized=function(){
var scrollerinstance=this
if (parseInt(this.visiblediv.offsetHeight)==0 || parseInt(this.hiddendiv.offsetHeight)==0)
setTimeout(function(){scrollerinstance.doondivsinitialized()}, 100)
else
this.initialize()
}

// -------------------------------------------------------------------
// initialize()- Initialize scroller method.
// -Get div objects, set initial positions, start up down animation
// -------------------------------------------------------------------

rsspausescroller.prototype.initialize=function(){
var scrollerinstance=this
this.getinline(this.visiblediv, this.hiddendiv)
this.hiddendiv.style.visibility="visible"
//set width of inner DIVs to outer DIV's width minus padding (padding assumed to be top padding x 2)
this.visiblediv.style.width=this.hiddendiv.style.width=this.tickerdiv.offsetWidth-(this.visibledivtop*2)+"px"
this.tickerdiv.onmouseover=function(){scrollerinstance.mouseoverBol=1}
this.tickerdiv.onmouseout=function(){scrollerinstance.mouseoverBol=0}
if (window.attachEvent) //Clean up loose references in IE
window.attachEvent("onunload", function(){scrollerinstance.tickerdiv.onmouseover=scrollerinstance.tickerdiv.onmouseout=null})
setTimeout(function(){scrollerinstance.animateup()}, this.delay)
}

// -------------------------------------------------------------------
// animateup()- Move the two inner divs of the scroller up and in sync
// -------------------------------------------------------------------

/*
rsspausescroller.prototype.animateup=function(){
var scrollerinstance=this
if (parseInt(this.hiddendiv.style.top)>(this.visibledivtop+5)){
this.visiblediv.style.top=parseInt(this.visiblediv.style.top)-5+"px"
this.hiddendiv.style.top=parseInt(this.hiddendiv.style.top)-5+"px"
setTimeout(function(){scrollerinstance.animateup()}, 50)
}
else{
this.getinline(this.hiddendiv, this.visiblediv)
this.swapdivs()
setTimeout(function(){scrollerinstance.rotatemessage()}, this.delay)
}
}
*/
rsspausescroller.prototype.animateup=function(){
var scrollerinstance=this
  this.visiblediv.style.top=this.visibledivtop+"px"
  this.getinline(this.hiddendiv, this.visiblediv)
  this.swapdivs()
  setTimeout(function(){scrollerinstance.rotatemessage()}, this.delay)
}

// -------------------------------------------------------------------
// swapdivs()- Swap between which is the visible and which is the hidden div
// -------------------------------------------------------------------

rsspausescroller.prototype.swapdivs=function(){
var tempcontainer=this.visiblediv
this.visiblediv=this.hiddendiv
this.hiddendiv=tempcontainer
}

rsspausescroller.prototype.getinline=function(div1, div2){
div1.style.top=this.visibledivtop+"px"
div2.style.top=Math.max(div1.parentNode.offsetHeight, div1.offsetHeight)+"px"
}

// -------------------------------------------------------------------
// rotatemessage()- Populate the hidden div with the next message before it's visible
// -------------------------------------------------------------------
/*
rsspausescroller.prototype.rotatemessage=function(){
var scrollerinstance=this
if (this.mouseoverBol==1) //if mouse is currently over scoller, do nothing (pause it)
setTimeout(function(){scrollerinstance.rotatemessage()}, 100)
else{
var i=this.hiddendivpointer
var ceiling=rsscontentdata[this.tickerid].length
this.hiddendivpointer=(i+1>ceiling-1)? 0 : i+1
this.hiddendiv.innerHTML=formatrssmessage(this.tickerid, this.hiddendivpointer, this.linktarget, this.logicswitch)
this.animateup()
}
}
*/
rsspausescroller.prototype.rotatemessage=function(){
var scrollerinstance=this
if (this.mouseoverBol==1) //if mouse is currently over scoller, do nothing (pause it)
setTimeout(function(){scrollerinstance.rotatemessage()}, 100)
else{
var i=this.hiddendivpointer
var ceiling=rsscontentdata[this.tickerid].length
this.hiddendivpointer=(i+1>ceiling-1)? 0 : i+1
this.hiddendiv.innerHTML=formatrssmessage(this.tickerid, this.hiddendivpointer, this.linktarget, this.logicswitch)
this.animateup()
}
}
// -------------------------------------------------------------------
// getRSScontentJS()- Fetch RSS feed as external JavaScript
// -------------------------------------------------------------------

rsspausescroller.getRSScontentJS=function(scripturl){
var scriptref=document.createElement('script')
scriptref.setAttribute("type","text/javascript")
scriptref.setAttribute("src", scripturl)
document.getElementsByTagName("head").item(0).appendChild(scriptref)
}


rsspausescroller.getCSSpadding=function(tickerobj){ //get CSS padding value, if any
if (tickerobj.currentStyle)
return tickerobj.currentStyle["paddingTop"]
else if (window.getComputedStyle) //if DOM2
return window.getComputedStyle(tickerobj, "").getPropertyValue("padding-top")
else
return 0
}

