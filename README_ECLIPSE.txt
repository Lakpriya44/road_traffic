SMARTGRID - EC5205 JAVA NETWORK PROGRAMMING PROJECT
==================================================

This is an Eclipse Java Project. It uses standard Java sockets and threads.
No external libraries are required. The code is written to be compatible
with Java 8 or later.

PROJECT CONTENTS
----------------
src/smartgrid/
  ApproachData.java          Movement data for one approach
  TrafficUpdate.java          Four-approach UDP traffic snapshot
  PedestrianRequest.java      TCP pedestrian request
  EmergencyRequest.java       TCP emergency request
  SignalCommand.java          TCP signal instruction
  TrafficStore.java           Shared server data (synchronized methods)
  TrafficController.java      Adaptive and neighbour-aware decision logic
  TrafficCenter.java          Main TCP/UDP server
  JunctionHandler.java        One TCP thread per junction
  UDPReceiver.java             Dedicated UDP receiving thread
  TrafficDecisionThread.java  Periodic decision-making thread
  JunctionClient.java          Junction console application
  UDPClient.java               Sends UDP updates
  TrafficSimulator.java        Automatic and manual traffic input
  PacketTest.java              Tests packet encoding/decoding

PART A - IMPORT INTO ECLIPSE
----------------------------
1. Extract SmartGrid_Eclipse.zip to a folder you can find.
2. Open Eclipse IDE.
3. Choose File > Import...
4. Expand General and choose Existing Projects into Workspace.
5. Click Next.
6. Select "Select root directory" and click Browse.
7. Choose the extracted folder named SmartGrid_Eclipse.
8. Eclipse should show a project named SmartGrid. Tick it.
9. Click Finish.
10. In Package Explorer, expand SmartGrid > src > smartgrid.

If Eclipse asks for a JRE, choose an installed JDK (Java 8 or newer).
If no JDK is installed, install a JDK first and configure it in
Window > Preferences > Java > Installed JREs.

PART B - TEST THE PACKET CLASSES FIRST
--------------------------------------
1. Expand src > smartgrid.
2. Right-click PacketTest.java.
3. Select Run As > Java Application.
4. Check the Console. It should end with:
   Packet test completed successfully.

PART C - RUN THE SERVER
-----------------------
1. Right-click TrafficCenter.java.
2. Select Run As > Java Application.
3. Keep this Console running.
4. Expected messages include:
   [TCP] Listening on port 6655
   [UDP] Listening for traffic updates on port 4442
   [SERVER] Waiting for J01, J02 and J03...

PART D - RUN THREE JUNCTION CLIENTS
-----------------------------------
For each client, right-click JunctionClient.java >
Run As > Run Configurations...

1. Select Java Application and create a new launch configuration.
2. In the Main tab, select project SmartGrid and main class
   smartgrid.JunctionClient.
3. Open the Arguments tab.
4. Under Program arguments, enter the junction ID and server address.

On the SAME computer as the server:
   J01 127.0.0.1

On another computer:
   J02 192.168.1.10
   J03 192.168.1.10

Replace 192.168.1.10 with the actual LAN IPv4 address of the computer
running TrafficCenter.

Click Apply, then Run. Create separate launch configurations for J01,
J02 and J03. On the server computer, J01 can run in another Eclipse
console. J02 and J03 can run on the other two group laptops.

All computers must be connected to the same Wi-Fi/LAN. Allow Java through
the firewall. The server computer must allow:
   TCP port 6655
   UDP port 4442

To find the server LAN IP on Linux, run:
   hostname -I

PART E - CLIENT COMMANDS
------------------------
Each JunctionClient console starts in automatic mode and sends simulated
traffic data every 3 seconds.

Type these commands in a junction console:

  help
      Show commands.

  auto
      Turn automatic traffic simulation on.

  manual
      Turn automatic simulation off.

  traffic
      Enter manual values for NORTH, EAST, SOUTH and WEST.
      For each approach enter five non-negative whole numbers:
      LEFT STRAIGHT RIGHT QUEUE WAIT_SECONDS

      Example for each prompt:
      5 12 3 15 30

  ped NORTH 4
      Request a pedestrian crossing at the NORTH crossing for 4 people.
      Replace NORTH with EAST, SOUTH or WEST if needed.

  emergency NORTH AMBULANCE
      Send an ambulance emergency request from NORTH.
      Supported vehicle types: AMBULANCE, FIRE_TRUCK, POLICE.

  quit
      Disconnect the junction client.

PART F - WHAT TO DEMONSTRATE
----------------------------
1. Start TrafficCenter.
2. Start J01, J02 and J03 clients.
3. Show all three TCP clients connecting to the server.
4. Show UDP traffic updates arriving from all three junctions.
5. Show the server periodically selecting an approach based on vehicle
   counts, queue length and waiting time.
6. Show downstream queue coordination along the E-W corridor:
   J01 EAST -> J02 WEST
   J02 EAST -> J03 WEST
   J03 WEST -> J02 EAST
   J02 WEST -> J01 EAST
7. Type "ped NORTH 4" in a client and show the pedestrian phase.
8. Type "emergency NORTH AMBULANCE" and show emergency priority.
9. Show that the server continues receiving updates while several
   junction clients are connected.

PROTOCOL SUMMARY
----------------
UDP traffic:
TRAFFIC|J01|timestamp|NORTH,left,straight,right,queue,wait|EAST,...|SOUTH,...|WEST,...

TCP client identification:
HELLO|J01

TCP pedestrian request:
PED_REQUEST|J01|NORTH|4|timestamp

TCP emergency request:
EMERGENCY_REQUEST|J02|NORTH|AMBULANCE|timestamp

TCP server signal command:
SIGNAL_COMMAND|J01|VEHICLE|NORTH|15|NORMAL|NONE|timestamp

IMPORTANT SCOPE / SIMULATION NOTES
----------------------------------
- This is an academic software simulation, not a real traffic-light controller.
- Each junction has four approaches and movement-based left/straight/right
  demand counts. These are simulated demand categories, not physical lane
  measurements.
- For a manageable demonstration, one approach is released at a time.
  The simulation does not model detailed vehicle conflict geometry.
- Pedestrian requests are handled as a separate phase with vehicle signals
  shown as red.
- The client displays a yellow transition and an all-red clearance before
  displaying the commanded phase. These are illustrative simulation delays,
  not certified real-world safety timings.
- The decision algorithm is deliberately simple and should be described as
  a prototype heuristic, not a professionally validated traffic algorithm.
- UDP does not guarantee delivery. The next periodic traffic update refreshes
  the server's latest snapshot.
- For a real deployment, detailed signal-phase safety engineering, sensors,
  authentication, fail-safe logic and road authority approval are required.

TROUBLESHOOTING
---------------
"Connection refused":
  Start TrafficCenter first. Check the server IP and TCP port 6655.

"Address already in use":
  Another server may already be running. Stop it before starting another.

No UDP updates:
  Check server IP, UDP port 4442, Wi-Fi/LAN and firewall.

Junction already connected:
  Only one client with a given ID (J01/J02/J03) can be connected at a time.

For a three-laptop demo, do not use 127.0.0.1 on J02/J03. Use the server
laptop's LAN IP address.
