$(function () {

$(".specimen_layout:odd").addClass("specimen_layout_alt");

$('#sortBy_select').on('change', function () { 
    var action = $("#sortby_action").val();
    var thesort = $(this).val(); 
    if ($("#ns_sortby_extras").length>0) {
        var theextras = $("#ns_sortby_extras").val(); 
    } else {
        var theextras = $("#sortby_extras").val(); 
    }
    var thetool = $("#show_tool").val(); 
    var goto = action + "?" + theextras + "&sortBy=" + thesort + "&t=" + thetool; 
    if (thesort != '') { 
        window.location = goto; 
    }
    return false;
});

var url = $.url();
var all_params = $.url(url).attr('query');
var trim_params = all_params.replace(/&sortBy.*/, '');
$("#sortby_extras").val(trim_params);

var sort_by = $.url(url).param('sortBy');
if (sort_by) {
    $('html, body').animate({scrollTop: $("#specimen_list_header").offset().top}, 1500);
}

var open_tool = $.url(url).param('t');
if (open_tool) {
    showTools(open_tool);
    highlightTool();
    fixStripes();
}

if ($("#multi_compare").length>0) { 
    $("input[name='selectall']").prop('checked',true);
    $(".data_checkbox :checkbox").prop('checked',true);
}

$("input[name='selectall']").click(function() {
  var comparetool = $("#compare_tools").css("display");;
  if ($(".sdcb").length>0) { 
      $(".sdcb :checkbox").prop('checked', $(this).prop('checked'));
  } else { 
      $(".data_checkbox :checkbox").prop('checked', $(this).prop('checked'));
  } 
  if (comparetool == "none") {
  } else {
      if ($(".sdcb").length>0) { 
          $(".sdcb.np :checkbox").prop("checked", false);
          $(".sdcb.np :checkbox").prop("disabled", true);
      }
  }
});
$(".sdcb :checkbox").click(function() {
  $("input[name='selectall']").prop('checked', false);
});

$(".data_checkbox :checkbox").click(function() {
  $("input[name='selectall']").prop('checked', false);
});

var domain = $("#domain").html();
$("#compare_form").click(function() {
    $("#theform").attr("action", domain + "/compareResults.do");
    $("#theform").submit();
});

$("#list_compare_form").click(function() {
    $("#theform").attr("action", domain + "/getComparison.do");
    $("#theform").submit();
});

$("#taxon_compare_form").click(function() {
    $("#theform").attr("action", domain + "/compareResults.do");
    $("#theform").submit();
});

if ($("#project_is").length>0) { 
    var this_project = $("#project_is").val();
    $("#the_project").val(this_project);
}

$("#map_form").click(function() {
    $("#theform").attr("action", domain + "/mapResults.do");
    $("#theform").submit();
});

$("#fieldguide_form").click(function() {
    $("#theform").attr("action", domain + "/fieldGuideResults.do");
    $("#theform").submit();
});
if ($("#mapped_count").length>0) { 
    var mapped = $("#mapped_count").val();
    $("#mapped").html(mapped);
}
});
function fixStripes() {
    $(".specimen_layout").removeClass("specimen_layout_alt");
    $(".specimen_layout:visible:odd").addClass("specimen_layout_alt");
}
function toggleCheckboxes() {
    if ($(".sdcb").length>0) { 
        var CBs = $(".sdcb").css("display");
    } else {
        var CBs = $(".data_checkbox").css("display");
        var the_data = $(".data_left").css("display");
    }
    if (CBs == "none") {
        if ($(".sdcb").length>0) { 
            $(".sdcb").show();
        } else {
            $(".data_left").hide();
            $(".lower_data.medium").show();
            $(".lower_gradient.medium ").show();
            $(".data_checkbox").show();
        }
//    } else {
//        $(".sdcb").hide();
    }

}
function highlightTool() {
    $('.tool_label').click(function() {
        $(this).addClass("tool_label_selected"); 
    });
}
function showTools(whichone) {
    $('.tools').each(function(index) {
        if ($(this).attr("id") == whichone) {
            $(this).show();
            $("#show_tool").val(whichone);
        } else {
            $(this).hide();
        }
        var totalcount = $("#totalcount").html();
        var imagecount = $("#imagecount").html();
        if (whichone == "compare_tools") {
            $(".display_count").html(imagecount);
            $(".no_photos").hide();
        } else {
            $(".sdcb.np :checkbox").removeProp("disabled");
            $(".display_count").html(totalcount);
            $(".no_photos").show();
        }
        $(".tool_label").removeClass("tool_label_selected"); 
        highlightTool();
        fixStripes();
        toggleCheckboxes();
        $("input[name='selectall']").prop('checked', false);
        $(".sdcb :checkbox").prop('checked', false );
    });
}
function hideTools(whichone) {
    $('.tools').each(function(index) {
        if ($(this).attr("id") == whichone) {
            $(this).hide();
            $("#show_tool").val('');
            if ($(".sdcb").length>0) { 
                $(".sdcb").hide();
            } else {
                $(".data_checkbox").hide();
                $(".data_left").show();
            }
            var totalcount = $("#totalcount").html();
            if (whichone == "compare_tools") {
                $(".no_photos").show();
                fixStripes();
                $(".display_count").html(totalcount);
            }
            $("#show_tool").val('');
            $(".tool_label").removeClass("tool_label_selected"); 
            $("input[name='selectall']").prop('checked', false);
            $(".sdcb :checkbox").prop('checked', false );
        }
    });
}

