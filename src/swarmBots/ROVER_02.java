package swarmBots;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import common.Coord;
import common.MapTile;
import common.ScanMap;
import enums.Direction;
import enums.Science;
import enums.Terrain;

/**
 * The seed that this program is built on is a chat program example found here:
 * http://cs.lmu.edu/~ray/notes/javanetexamples/ Many thanks to the authors for
 * publishing their code examples
 */

public class ROVER_02 {

    BufferedReader in;
    PrintWriter out;
    String rovername;
    ScanMap scanMap;
    int sleepTime;
    String SERVER_ADDRESS = "localhost";
    static final int PORT_ADDRESS = 9537;
    private int totalTiles = 2500;
    private int xTile = 50;
    private int yTile = 50;

    Direction currentDirection = Direction.SOUTH;
    Coord cc = null;
    HashSet<Coord> science_collection = new HashSet<Coord>(); //Science collected by the rover and the coords of extraction
    HashSet<Coord> displayed_science = new HashSet<Coord>(); //Science found by the rover
    HashMap<Coord, MapTile> explored_map = new HashMap<Coord, MapTile>(); //Map explored by the rover
    List<Socket> sockets = new ArrayList<Socket>();

    // just means it did not change locations between requests, could be
    // velocity limit or obstruction etc.
    boolean stuck = false;
    boolean blocked = false;

    public ROVER_02() {
        System.out.println("ROVER_02 rover object constructed");
        rovername = "ROVER_02";
        SERVER_ADDRESS = "localhost";

        // this should be a safe but slow timer value
        // in milliseconds - smaller is faster, but the server
        // will cut connection if it is too small
        sleepTime = 300;

    }

    public ROVER_02(String serverAddress) {
        System.out.println("ROVER_02 rover object constructed");
        rovername = "ROVER_02";
        SERVER_ADDRESS = serverAddress;

        // in milliseconds - smaller is faster, but the server
        // will cut connection if it is too small
        sleepTime = 200;

    }

    class RoverComm implements Runnable {

        String ip;
        int port;
        Socket socket;

        public RoverComm(String ip, int port) {
            this.ip = ip;
            this.port = port;
        }

        @Override
        public void run() {
            do {
                try {
                    socket = new Socket(ip, port);
                } catch (UnknownHostException e) {
                    System.out.println(e);
                } catch (IOException e) {
                    System.out.println(e);
                }
            } while (socket == null);
            sockets.add(socket);
            System.out
                    .println(socket.getPort() + " " + socket.getInetAddress());
        }

    }

    /**
     * add all rover's ip and port number into a list so they can be connected
     */
    

    /**
     * Connects to the server then enters the processing loop.
     */
    private void run() throws IOException, InterruptedException {

        // Make connection and initialize streams
        Socket socket = new Socket(SERVER_ADDRESS, PORT_ADDRESS);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(), true);

        // Gson gson = new GsonBuilder().setPrettyPrinting().create();

        // Process all messages from server, wait until server requests Rover ID
        // name

        // connect to all all the other rovers
        

        while (true) {
            String line = in.readLine();
            if (line.startsWith("SUBMITNAME")) {

                // This sets the name of this instance of a swarmBot for
                // identifying thread to the server
                out.println(rovername);
                break;
            }
        }

        // ******** Rover logic *********
        // int cnt=0;
        String line = "";

        String[] cardinals = new String[4];
        cardinals[0] = "N";
        cardinals[1] = "E";
        cardinals[2] = "S";
        cardinals[3] = "W";

        String currentDir = cardinals[0];
        Coord currentLoc = null;
        Coord previousLoc = null;

        // start Rover controller process
        while (true) {

            // currently the requirements allow sensor calls to be made with no
            // simulated resource cost

            // **** location call ****
            out.println("LOC");
            line = in.readLine();
            if (line == null) {
                System.out.println("ROVER_02 check connection to server");
                line = "";
            }
            if (line.startsWith("LOC")) {
                // loc = line.substring(4);
                currentLoc = extractLOC(line);
                cc = new Coord(Integer.valueOf(line.split(" ")[1]),
                        Integer.valueOf(line.split(" ")[2]));
            }
            System.out.println("ROVER_02 currentLoc at start: " + currentLoc);

            // after getting location set previous equal current to be able to
            // check for stuckness and blocked later
            previousLoc = currentLoc;

            // **** get equipment listing ****
            ArrayList<String> equipment = new ArrayList<String>();
            equipment = getEquipment();
            // System.out.println("ROVER_02 equipment list results drive " +
            // equipment.get(0));
            System.out.println(
                    "ROVER_02 equipment list results " + equipment + "\n");

            // ***** do a SCAN *****
            // System.out.println("ROVER_02 sending SCAN request");
            this.doScan();
            scanMap.debugPrintMap();
            this.Gather();

            // ***** MOVING *****

            // pull the MapTile array out of the ScanMap object
            MapTile[][] scanMapTiles = scanMap.getScanMap();
            int centerIndex = (scanMap.getEdgeSize() - 1) / 2;
            addScanMap(scanMapTiles, cc, scanMap.getEdgeSize());
            Iterator it = explored_map.entrySet().iterator();
            while(it.hasNext()) {
            	Map.Entry pair = (Map.Entry)it.next();
                System.out.println(pair.getKey().toString() + " = " + ((MapTile) pair.getValue()).getTerrain().getTerString());
                it.remove();
            }
            // tile S = y + 1; N = y - 1; E = x + 1; W = x - 1

            // ***************************************************

            masterMove(currentDirection, scanMapTiles, centerIndex);
            shareScience();
            
            
            

            // ***********************************************************
            
         
              

            // ***************************************************

            // another call for current location
            out.println("LOC");
            line = in.readLine();
            if (line == null) {
                System.out.println("ROVER_02 check connection to server");
                line = "";
            }
            if (line.startsWith("LOC")) {
                currentLoc = extractLOC(line);
            }

            // System.out.println("ROVER_02 currentLoc after recheck: " +8
            // currentLoc);
            // System.out.println("ROVER_02 previousLoc: " + previousLoc);

            // test for stuckness
            stuck = currentLoc.equals(previousLoc);

            // System.out.println("ROVER_02 stuck test " + stuck);
            System.out.println("ROVER_02 blocked test " + blocked);

            // TODO - logic to calculate where to move next

            Thread.sleep(sleepTime);

            System.out.println(
                    "ROVER_02 ------------ bottom process control --------------");
        }

    }

    // ################ Support Methods ###########################
    public void Gather(){
    	out.println("GATHER");
	
	
    }
    private void clearReadLineBuffer() throws IOException {
        while (in.ready()) {
            // System.out.println("ROVER_02 clearing readLine()");
            String garbage = in.readLine();
        }
    }

    // method to retrieve a list of the rover's equipment from the server
    private ArrayList<String> getEquipment() throws IOException {
        // System.out.println("ROVER_02 method getEquipment()");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        out.println("EQUIPMENT");

        String jsonEqListIn = in.readLine(); // grabs the string that was
                                             // returned first
        if (jsonEqListIn == null) {
            jsonEqListIn = "";
        }
        StringBuilder jsonEqList = new StringBuilder();
        // System.out.println("ROVER_02 incomming EQUIPMENT result - first
        // readline: " + jsonEqListIn);

        if (jsonEqListIn.startsWith("EQUIPMENT")) {
            while (!(jsonEqListIn = in.readLine()).equals("EQUIPMENT_END")) {
                if (jsonEqListIn == null) {
                    break;
                }
                // System.out.println("ROVER_02 incomming EQUIPMENT result: " +
                // jsonEqListIn);
                jsonEqList.append(jsonEqListIn);
                jsonEqList.append("\n");
                // System.out.println("ROVER_02 doScan() bottom of while");
            }
        } else {
            // in case the server call gives unexpected results
            clearReadLineBuffer();
            return null; // server response did not start with "EQUIPMENT"
        }

        String jsonEqListString = jsonEqList.toString();
        ArrayList<String> returnList;
        returnList = gson.fromJson(jsonEqListString,
                new TypeToken<ArrayList<String>>() {
                }.getType());
        // System.out.println("ROVER_02 returnList " + returnList);

        return returnList;
    }

    // sends a SCAN request to the server and puts the result in the scanMap
    // array
    public void doScan() throws IOException {
        // System.out.println("ROVER_02 method doScan()");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        out.println("SCAN");

        String jsonScanMapIn = in.readLine(); // grabs the string that was
                                              // returned first
        if (jsonScanMapIn == null) {
            System.out.println("ROVER_02 check connection to server");
            jsonScanMapIn = "";
        }
        StringBuilder jsonScanMap = new StringBuilder();
        System.out.println("ROVER_02 incomming SCAN result - first readline: "
                + jsonScanMapIn);

        if (jsonScanMapIn.startsWith("SCAN")) {
            while (!(jsonScanMapIn = in.readLine()).equals("SCAN_END")) {
                // System.out.println("ROVER_02 incomming SCAN result: " +
                // jsonScanMapIn);
                jsonScanMap.append(jsonScanMapIn);
                jsonScanMap.append("\n");
                // System.out.println("ROVER_02 doScan() bottom of while");
            }
        } else {
            // in case the server call gives unexpected results
            clearReadLineBuffer();
            return; // server response did not start with "SCAN"
        }
        // System.out.println("ROVER_02 finished scan while");

        String jsonScanMapString = jsonScanMap.toString();
        // debug print json object to a file
        // new MyWriter( jsonScanMapString, 0); //gives a strange result -
        // prints the \n instead of newline character in the file

        // System.out.println("ROVER_02 convert from json back to ScanMap
        // class");
        // convert from the json string back to a ScanMap object
        scanMap = gson.fromJson(jsonScanMapString, ScanMap.class);
    }

    // this takes the LOC response string, parses out the x and x values and
    // returns a Coord object
    public static Coord extractLOC(String sStr) {
        sStr = sStr.substring(4);
        if (sStr.lastIndexOf(" ") != -1) {
        	String xStr = sStr.substring(0, sStr.lastIndexOf(" "));
            System.out.println("extracted xStr " + xStr);

            String yStr = sStr.substring(sStr.lastIndexOf(" ") + 1);
            System.out.println("extracted yStr " + yStr);
            
            return new Coord(Integer.parseInt(xStr), Integer.parseInt(yStr));
        }
        return null;
    }

    /** determine if the rover is about to reach a "blocked" tile */
    public boolean isNextBlock(Direction direction, MapTile[][] scanMapTiles,
            int centerIndex) {

        switch (direction) {
        case NORTH:
            return isBlocked(scanMapTiles[centerIndex][centerIndex - 1]);
        case SOUTH:
            return isBlocked(scanMapTiles[centerIndex][centerIndex + 1]);
        case WEST:
            return isBlocked(scanMapTiles[centerIndex - 1][centerIndex]);
        case EAST:
            return isBlocked(scanMapTiles[centerIndex + 1][centerIndex]);
        default:
            // this code should be unreachable
            return false;
        }
    }

    /** determine if the rover is on ROCK NONE OR SAND */
    private boolean isBlocked(MapTile tile) {
        List<Terrain> blockers = Arrays.asList(Terrain.NONE,
                Terrain.SAND);
        Terrain terrain = tile.getTerrain();
        return tile.getHasRover() || blockers.contains(terrain);
    }

    /** return a DIFFERENT direction */
    private Direction changeDirection(Direction direction) {
        switch (direction) {
        case NORTH:
            return Direction.WEST;
        case SOUTH:
            return Direction.EAST;
        case WEST:
            return Direction.SOUTH;
        case EAST:
            return Direction.NORTH;
        default:
            return null;
        }
    }

    /** move the rover one tile */
    private void move(Direction direction) {
        switch (direction) {
        case NORTH:
            out.println("MOVE N");
            break;
        case SOUTH:
            out.println("MOVE S");
            break;
        case WEST:
            out.println("MOVE W");
            break;
        case EAST:
            out.println("MOVE E");
            break;
        }
    }

    /**
     * recursively call itself until it find a direction that won't lead to a
     * blocked path
     */
    private Direction findGoodDirection(Direction direction,
            MapTile[][] scanMapTiles, int centerIndex) {

        if (isNextBlock(direction, scanMapTiles, centerIndex)) {
            return findGoodDirection(changeDirection(direction), scanMapTiles,
                    centerIndex);
        } else {
            return direction;
        }
    }

    /** the rover move logic */
    private void masterMove(Direction direction, MapTile[][] scanMapTiles,
            int centerIndex) {
        detectRadioactive(scanMapTiles);
        if (isNextBlock(direction, scanMapTiles, centerIndex)) {
            Direction goodDirection = findGoodDirection(direction, scanMapTiles,
                    centerIndex);
            if (isNextEdge(direction, scanMapTiles, centerIndex)) {
                currentDirection = findGoodDirection(direction, scanMapTiles,
                        centerIndex);
                move(currentDirection);
            } else {
                move(goodDirection);
            }

        } else {
            move(direction);
        }
    }

    /**
     * determine if the rover is about to reach a "NONE" tile. use to indicate
     * that you've reach the edge of the map and may need to permantly change
     * direction
     */
    private boolean isNextEdge(Direction direction, MapTile[][] scanMapTiles,
            int centerIndex) {

        switch (direction) {
        case NORTH:
            return isNone(scanMapTiles[centerIndex][centerIndex - 1]);
        case SOUTH:
            return isNone(scanMapTiles[centerIndex][centerIndex + 1]);
        case WEST:
            return isNone(scanMapTiles[centerIndex - 1][centerIndex]);
        case EAST:
            return isNone(scanMapTiles[centerIndex + 1][centerIndex]);
        default:
            // this code should be unreachable
            return false;
        }
    }

    /**
     * iterate through a scan map to find a tile with radiation. get the
     * adjusted (absolute) coordinate of the tile and added into a hash set
     */
    private void detectRadioactive(MapTile[][] scanMapTiles) {
        for (int x = 0; x < scanMapTiles.length; x++) {
            for (int y = 0; y < scanMapTiles[x].length; y++) {
                MapTile mapTile = scanMapTiles[x][y];
                if (mapTile.getScience() == Science.RADIOACTIVE) {
                    int tileX = cc.xpos + (x - 5);
                    int tileY = cc.ypos + (y - 5);
                    System.out.println("Radioactive Location: [x:" + tileX
                            + " y: " + tileY);
                    science_collection.add(new Coord(tileX, tileY));
                }
            }
        }
    }

    /** determine if the tile is NONE */
    private boolean isNone(MapTile tile) {
        return tile.getTerrain() == Terrain.NONE;
    }

    /**
     * write to each rover the coords of a tile that contains radiation. will
     * only write to them if the coords haven't is new.
     */
    public void shareScience() {
        for (Coord c : science_collection) {
            if (!displayed_science.contains(c)) {
                for (Socket s : sockets)
                    try {
                        new DataOutputStream(s.getOutputStream())
                                .writeBytes(c.toString() + "\r\n");
                    } catch (Exception e) {

                    }
                displayed_science.add(c);
            }
            System.out.println("Minerals located at");
            System.out.println("x cord"+c.xpos); System.out.println("y cord"+c.ypos);
        }
    }
    
    public void addScanMap(MapTile[][] m, Coord c, int edgeSize) {
    	int cornX = c.xpos - 3;
		int cornY = c.ypos - 3;
		for (int i = 0; i < edgeSize; i++) {
			for (int j = 0; j < edgeSize; j++) {
				explored_map.put(new Coord(cornX + j, cornY + i), m[j][i]);
			}
		}
    }

    /**
     * Runs the client
     */

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println(1);
            new ROVER_02().run();
        } else {
            System.out.println(2);
            new ROVER_02("localhost").run();
        }
    }

}
