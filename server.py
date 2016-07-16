from bottle import route, run, template, static_file, get, post, request

sent = False
cmd = "GREEN_LED 1"
# cmd = "PORT_1_SERVO_POSITION 10240/8"

@route('/')
def index():
    return "running"

@get('/command') # or @route('/login', method='POST')
def command():
    global cmd
    # global sent
    # if sent:
    #     return ""
    # else:
    #     sent = True
    #     return "GREEN_LED 1"
    return cmd

@post('/sendcmd')
def command():
    global cmd
    message = request.forms.get('message')
    print message
    cmd = message
    return "success"

run(host='localhost', port=8080)
