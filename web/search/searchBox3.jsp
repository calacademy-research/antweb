<%-- "Basic Search" box of the advanced search form (static include of search/advancedSearch-body.jsp): loads
     Google's Programmable Search script (cse.google.com, engine id cx in the script), which draws the box and its
     results in the page. --%><!-- Used on the advanced search page.  Newest method.  Always fills the whole page so would
     not be good in the menu without UI niceties. -->
<div class="gcse-XXX">
<script>
  (function() {
    var cx = '017956581337434518266:y_g6imns73s';
    var gcse = document.createElement('script');
    gcse.type = 'text/javascript';
    gcse.async = true;
    gcse.src = (document.location.protocol == 'https:' ? 'https:' : 'http:') +
        '//cse.google.com/cse.js?cx=' + cx;
    var s = document.getElementsByTagName('script')[0];
    s.parentNode.insertBefore(gcse, s);
  })();
</script>
<gcse:search></gcse:search>            
</div>