/*
* CleanPaste for YUI Rich Text Editor
* http://richtextcleanpaste.codeplex.com
*
* Copyright (c) 2009 Anthony Super
* http://antscode.blogspot.com
*
* v 1.1
*/

function CleanPaste(editor) {
    this.Editor = editor;
    this.Editor.on('editorContentLoaded', this.init, this, true);
    this.OnBeforePaste = null;
    this.OnAfterPaste = null;
}

CleanPaste.prototype.init = function() {
    var doc = this.Editor._getDoc();

    if (document.all) {
        // IE events
        YAHOO.util.Event.on(doc.body, 'beforepaste', this.handleBeforePaste, this, true);
        YAHOO.util.Event.on(doc.body, 'paste', this.handlePaste, this, true);
        YAHOO.util.Event.on(doc, 'dragstart', this.handleDragDrop, this, true);
        YAHOO.util.Event.on(doc, 'dragend', this.handleDragDrop, this, true);
    }
    else {
        // Mozilla events
        this.Editor.on('editorKeyDown', this.handleKeyDown, this, true);
        YAHOO.util.Event.on(doc, 'contextmenu', this.handleContextMenu, this, true);
    }
}

CleanPaste.prototype.handleKeyDown = function(e) {
    var vkey = 86
    var vInsertKey = 45
    var isMac = (navigator ? navigator.appVersion.indexOf('Mac') != -1 : false);
    var _this = this;

    // Check to see if CTRL + V is pressed
    if ((!isMac && e.ev.keyCode == vkey && e.ev.ctrlKey) ||
        (!isMac && e.ev.keyCode == vInsertKey && e.ev.shiftKey) ||
        (isMac && e.ev.keyCode == vkey && e.ev.metaKey)) {
        this.InsertContainer()
        setTimeout(function() { _this.CleanPaste() }, 10);
    }
}

CleanPaste.prototype.handleBeforePaste = function(e) {
    var container = this.Editor._getDoc().getElementById('Cleaner');

    // Remove any existing container
    if (container)
        container.parentNode.removeChild(container);

    // Insert a container for placing the cleaned text
    this.InsertContainer();

    // Cancel the event bubble
    YAHOO.util.Event.stopEvent(e);
}

CleanPaste.prototype.handlePaste = function() {
    var _this = this;
    setTimeout(function() { _this.CleanPaste() }, 10);
}

CleanPaste.prototype.handleDragDrop = function(e) {
    YAHOO.util.Event.stopEvent(e);
}

CleanPaste.prototype.handleContextMenu = function(e) {
    YAHOO.util.Event.stopEvent(e);
}

CleanPaste.prototype.InsertContainer = function() {
    this.Editor.execCommand('inserthtml', "<div id='Cleaner'>_</div>");
    var container = this.Editor._getDoc().getElementById('Cleaner');
    this.Editor._selectNode(container);
}

CleanPaste.prototype.CleanPaste = function() {
    var container = this.Editor._getDoc().getElementById("Cleaner");
    var sourceText = container.innerHTML;
    var cleanText = this.CleanHTML(sourceText);
    var newText = document.createElement('span');

    var pasteParams = { 'sourceText': sourceText, 'cleanText': cleanText };

    if (this.OnBeforePaste != null) {
        // Fire OnBeforePaste event
        this.OnBeforePaste(pasteParams);
    }

    // Update cleaned text from event handler
    cleanText = pasteParams.cleanText;

    newText.innerHTML = cleanText;

    if (document.all) {
        // Remove the container
        container.parentNode.removeChild(container);

        // Insert clean text
        this.Editor.execCommand('inserthtml', cleanText);
    }
    else {
        container.parentNode.replaceChild(newText, container);
    }

    if (this.OnAfterPaste != null) {
        // Fire OnAfterPaste event
        this.OnAfterPaste(pasteParams);
    }
}

CleanPaste.prototype.CleanHTML = function(html) {
    // Run the standard YUI cleanHTML method
    html = this.Editor.cleanHTML(html);

    // Remove additional MS Word content
    html = html.replace(/<(\/)*(\\?xml:|meta|link|span|font|del|ins|st1:|[ovwxp]:)((.|\s)*?)>/gi, ''); // Unwanted tags
    html = html.replace(/(class|style|type|start)=("(.*?)"|(\w*))/gi, ''); // Unwanted sttributes
    html = html.replace(/<style(.*?)style>/gi, '');   // Style tags
    html = html.replace(/<script(.*?)script>/gi, ''); // Script tags
    html = html.replace(/<!--(.*?)-->/gi, '');        // HTML comments
    html = html.replace(/(&#8220;)|(&#8221;)|[“”\u8220\u8221]/gi, "&quot;");
    html = html.replace(/(&#8216;)|(&#8217;)|[‘’\u8216\u8217]/gi, "&#39;");
//    html = html.replace(/(&#8217;)|[’\u8217]/gi, "&#39;");
    html = html.replace(/(&#8230;)|[…\u8230]/gi, "...");
    
    return html;
}
