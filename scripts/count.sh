#!/usr/bin/env bash
# Prints the endpoint count (OpenAPI JSON) and test count (Gradle report).
# Both files come from `./gradlew test`; ApiIntegrationTest exports build/openapi.json.
set -euo pipefail
cd "$(dirname "$0")/.."

python3 - <<'EOF'
import glob, json
import xml.etree.ElementTree as ET

methods = {"get", "post", "put", "patch", "delete"}
paths = json.load(open("build/openapi.json"))["paths"]
print("endpoints:", sum(1 for ops in paths.values() for m in ops if m in methods))

suites = [ET.parse(f).getroot() for f in glob.glob("build/test-results/test/*.xml")]
print("tests:", sum(int(s.get("tests")) for s in suites))
print("failures:", sum(int(s.get("failures")) + int(s.get("errors")) for s in suites))
print("mcp_tests:", open("mcp-server/test_server.py").read().count("    def test_"))
EOF
