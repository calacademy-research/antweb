<%-- A d3pie pie chart (div pieChart), statically included twice by overview-body.jsp (Species and Specimens per
     Subfamily) and by overviews-body.jsp. Uses the including page's title, subtitle, footer and jsonData (the
     slices); the including page loads d3 and d3pie.min.js. Docs: docs/numbers.md rows 34 and 35. --%>
<!-- Pie chart genererated by: http://d3pie.org/#generator -->
<div id="pieChart"></div>


<script>
var pie = new d3pie("pieChart", {
	"header": {
		"title": {
			"text": "<%= title %>",
			"fontSize": 22,
			"font": "verdana"
		},
		"subtitle": {
			"text": "<%= subtitle %>",
			"color": "#999999",
			"fontSize": 10,
			"font": "verdana"
		},
		"titleSubtitlePadding": 12
	},
	"footer": {
		"text": "<%= footer %>",
		"color": "#999999",
		"fontSize": 11,
		"font": "open sans",
		"location": "bottom-center"
	},
	"size": {
		"canvasHeight": 400,
		"canvasWidth": 590,
		"pieOuterRadius": "85%"
	},
	"data": {
		"smallSegmentGrouping": {
			"enabled": true
		},
		"content": [
<%= jsonData %>	
		]
	},
	"labels": {
		"outer": {
			"pieDistance": 32
		},
		"inner": {
			"hideWhenLessThanPercentage": 4
		},
		"mainLabel": {
			"font": "verdana"
		},
		"percentage": {
			"color": "#e1e1e1",
			"font": "verdana",
			"decimalPlaces": 0
		},
		"value": {
			"color": "#e1e1e1",
			"font": "verdana"
		},
		"lines": {
			"enabled": true,
			"color": "#cccccc"
		},
		"truncation": {
			"enabled": true
		}
	},
	"effects": {
		"pullOutSegmentOnClick": {
			"effect": "linear",
			"speed": 400,
			"size": 8
		}
	},
	"misc": {
		"colors": {
			"background": "#ffffff"
		}
	}
});
</script>
