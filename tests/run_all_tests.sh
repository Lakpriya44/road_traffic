#!/bin/bash
# Runs every automated test. Run from the project root:  bash tests/run_all_tests.sh
# Needs a JDK (javac) and, for the dashboard tests, Node.js 18+.
set -e
rm -rf /tmp/sg_bin && mkdir /tmp/sg_bin
javac -Xlint:all -encoding UTF-8 -d /tmp/sg_bin src/smartgrid/*.java
echo "== Java: packet tests";  java -cp /tmp/sg_bin smartgrid.PacketTest
echo "== Java: gateway tests"; java -cp /tmp/sg_bin smartgrid.GatewayTest
if command -v node >/dev/null; then echo "== Dashboard logic tests"; node tests/dashboard_tests.js; else echo "(Node.js not installed: dashboard tests skipped)"; fi
