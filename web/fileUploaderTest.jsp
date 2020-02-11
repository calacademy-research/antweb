<html>
<head><title>Upload page</title></head></p> <p><body>

<form action="/antweb/fileUploader" method="post" enctype="multipart/form-data" name="form1" id="form1">
<center>
 Specify file: <input name="file" type="file" id="file">
 Specify file: <input name="file" type="file" id="file">
 Specify file:<input name="file" type="file" id="file">
 <input type="submit" name="Submit" value="Submit files"/>
<center>
</form>

<hr>

<form action="/antweb/fileUploader" method="post" enctype="multipart/form-data" name="form1" id="form1">
<input accept="image/jpeg,image/gif,image/png" type="file" name="upload[]" multiple/>
 <input type="submit" name="Submit" value="Submit files"/>
</form>

    <hr/>
 
    <fieldset>
        <legend>Upload File</legend>
        <form action="/antweb/fileUploader" method="post" enctype="multipart/form-data">
            <label for="filename_1">File: </label>
            <input id="filename_1" type="file" name="filename_1" size="50"/><br/>
            <label for="filename_2">File: </label>
            <input id="filename_2" type="file" name="filename_2" size="50"/><br/>
            <br/>
            <input type="submit" value="Upload File"/>
        </form>
    </fieldset>

</body>
</html>