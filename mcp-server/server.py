"""Read-only MCP server over the LoanLedger REST API. Set LOANLEDGER_URL to point it elsewhere."""
import json
import os
import urllib.error
import urllib.request

from mcp.server.mcpserver import MCPServer
from mcp.server.mcpserver.exceptions import ToolError

mcp = MCPServer("loanledger")


def call_api(path, body=None):
    base = os.environ.get("LOANLEDGER_URL", "http://localhost:8080").rstrip("/")
    request = urllib.request.Request(
        f"{base}/api/v1{path}",
        data=None if body is None else json.dumps(body).encode(),
        headers={"Content-Type": "application/json"},
    )
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        # The API answers with RFC 7807 Problem Details; pass its explanation to the caller.
        raise ToolError(json.load(error).get("detail", str(error))) from error


@mcp.tool()
def get_loan(loan_id: int) -> dict:
    """Get one loan: borrower, terms, balance, amounts due, status and escrow."""
    return call_api(f"/loans/{loan_id}")


@mcp.tool()
def list_delinquent_loans(page: int = 0, size: int = 20) -> dict:
    """List delinquent loans, the oldest due date first."""
    return call_api(f"/loans/delinquent?page={page}&size={size}")


@mcp.tool()
def simulate_payment(loan_id: int, amount: float, payment_date: str) -> dict:
    """Show how a payment would be allocated, without saving anything. payment_date is YYYY-MM-DD."""
    return call_api(f"/loans/{loan_id}/payments/simulate", {"amount": amount, "paymentDate": payment_date})


if __name__ == "__main__":
    mcp.run()
