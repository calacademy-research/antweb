#! /usr/local/bin/python3.6

# create new
def application(environ,start_response):

    import sys
    sys.stderr.write(sys.version)
    ver = '' + sys.version

    status = '200 OK'
    html = '<html>\n' \
           '<body>\n' \
           '<div style="width: 100%; font-size: 40px; font-weight: bold; text-align: center;">\n' \
           'mod_wsgi Test Page!\n' \
           '</div>\n' \
           '</body>\n' \
           '</html>\n'
    response_header = [('Content-type','text/html')]
    start_response(status,response_header)
    return [html]
