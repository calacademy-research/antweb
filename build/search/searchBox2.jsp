
<div class="search">
Search!

<!-- Put the following javascript before the closing  tag. -->
<script>
(function() {
  var cx = '123:456'; // Insert your own Custom Search engine ID here
  var gcse = document.createElement('script'); gcse.type = 'text/javascript'; gcse.async = true;
  gcse.src = (document.location.protocol == 'https' ? 'https:' : 'http:') +
      '//www.google.com/cse/cse.js?cx=' + cx;
  var s = document.getElementsByTagName('script')[0]; s.parentNode.insertBefore(gcse, s);
})();
</script>

<!-- Place this tag where you want both of the search box and the search results to render -->
<gcse:search></gcse:search>
<gcse:searchbox-only resultsUrl="http://www.example.com" newWindow="true" queryParameterName="search">
<gcse:searchbox enableHistory="true" autoCompleteMaxCompletions="5" autoCompleteMatchType='any'>
</gcse:searchbox>
<gcse:searchresults refinementStyle="link">

</div>