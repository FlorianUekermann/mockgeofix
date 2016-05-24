#!/usr/bin/env python

# GPX from google maps route: http://www.gpsvisualizer.com/convert_input

### stdlib packages
import os
import socket
import argparse
import sys
import select
import time
import threading
import thread
import urllib
import json
###

INDEX = "/whereami/setloc.html"
UPDATE_INTERVAL = 0.3

# curr_lat = 48.7833
# curr_lon = 9.1833
curr_lat = None  # updated by http thread, read by main thread
curr_lon = None   # updated by http thread, read by main thread


def main(args):
    global curr_lon
    global curr_lat

    http_thread = threading.Thread(target=start_http_server, args=(args,))
    http_thread.daemon = True
    http_thread.start()
    time.sleep(1)

    s = socket.socket()
    try:
        s.connect((args.ip, args.port))
    except socket.error as ex:
        print("Error connecting to %s:%s (%s)" % (args.ip, args.port, ex))
        sys.exit(2)

    try:
        while True:
            rlist, wlist, _ = select.select([s], [s], [])
            if s in rlist:
                x = s.recv(1024)
                if "KO: password required" in x:
                    s.close()
                    print("Password protection is enabled MockGeoFix settings. This is not supported.")
                    sys.exit(2)
                if x == '':
                    s.close()
                    print("Connection closed.")
                    sys.exit(2)
            if s in wlist:
                if curr_lon is not None and curr_lat is not None:
                    s.send("geo fix %f %f\r\n" % (curr_lon, curr_lat))
            time.sleep(UPDATE_INTERVAL)
    except socket.error as ex:
        print(ex)
        thread.interrupt_main()


def start_http_server(args):
    import SimpleHTTPServer
    import SocketServer

    class Handler(SimpleHTTPServer.SimpleHTTPRequestHandler):
        def do_GET(self):
            if args.no_web and not self.path.startswith("/send_position/"):
                self.send_response(400)
                self.end_headers()
                return
            if self.path.strip() == "/":
                self.send_response(301)
                self.send_header("Location", INDEX)
                self.end_headers()
                return None
            if self.path.startswith("/send_position/"):
                return self.send_position()
            return SimpleHTTPServer.SimpleHTTPRequestHandler.do_GET(self)

        def send_position(self):
            global curr_lat
            global curr_lon
            try:
                json_str = urllib.unquote(self.path[15:])  # strip "/send_position/"
                dct = json.loads(json_str)
                curr_lat = dct["lat"]
                for k in "lon", "lng", "long":
                    if k in dct:
                        curr_lon = dct[k]
                        break
                response_code = 200
            except:
                response_code = 400

            self.send_response(response_code)
            self.send_header("Content-type", "application/json")
            self.send_header("Content-Length", "0")
            self.end_headers()

        def list_directory(self, _):
            self.path = "/"
            return self.do_GET()

        def log_message(self, *_):
            return

    class TCPServer(SocketServer.TCPServer):
        def server_bind(self):
            self.socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            self.socket.bind(self.server_address)

        def handle_error(self, request, client_address):
            if sys.exc_info()[0] == socket.error:
                return  # client probably closed connection
            return SocketServer.TCPServer.handle_error(self, request, client_address)

    try:
        httpd = TCPServer((args.listen_ip, args.listen_port), Handler)
    except Exception as ex:
        print("Error starting HTTP server: %s" % ex)
        thread.interrupt_main()
        thread.exit()

    print("Open http://%s:%s in your web browser." % (args.listen_ip, args.listen_port))
    httpd.serve_forever()

if __name__ == '__main__':
    args_parser = argparse.ArgumentParser(
        description=("Provides /send_position/ endpoint which accepts a json dictionary "
                     "with 'lat' and 'lon' fields. Also provides a simple js application "
                     "with a map you can set the location mock (protip: try wasd and qezxc)"))
    args_parser.add_argument("-i", "--ip", help="connect to MockGeoFix using this IP address",
                             required=True)
    args_parser.add_argument("-p", "--port", default=5554, help="default: 5554", type=int)
    args_parser.add_argument("-I", "--listen-ip", help="Run the HTTP server on this ip.",
                             required=True)
    args_parser.add_argument("-P", "--listen-port", help="HTTP server's port (default: 8080)",
                             required=False, default=8080, type=int)
    args_parser.add_argument("-n", "--no-web", default=False, action="store_true",
                             help="Don't serve any html, js, css files. Only provide the endpoint.")

    args = args_parser.parse_args()

    os.chdir(os.path.dirname(os.path.abspath(__file__)))
    try:
        main(args)
    except KeyboardInterrupt:
        print("Exiting.")
