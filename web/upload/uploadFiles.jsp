
<html>
   <head>
      <title>File Uploading Form</title>
   </head>
   
   <body>
      <h3>File Upload:</h3>

		Select a file to upload: <br />
		<form action = "UploadServlet" method = "post"
		   enctype = "multipart/form-data">
		   <input type = "file" name = "file" size = "50" />
		   <br />
		   <input type = "submit" value = "Upload File" />
		</form>


		<form action="UploadFileServlet" method="post">
		<input type="text" name="description" />
		<input type="file" name="file" />
		<input type="submit" />
		</form>


   </body>

</html>



