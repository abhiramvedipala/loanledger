"""Starts server.py over stdio, calls each tool as an MCP client would and prints the exchange.
Needs the stack running (docker compose up). Run from mcp-server/ with its .venv active."""
import asyncio
import json
import sys

from mcp import ClientSession, StdioServerParameters
from mcp.client.stdio import stdio_client

LOAN_FIELDS = ("id", "loanNumber", "status", "currentBalance", "nextDueDate", "interestDue", "principalDue", "escrowDue")


def brief(loan):
    return {key: loan[key] for key in LOAN_FIELDS}


async def call(session, tool, arguments, shorten=lambda result: result):
    print(f"> {tool}({json.dumps(arguments)})")
    result = await session.call_tool(tool, arguments)
    if result.is_error:
        print(f"error: {result.content[0].text}\n")
        return None
    data = json.loads(result.content[0].text)
    print(json.dumps(shorten(data), indent=2), "\n")
    return data


async def main():
    params = StdioServerParameters(command=sys.executable, args=["server.py"])
    async with stdio_client(params) as (read, write), ClientSession(read, write) as session:
        await session.initialize()
        tools = await session.list_tools()
        print("tools:", ", ".join(tool.name for tool in tools.tools), "\n")
        delinquent = await call(session, "list_delinquent_loans", {"size": 2},
                                lambda r: {"totalElements": r["totalElements"], "content": [brief(l) for l in r["content"]]})
        loan = delinquent["content"][0]
        await call(session, "get_loan", {"loan_id": loan["id"]}, brief)
        due = round(loan["interestDue"] + loan["principalDue"] + loan["escrowDue"], 2)
        await call(session, "simulate_payment", {"loan_id": loan["id"], "amount": due, "payment_date": "2025-12-01"})
        await call(session, "get_loan", {"loan_id": 999999})


asyncio.run(main())
