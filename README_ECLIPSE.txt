SMARTGRID - EC5205 JAVA NETWORK PROGRAMMING PROJECT
==================================================

This is an Eclipse Java project using standard Java sockets and threads.
No external libraries are required. The source is compatible with Java 8+
(the project has also been checked with Java 21).

PROJECT CONTENTS
----------------
src/smartgrid/
  SmartGridConfig.java        Shared ports, timeouts and simulation limits
  ApproachData.java           Movement data for one approach
  TrafficUpdate.java           Four-approach UDP traffic snapshot
  PedestrianRequest.java      TCP pedestrian request
  EmergencyRequest.java       TCP emergency request
  SignalCommand.java          TCP signal instruction
  TrafficStore.java           Shared traffic data and bounded request queues
  TrafficController.java      Adaptive and neighbour-aware decision logic
  TrafficCenter.java          Main TCP/UDP server
  JunctionHandler.java        One TCP thread per junction
  UDPReceiver.java             Dedicated UDP receiving thread
  TrafficDecisionThread.java  Per-junction signal scheduler
  JunctionClient.java          Junction console application
  UDPClient.java               Sends UDP updates
  TrafficSimulator.java        Stateful automatic and manual traffic input
  PacketTest.java              Dependency-free packet tests

PART A - IMPORT INTO ECLIPSE
----------------------------
1. Extract SmartGrid_Eclipse.zip.
2. Open Eclipse IDE.
3. Choose File > Import...
4. Expand General and choose Existing Projects into Workspace.
5. Click Next.
6. Select "Select root directory" and browse to the extracted
   SmartGrid_Eclipse folder.
7. Tick the SmartGrid project and click Finish.
8. If Eclipse asks for a JRE, select an installed JDK (Java 8 or newer).

PART B - RUN PACKET TESTS
------------------------
1. Expand src > smartgrid.
2. Right-click PacketTest.java.
3. Select Run As > Java Application.
4. The Console should finish with:
   All packet tests passed (4 round-trips + 5 invalid-input tests).

PART C - RUN THE SERVER (LAPTOP 1)
----------------------------------
1. Right-click TrafficCenter.java.
2. Select Run As > Java Application.
3. Keep the server Console running.
4. Expected messages:
   [TCP] Listening on port 6655
   [UDP] Listening for traffic updates on port 4442
   [SERVER] Waiting for J01, J02 and J03...

PART D - RUN THE THREE JUNCTION CLIENTS
----------------------------------------
For each client, right-click JunctionClient.java > Run As > Run Configurations.
Create a separate Java Application configuration for each junction.

Main tab:
  Project: SmartGrid
  Main class: smartgrid.JunctionClient

Arguments tab > Program arguments:
  Laptop 1 (server laptop): J01 127.0.0.1
  Laptop 2:                 J02 SERVER_LAN_IP
  Laptop 3:                 J03 SERVER_LAN_IP

Replace SERVER_LAN_IP with the IPv4 address of Laptop 1.
On Laptop 1, run the server and J01 client in separate Eclipse consoles.

All three laptops must be on the same Wi-Fi/LAN. Allow Java through the
server laptop firewall. Required inbound ports on Laptop 1:
  TCP 6655
  UDP 4442

To find Laptop 1's IPv4 address on Linux:
  hostname -I

Do not use 127.0.0.1 for J02 or J03. It means that laptop itself, not Laptop 1.

PART E - CLIENT CONSOLE COMMANDS
--------------------------------
Each client starts in automatic mode and sends a simulated traffic update
every 3 seconds. It also sends a TCP heartbeat every 10 seconds.

  help
      Show commands.

  auto
      Enable automatic simulated traffic data.

  manual
      Stop automatic traffic updates (TCP heartbeat continues). If you have
      already entered a snapshot with 'traffic', that snapshot keeps being
      re-sent every 3 seconds (fresh timestamp) so it does not expire.

  traffic
      Switches to manual mode and sends the values you type; the same
      snapshot is then re-sent every 3 seconds until you type 'auto'.
      Enter manual values for NORTH, EAST, SOUTH and WEST.
      For each approach enter five whole numbers:
      LEFT STRAIGHT RIGHT QUEUE WAIT_SECONDS
      Example: 5 12 3 15 30

  ped NORTH 4
      Queue a pedestrian crossing request at NORTH for 4 people.
      Use NORTH, EAST, SOUTH or WEST.

  emergency NORTH AMBULANCE
      Queue an emergency request arriving from NORTH.
      Supported types: AMBULANCE, FIRE_TRUCK, POLICE.

  quit
      Disconnect the junction client.

PART F - DEMONSTRATION ORDER
----------------------------
1. Start TrafficCenter on Laptop 1.
2. Start J01 on Laptop 1, J02 on Laptop 2, J03 on Laptop 3.
3. Confirm all three TCP connections appear on the server.
4. Confirm UDP updates from all three junctions appear every few seconds.
5. Show the server selecting a vehicle approach and sending a signal command.
6. Show the client displaying yellow, all-red clearance, then the green phase
   for the commanded duration. The next command is scheduled after that phase
   completes; commands are not intentionally overlapped.
7. Type "ped NORTH 4" and show the queued pedestrian phase.
8. Type "emergency NORTH AMBULANCE" and show emergency priority.
9. Show the simulator's selected vehicle queue decreasing after its green.
   (Simulated arrivals are low enough that queues rise and fall, typically
   staying below about 40 vehicles, so green times vary between roughly 8 and
   25 seconds instead of sitting at the maximum.)

NETWORK / CONTROL BEHAVIOUR
---------------------------
- TCP port 6655 carries client identification, requests, acknowledgements,
  heartbeats and signal commands.
- UDP port 4442 carries periodic traffic snapshots.
- UDP updates are accepted only when their source IP matches the IP address
  registered by that junction's TCP connection.
- The server measures traffic freshness using its own receive time. A traffic
  snapshot older than 10 seconds is ignored. Out-of-order timestamps from the
  same client are rejected.
- Each junction has bounded pedestrian and emergency request queues. A request
  is acknowledged only after it has been accepted into the queue. Emergency
  requests are selected before pedestrian requests.
- Requests are served at the next available phase boundary. The prototype does
  not interrupt a currently displayed green phase; this avoids overlapping
  commands and keeps the demonstration logic simple.
- Clients send a TCP heartbeat every 10 seconds. The server closes a connection
  that has not sent a heartbeat within 30 seconds and clears that junction's
  stored traffic and pending requests.
- Vehicle green duration is 8 to 25 seconds. The client shows a 1-second yellow
  transition and a 1-second all-red clearance before the commanded green.
  The server schedules the next phase after clearance plus active duration.
- The automatic simulator accumulates arrivals and reduces the selected
  approach's simulated queue when its vehicle green phase completes.

ROAD DIRECTION CONVENTION / CORRIDOR
------------------------------------
NORTH, EAST, SOUTH and WEST mean the SIDE FROM WHICH VEHICLES ARRIVE at a
junction, not the direction in which they travel after crossing it.

Eastbound corridor:
  J01 WEST -> J02 WEST -> J03 WEST

Westbound corridor:
  J03 EAST -> J02 EAST -> J01 EAST

The neighbour-aware controller uses the downstream queue at the next junction
for these four links. The north and south approaches are side roads.

IMPORTANT ACADEMIC SCOPE
------------------------
- This is an academic software simulation, not a real traffic-light controller.
- Each junction has four approaches and movement-based left/straight/right
  demand counts. These are simulated logical demand categories, not physical
  lane measurements.
- One vehicle approach is released at a time. The program does not model full
  vehicle conflict geometry or turning-path conflicts.
- A pedestrian phase is represented by all vehicle approaches red and one
  crossing direction active.
- Yellow and all-red timings are illustrative, not certified safety timings.
- The controller is a simple heuristic prototype, not a professionally
  validated traffic engineering algorithm.
- UDP does not guarantee delivery; the next periodic snapshot refreshes data.
- For real deployment, detailed signal-phase engineering, authenticated
  devices, fail-safe hardware, sensors and road-authority approval are required.

TROUBLESHOOTING
---------------
"Connection refused":
  Start TrafficCenter first. Check the server laptop IP and TCP port 6655.

"Connection timed out":
  Check the server IP, same Wi-Fi/LAN, firewall and TCP port 6655.

"Address already in use":
  Another server may be running. Stop it before starting a second server.

No UDP updates:
  Check UDP port 4442, firewall, server IP and that the TCP client connected
  successfully first. The server accepts UDP only from the registered TCP IP.

Clients connect over TCP but no UDP updates appear (or J02/J03 cannot connect):
  Some university/public Wi-Fi networks block laptop-to-laptop traffic
  ("client isolation"). Use a phone hotspot or a switch/router you control.
  Also open UDP 4442 (not only TCP 6655) in the server laptop firewall, and
  turn off VPN / virtual network adapters that may change the source IP.

"Junction already connected":
  Only one client with a given ID (J01/J02/J03) can be connected at a time.
  Close the old client process before starting another one with that ID.

If the server closes unexpectedly, the client should print a disconnect message
and stop instead of waiting indefinitely for another keyboard command.


LIVE DASHBOARD (for the evaluation panel)
=========================================
When TrafficCenter starts it also serves a live web dashboard on port 8080.
Open it in any browser:

  Laptop 1:         http://localhost:8080
  Another laptop:   http://SERVER_LAN_IP:8080   (allow TCP 8080 in the firewall)

It shows each junction's signal lights, queues, demand scores, the active
phase with a countdown, emergency / pedestrian phases, counters, and a live
event log (Network, Requests, and the raw Server console tab).
It is read-only and updates by itself; all command-line commands on the clients
(ped, emergency, manual, traffic, auto, quit) work exactly as before, and the
server console keeps printing at the same time.

Notes:
- Run the server from the project folder (Eclipse does this by default) because
  the page is read from web/dashboard.html.
- The dashboard needs no internet; it uses no external libraries or fonts.
- If port 8080 is busy, the server prints a warning and keeps running without it.
