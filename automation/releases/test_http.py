"""Smoke не должен пересылать Basic Auth даже через перенаправление localhost."""
import http.server
import threading
import unittest
import smoke


class HttpBoundaryTest(unittest.TestCase):
    def test_redirect_is_reported_without_forwarding_gateway_credentials(self):
        forwarded = []
        class Handler(http.server.BaseHTTPRequestHandler):
            def do_GET(self):
                if self.path == '/other':
                    forwarded.append(self.headers.get('Authorization'))
                    self.send_response(200)
                else:
                    self.send_response(302)
                    self.send_header('Location', 'http://localhost:' + str(self.server.server_port) + '/other')
                self.end_headers()

            def log_message(self, *_):
                pass

        server = http.server.HTTPServer(('127.0.0.1', 0), Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            response = smoke.Http('http://127.0.0.1:' + str(server.server_port), 'Basic synthetic')('/')
            self.assertEqual(response.status, 302)
            self.assertEqual(forwarded, [])
        finally:
            server.shutdown()
            server.server_close()
            thread.join()
