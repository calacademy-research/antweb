$('document').ready(function() {
  var screenHeight = $(window).height();
	var screenWidth = $(window).width();
	var navHeight = $('#main-nav').height();
	var contentHeight = screenHeight - navHeight;
  
  var delay = 300;
	$('.nav-item').each(function() {
		$(this).css('transition-delay', delay + 'ms');
		delay = delay + 100;
	});
  
	$('#mobile-nav').height(screenHeight);
	$('#content').css({
		'height': contentHeight,
		'margin-top': navHeight
	});

	$('.nav-item').each(function() {
		if ($(this).next().is('.sub-nav')) {
			$(this).addClass('arrowed');
		} else {};
	});
	$('#bun').click(function() {
		closeOut()
	});
	$('#content').click(function() {
		if ($('#container').hasClass('body-slide')) {
			closeOut()
		} else {};
	});
	$('.arrowed').click(function() {
		$(this).toggleClass('selected');
		$(this).siblings().removeClass("selected");
		$('.sub-nav').each(function() {
			$(this).slideUp("slow");
		});
		if ($(this).next('.sub-nav').is(':visible')) {
			$(this).next('.sub-nav').slideUp('slow');
		} else {
			$(this).next('.sub-nav').slideDown('slow');
		};
	});
});

function closeOut() {
	$('body').toggleClass('scroll-jam');
	$('#sidebar').toggleClass('nav-slide');
	$('#container').toggleClass('body-slide');
	$('.nav-item').toggleClass('item-slide');
	$('.nav-item').removeClass('selected');
	$('.sub-nav').each(function() {
		$(this).hide();
	});
    triangleRezise()
}

function triangleRezise(){
  if($('#container').hasClass('body-slide')) {
    $('.triangle').css('transition', '300ms ease');
    var screenHeight = $(window).height();
    var screenWidth = $(window).width();
    var navHeight = $('#main-nav').height();
    var triangleHeight = screenHeight - navHeight;
    var triangleWidth = screenWidth - 300;
    var triangleWidth = triangleWidth / 3;
    var triangleWidth = triangleWidth * 2;
    var triangleHeight = triangleHeight * 2;
    var triangleHeight = "" + triangleHeight + "px ";
    var triangleWidth = "" + triangleWidth + "px ";
    var triangle = triangleHeight + triangleWidth + "0px 0px";
    $('.triangle').css("border-width", triangle);
  }
  else{
    var screenHeight = $(window).height();
    var screenWidth = $(window).width();
    var navHeight = $('#main-nav').height();
    var triangleHeight = screenHeight - navHeight;
   var triangleWidth = screenWidth / 3;
	var triangleWidth = triangleWidth * 2;
	var triangleHeight = triangleHeight * 2;
	var triangleHeight = "" + triangleHeight + "px ";
	var triangleWidth = "" + triangleWidth + "px ";
	var triangle = triangleHeight + triangleWidth + "0px 0px";
	$('.triangle').css("border-width", triangle);
	setTimeout(function() {
		$('.triangle').css('transition', 'none');
	}, 300);
};
};
triangleRezise();
$(window).resize(function() {
  triangleRezise()
});