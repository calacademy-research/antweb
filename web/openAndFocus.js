function openAndFocus(theUrl, theName) {
  var currentHeight = getInnerHeight(window);
  var currentWidth = getInnerWidth(window);
  var theDisplayString;
  var sizeDelta = 50;
  var thePage;

  if ((currentHeight > 0) && (currentWidth > 0)) {
    theDisplayString = "width=" + (currentWidth - sizeDelta) + 
    ",height=" + (currentHeight - sizeDelta) + ",resizable=yes,scrollbars=yes,status=yes,toolbar=yes,location=yes";
    thePage = window.open(theUrl,theName,theDisplayString);
  } else {
    thePage = window.open(theUrl,theName);
  }
  thePage.focus();
}

function getInnerWidth(win) {
  var winWidth;
  if (win.innerWidth) {
    winWidth = win.innerWidth;
  }
  else if (win.document.documentElement && win.document.documentElement.clientWidth) {
    winWidth = win.document.documentElement.clientWidth;
  }
  else if (document.body) {
    winWidth = win.document.body.clientWidth;
  }
  return winWidth;
}

function getInnerHeight(win) {
  var winHeight;
  if (win.innerHeight) {
    winHeight = win.innerHeight;
  }
  else if (win.document.documentElement && win.document.documentElement.clientHeight) {
    winHeight = win.document.documentElement.clientHeight;
  }
  else if (win.document.body) {
    winHeight = win.document.body.clientHeight;
  }
  return winHeight;
}
