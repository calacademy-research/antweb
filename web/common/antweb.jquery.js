$(function() {


// MENUS
// To be reworked at a later design phase

var activeMenuL1;

$('#navigation').mouseleave(function() {
    closeL1Menu();
});

$('.menu_l1').click(function(e) {

    // toggle l1 menu state
    if(activeMenuL1 && $(this).data('submenu')==activeMenuL1) {
        // close all menus
        closeL1Menu();
    } else {
        // show menu
        openMenuL1($(this).data('submenu'));

        // manage l1 menu highlight
        $('.menu_l1').removeClass('highlight');
        $(this).addClass('highlight');
    }
});

function openMenuL1(menu) {
    // hide all submenus
    $('.subnav').hide();

    // set the active l1 menu
    activeMenuL1 = menu;

    // show the submenu
    $('#'+menu).toggle();

    // show the subnavigation element
    if($('#subnavigation').is(":hidden")) {
        $('#subnavigation').slideToggle(150);
    }
}

function closeL1Menu() {
    // reset all menus
    activeMenuL1 = null;
    $('#subnavigation').slideUp(150);
    $('.menu_l1').removeClass('highlight');
    $("#accordion ul").slideUp();
}



// From *Josh Dec 2015
//$(".show_items a").append("<a class='more'> (show more)</a>");
//$(".country_list .more, .region_items .more").remove();

//$(".show_items .more").click(function() {
//    var target = $(this).parent();
//    // close parent sibling menus
//    var parent = target.parents("ul").not("#accordion");
//    $("#accordion ul").not(parent).slideUp();

//    // show children
//    if (target.next().is(':hidden')) {
//        target.next().slideDown();
//    } else {
//        target.next().slideUp();
//    }    
//}); 




// ACCORDION
// used for sub menus

// From *Josh Dec 2015.  Commented out the following...
$("#accordion a").click(function() {
    // close parent sibling menus
    var parent = $(this).parents("ul").not("#accordion");
    $("#accordion ul").not(parent).slideUp()//;

    // show children
    if ($(this).next().is(':hidden')) {
        $(this).next().slideDown();
    } else {
        $(this).next().slideUp();
    }    
}); 








  var msie6 = $.browser == 'msie' && $.browser.version < 7;
  if (!msie6) {
   if ($('#page_context').length>0) {
    var top = $('#page_context').offset().top - parseFloat($('#page_context').css('margin-top').replace(/auto/, 0));
    $(window).scroll(function (event) {
      var y = $(this).scrollTop();
      if (y >= top) {
        // $('#page_context').addClass('fixed');
        $("#footer").addClass('bump');
      } else {
        // $('#page_context').removeClass('fixed');
        $("#footer").removeClass('bump');
      }
    });
   }
  }

    if ($('.container.home').length>0) {
        var bgImgArray = [
            {img: "https://www.antweb.org/image/casent0264380_p_1_high.jpg", specimen_url: "https://www.antweb.org/bigPicture.do?imageId=121355", specimen: "casent0264380", species_url: "https://www.antweb.org/images.do?rank=species&name=elegantissima&genus=lepisiota&project=allantwebants", species: "Lepisiota elegantissima"},
            {img: "https://www.antweb.org/image/casent0191696_p_1_high.jpg", specimen_url: "https://www.antweb.org/bigPicture.do?imageId=60121", specimen: "casent0191696", species_url: "https://www.antweb.org/images.do?rank=species&name=darwinii&genus=camponotus&project=allantwebants", species: "Camponotus darwinii"},
            {img: "https://www.antweb.org/image/casent0006837_h_1_high.jpg", specimen_url: "https://www.antweb.org/bigPicture.do?imageId=43794", specimen: "casent0006837", species_url: "https://www.antweb.org/images.do?rank=species&name=iriodum&genus=myrmoteras&project=allantwebants", species: "Myrmoteras iriodum"},
            {img: "https://www.antweb.org/image/casent0178570_d_1_high.jpg", specimen_url: "https://www.antweb.org/bigPicture.do?imageId=49569", specimen: "casent0178570", species_url: "https://www.antweb.org/images.do?rank=species&name=ferox&genus=acanthomyrmex&project=allantwebants", species: "Acanthomyrmex ferox"},
            {img: "https://www.antweb.org/image/casent0101062_p_1_high.jpg", specimen_url: "https://www.antweb.org/bigPicture.do?imageId=41121", specimen: "casent0101062", species_url: "https://www.antweb.org/images.do?rank=species&name=concavus&genus=acanthomyrmex&project=allantwebants", species: "Acanthomyrmex concavus"},
            {img: "https://www.antweb.org/image/casent0172636_h_1_high.jpg", specimen_url: "https://www.antweb.org/bigPicture.do?imageId=32148", specimen: "casent0172636", species_url: "https://www.antweb.org/images.do?rank=species&name=nigricans%20sjoestedti&genus=dorylus&project=allantwebants", species: "Dorylus nigricans sjoestedti"},
            {img: "https://www.antweb.org/image/casent0006046_h_1_high.jpg", specimen_url: "https://www.antweb.org/bigPicture.do?imageId=6384", specimen: "casent0006046", species_url: "https://www.antweb.org/images.do?rank=species&name=texana&genus=atta&project=allantwebants", species: "Atta texana"}
        ];

        var currentdate = new Date();
        var bg_number = currentdate.getDay();

        $('.container.home').css('background', 'url(' + bgImgArray[bg_number].img + ') no-repeat scroll 0 0 #000');
        $('.container.home').css('background-size', '1000px auto');
        $("#specimen_link").prop('href', bgImgArray[bg_number].specimen_url);
        $('#the_specimen').html(bgImgArray[bg_number].specimen);
        $("#species_link").prop('href', bgImgArray[bg_number].species_url);
        $('#the_species').html(bgImgArray[bg_number].species);
    }

    var m_names = new Array("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December");
    var d = new Date();
    var curr_date = d.getDate();
    var curr_month = d.getMonth();
    var curr_year = d.getFullYear();
    var today_is = curr_date + " " + m_names[curr_month] + " " + curr_year;
    $(".today").html(today_is);
    $(".year").html(curr_year);

    $(".numbers").each(function() {
        $(this).parseNumber({format:"#,###", locale:"us"});
        $(this).formatNumber({format:"#,###", locale:"us"});
    });

//    if ($('#cite').length>0) {
        var for_print = $("#for_print").val();
        var for_web = $("#for_web").val();
        $("#cite_print").html(for_print);
        $("#cite_web").html(for_web);
        if ($('#extra_copyright').length>0) {
            var extra_copyright = $("#extra_copyright").val();
            $("#cite_copyright").html(extra_copyright);
        }
//    }

    if (!$('#next_in_taxon').text().length>0) {
        $('.lower_gradient').css('height', '30px');
    }
    var totalcount = $("#imaged_taxa_count").val();
    $("#imaged_taxa").html(totalcount);

    $('.curator_login').click(function(event) {
        if ($('#login_form').is(':visible')) { 
            $('#login_form').hide();
            $('#login_username').blur();
        } else {
            $('#login_form').show();
            $('#login_username').focus();
        }
    });

    $('#cite').click(function(event) {
        if ($('#citation_overlay').is(':visible')) { 
            $('#citation_overlay').hide();
        } else {
            $('#citation_overlay').show();
        }
    });

    $('#close_citation_overlay').click(function(event) {
        $('#citation_overlay').hide();
    });

    $('.ue_link').click(function(){
        // if ($('.ue_content').is(':visible')) { 
        //    $('.ue_content').fadeOut(400);
        // }
        $(this).next('.ue_content').fadeIn(400);
    });

    $('#download_data').click(function(event) {
        if ($('#download_data_overlay').is(':visible')) { 
            $('#download_data_overlay').hide();
        } else {
            $('#download_data_overlay').show();
        }
    });

    $('#close_download_data').click(function(event) {
        $('#download_data_overlay').hide();
        $(".tool_label").removeClass("tool_label_selected");
    });

    $('.slide').each(function() {
        $(this).hover(
        function() {
            $(this).find('.hover').show();
        },
        function() {
            $(this).find('.hover').hide();
        })
    });




    $('#project_images').click(function(event) {
        if ($('#proj_list').is(':visible')) { 
            $('#proj_list').hide();
        }
        if ($('#proj_images').is(':visible')) { 
            $('#proj_images').hide();
        } else {
            $('#proj_images').show();
        }
    });

    $('#project_list').click(function(event) {
        if ($('#proj_images').is(':visible')) { 
            $('#proj_images').hide();
        }
        if ($('#proj_list').is(':visible')) { 
            $('#proj_list').hide();
        } else {
            $('#proj_list').show();
        }
    });

    $('.proj_taxon').mouseleave(function(event) {
        $('.proj_taxon').delay(1000).fadeOut();
    });

    $('#pfg_choices').mouseleave(function(event) {
        $('#pfg_choices').delay(1000).fadeOut();
    });

    $('#project_fg').click(function(event) {
        if ($('#pfg_choices').is(':visible')) { 
            $('#pfg_choices').hide();
        } else {
            $('#pfg_choices').show();
        }
    });

    $('#change_view').click(function(event) {
        if ($('#view_choices').is(':visible')) { 
            $('#view_choices').hide();
        } else {
            // var theWidth = $('#change_view').width() + 7;
            // $('#view_choices').width(theWidth);
            $('#view_choices').show();
        }
    });

    $('#view_choices').mouseleave(function(event) {
        $('#view_choices').delay(1000).fadeOut();
    });

    $('#change_thumbs').click(function(event) {
        if ($('#thumb_choices').is(':visible')) { 
            $('#thumb_choices').hide();
        } else {
            var theWidth = $('#change_thumbs').width() + 7;
            $('#thumb_choices').width(theWidth);
            $('#thumb_choices').show();
        }
    });
    $('#thumb_choices').mouseleave(function(event) {
        $('#thumb_choices').delay(1000).fadeOut();
    });

    $('#change_status').click(function(event) {
        if ($('#status_choice').is(':visible')) { 
            $('#status_choice').hide();
        } else {
            // var theWidth = $('#change_thumbs').width() + 7;
            // $('#status_choice').width(theWidth);
            $('#status_choice').show();
        }
    });
    $('#status_choice').mouseleave(function(event) {
        $('#status_choice').delay(1000).fadeOut();
    });
    

    $('#change_caste').click(function(event) {
        if ($('#caste_choices').is(':visible')) { 
            $('#caste_choices').hide();
        } else {
            var theWidth = $('#change_caste').width() + 7;
            $('#caste_choices').width(theWidth);
            $('#caste_choices').show();
        }
    });
    $('#caste_choices').mouseleave(function(event) {
        $('#caste_choices').delay(1000).fadeOut();
    });

    $('#change_subgenera').click(function(event) {
        if ($('#subgenera_choices').is(':visible')) { 
            $('#subgenera_choices').hide();
        } else {
            var theWidth = $('#change_subgenera').width() + 7;
            $('#subgenera_choices').width(theWidth);
            $('#subgenera_choices').show();
        }
    });
    $('#subgenera_choices').mouseleave(function(event) {
        $('#subgenera_choices').delay(1000).fadeOut();
    });

});

function loadTabData(url,data) {
    $('#tab_data').attr('src', url + "/" + data);
}

function loadSearchResultsData(url) {
    $("#download_search_results").attr("src", url + "/getSearchList.do");
    $('#download_data_overlay').show();
}

function changeThumbView(t,params) {
    $.cookie('thumbs', t, { path: '/', expires: 5*365 });
    window.location = params; 
}

function changeCasteView(t,params) {
    $.cookie('caste', t, { path: '/', expires: 5*365 });
    window.location = params; 
}


function showRatioOverlay(id,ss,ns) {
    if ($('#'+id+'').is(':visible')) {
        $('#'+id+'').hide();
        $('#'+ns+'').data("slideshow").stop();
    } else {
    $('.ratio_overlay').hide();
    $('#'+id+'').show();
    $('#'+ns+'').tabs("#"+ss+" > div", {
        effect: 'default',
        autopause: true,
        rotate: true
    }).slideshow( {interval: 1000} );
    $('#'+ns+'').data("slideshow").stop();
    $('#'+ns+'').data("slideshow").play();
    }
}

function runSlideshow(ns) {
    $('#'+ns+'').data("slideshow").play();
}

function hideRatioOverlay(id,ss,ns) {
    $('#'+id+'').hide();
    $('#'+ns+'').data("slideshow").stop();
}

