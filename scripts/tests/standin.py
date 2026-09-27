# SPDX-FileCopyrightText: 2026 5thlayer
# SPDX-License-Identifier: MIT
#
# A local stand-in for the sites the upload steps send to. It listens on localhost only, records
# every request it gets, and answers the way the site would.
import json
import threading
from email.parser import BytesParser
from email.policy import HTTP
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


class Request:
    def __init__(self, method, path, headers, body):
        self.method, self.path, self.headers, self.body = method, path, headers, body

    def parts(self):
        """The multipart form's parts, by name: (content bytes, filename)."""
        head = f"Content-Type: {self.headers['Content-Type']}\r\n\r\n".encode()
        message = BytesParser(policy=HTTP).parsebytes(head + self.body)
        return {p.get_param("name", header="content-disposition"): (p.get_payload(decode=True), p.get_filename())
                for p in message.iter_parts()}


class StandIn:
    """Plays Modrinth: /v2/project/<id>/version lists `versions[id]`, and POST /v2/version adds one."""

    def __init__(self):
        self.requests = []
        self.versions = {}
        stand_in = self

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *args):
                pass

            def _record(self):
                body = self.rfile.read(int(self.headers.get("Content-Length") or 0))
                request = Request(self.command, self.path, dict(self.headers), body)
                stand_in.requests.append(request)
                return request

            def _answer(self, status, payload):
                data = json.dumps(payload).encode()
                self.send_response(status)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(data)))
                self.end_headers()
                self.wfile.write(data)

            def do_GET(self):
                self._record()
                parts = self.path.split("/")
                if len(parts) == 5 and parts[1:3] == ["v2", "project"] and parts[4] == "version":
                    self._answer(200, [{"version_number": v} for v in stand_in.versions.get(parts[3], [])])
                else:
                    self._answer(404, {"error": "not_found"})

            def do_POST(self):
                request = self._record()
                if self.path != "/v2/version":
                    return self._answer(404, {"error": "not_found"})
                data = json.loads(request.parts()["data"][0])
                stand_in.versions.setdefault(data["project_id"], []).append(data["version_number"])
                self._answer(200, {"id": "standin", "version_number": data["version_number"]})

        self.server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self.url = f"http://127.0.0.1:{self.server.server_address[1]}"
        threading.Thread(target=self.server.serve_forever, daemon=True).start()

    def close(self):
        self.server.shutdown()
        self.server.server_close()
