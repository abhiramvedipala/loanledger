"""Runs the tools against a stub HTTP server. Run with: python -m unittest test_server"""
import json
import os
import threading
import unittest
from http.server import BaseHTTPRequestHandler, HTTPServer

import server


class StubApi(BaseHTTPRequestHandler):
    def reply(self, status, body):
        self.send_response(status)
        self.end_headers()
        self.wfile.write(json.dumps(body).encode())

    def do_GET(self):
        if self.path == "/api/v1/loans/7":
            self.reply(200, {"id": 7})
        elif self.path == "/api/v1/loans/delinquent?page=0&size=20":
            self.reply(200, {"totalElements": 1})
        else:
            self.reply(404, {"detail": "Loan 8 not found"})

    def do_POST(self):
        body = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
        self.reply(200, {"path": self.path, "sent": body})

    def log_message(self, *args):
        pass


class ToolsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.api = HTTPServer(("localhost", 0), StubApi)
        threading.Thread(target=cls.api.serve_forever, daemon=True).start()
        os.environ["LOANLEDGER_URL"] = f"http://localhost:{cls.api.server_port}"

    @classmethod
    def tearDownClass(cls):
        cls.api.shutdown()
        cls.api.server_close()

    def test_get_loan(self):
        self.assertEqual(server.get_loan(7), {"id": 7})

    def test_list_delinquent_loans(self):
        self.assertEqual(server.list_delinquent_loans(), {"totalElements": 1})

    def test_simulate_payment_posts_to_the_simulate_endpoint(self):
        result = server.simulate_payment(7, 100.5, "2025-12-01")
        self.assertEqual(result["path"], "/api/v1/loans/7/payments/simulate")
        self.assertEqual(result["sent"], {"amount": 100.5, "paymentDate": "2025-12-01"})

    def test_api_problem_becomes_a_clear_error(self):
        with self.assertRaisesRegex(server.ToolError, "Loan 8 not found"):
            server.get_loan(8)


if __name__ == "__main__":
    unittest.main()
