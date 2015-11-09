import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.Collections;
import java.util.Collection;
import java.util.Scanner;
import java.net.Socket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.io.File;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * FILE: LibraryClient.java
 *
 * Client program to be used with a distributed system modeled in
 * LibraryServer.java. Requires a special configuration file where the first
 * line indicates how many servers there are and the following lines contain the
 * addresses of the servers with their respective port (ex: 127.0.0.1:1234).
 *
 * Developed as part of a class assignment (CSE535 -- SUNY Korea)
 *
 * @author Delvison Castillo (delvison.castillo@sunykorea.ac.kr)
 *
 */

public class LibraryClient
{
	private File f; // file object for config file 
	private Socket host; // host that the client is connected to
	private boolean debug = true; // debug flag
	private boolean isConnected; // indicator of connection status
	private PrintWriter sockOut; // for writing out on the socket
	private PrintWriter output; // for writing output to a file 
	private BufferedReader sockIn; // for reading in from the socket
	private String configFile = ".clientConfig.dat"; // config file location
	private HashMap<String,Boolean> servers; // < [addr:status], ...>
	private String clientID; // clientID given by server
	private ArrayList<String> commands;

	private final String GREEN = "\033[92m";
	private final String RED = "\033[91m";
	private final String ENDC = "\033[0m";
	private final String YELLOW = "\u001B[33m";
	private final String BLUE = "\u001B[34m"; 

	// file output

	/**
	 * Constructor method. Initializes the hashmap used to hold the addresses of
	 * the servers as well as their status. Calls initialize. If initialize is
	 * successful, then the program enters its main loop.
	 */
	public LibraryClient(String clientID)
	{
		this.clientID = clientID;
		servers = new HashMap<String,Boolean>();
		commands = new ArrayList<String>();
		if (initialize()) {
			if (!commands.isEmpty()) runCommands();
			mainLoop();
		} else {
			System.out.println("No connections.");
			terminate();
		}
	}

	/**
	 * Initializes the program.
	 * @return boolean indicating whether or not the program was successfully
	 * initialized.
	 */
	private boolean initialize()
	{
		try 
		{
			System.out.println("Initializing...");
			f = new File(configFile);
			Scanner s = new Scanner(f);
			// n <- read in number of servers available
			int servNum = s.nextInt();
			debug("number of servers: "+servNum);
			// add servers to hashmap of servers
			for (int i = 0;i<=servNum;i++) {
				String j = s.nextLine();
				if (j.contains(":")) {
					servers.put(j, true);
					//debug("added server "+j);
				}
			}
			// read commands
			while (s.hasNext())	{
				String cmd = s.nextLine();
				commands.add(cmd);
			}
			s.close();
			// connect to a random server
		  return connect() ? true : false;
		} catch (Exception e) 
		{
			debug("initialize():File read error: "+configFile);
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * Updates the socket with a server that is live chosen at random from the
	 * list of available servers. 
	 * @return boolean indicating whether or not the program was able to connect 
	 * to a server.
	 */
	private boolean connect()
	{
		//debug("Entering connect()");
		boolean isConnected = false;
		this.isConnected = isConnected;
		// FIXME: warning here with typecast
		@SuppressWarnings("unchecked") // Just for this one statement
		List<String> srvlist = new ArrayList(
		Arrays.asList(servers.keySet().toArray()));
		// randomize the server list
		Collections.shuffle(srvlist);
		for (String server : srvlist)
		{
			try
			{
				String[] s = server.split(":");
				String ip = s[0];
				int port = Integer.parseInt(s[1]);
				host = new Socket();
				host.connect(new InetSocketAddress(ip,port),3000);
				//debug("connect(): socket created on port "+host.getLocalPort());
				sockOut = new PrintWriter(host.getOutputStream(),true);
				sockIn = new BufferedReader(
				new InputStreamReader(host.getInputStream()));
				// send identity notification
				sockOut.println("client");
				// receive ACK.
				char[] buffer = new char[64];
				int r = sockIn.read(buffer,0,64);
				if (r != 0) 
				{
					//clientID = new String(buffer).trim();
					isConnected = true;
					debug("Connected to "+getIP(host)+" as "+clientID, YELLOW);
				} else {
					debug("Failed to connect to... "+getIP(host),RED);
				}
			} catch (Exception e) 
			{
				/* debug("connect(): Exception on "+server, RED); */
				servers.put(server,false);
				/* e.printStackTrace(); */
			} 
			if (isConnected) break;
		}
		this.isConnected = isConnected;
		/* try { output = new PrintWriter(this.clientID+".out"); */
		/* } catch (java.io.FileNotFoundException e) { } */
		return isConnected;
	}

	/**
	 * Sends a command to the server, receives a response. 
	 * @param String cmd - command to be sent to the server.
	 * @return String contains the response from the server.
	 */
	public String sendCmd(String cmd)
	{
		String res = "";
		if (isConnected)
		{
			try 
			{
				sockOut.println(cmd);
				debug("sent command ("+getIP(host)+"): "+cmd,GREEN);
				char[] buffer = new char[1024];
				long startTime = System.nanoTime();
				while (res.trim().length() <1 ){
					int r = sockIn.read(buffer,0,buffer.length);
					host.setSoTimeout(3000); // time out
					res = new String(buffer).trim();
					if ((System.nanoTime() - startTime)/1000000 >= 5000){
						throw new java.io.IOException();
					}
				}
				if (res.substring(0,4).equals("fail"))
				{
					System.out.println("["+getTime()+"] "+RED+res+ENDC);
				} else {
					System.out.println("["+getTime()+"] "+BLUE+res+ENDC);
				}
			} catch(Exception e) 
			{
				debug("RECONNECTING...",RED);
				/* servers.put(host.getInetAddress().getHostAddress(), false); */
				return connect() ? sendCmd(cmd) : "SYSTEM IS DOWN.";
			}
		} else { 
			res = "ERROR: No connection."; 
		}
		return res;
	}

	private void runCommands()
	{
		System.out.println("[*] Running commands in config.");
		try {
			for (String cmd: commands){
				sendCmd(cmd);
				/* Thread.sleep(2000); */
			}
		} catch (Exception e){
			e.printStackTrace();
		}
	}

	/**
	 * Main loop of the client program.
	 */
	private void mainLoop()
	{
		/* debug("entering mainLoop()."); */
		Scanner in = new Scanner(System.in);
		String cmd = "";
		prompt();

		// catch keyboardInterrupt
		Runtime.getRuntime().addShutdownHook(new Thread() {
				public void run() {
					terminate();
				}
		 });

		while (!((cmd = in.nextLine()).equals("quit")))
		{
			if (cmd.equals("reconnect")) { connect(); } 
			else if (cmd.equals("test")) { testLoop("b0",0); } 
			else { sendCmd(cmd); }
				prompt();
		}
		terminate();
	}

	/**
	 * Terminates the program.
	 */
	private void terminate()
	{
		debug("Terminating...");
		try
		{
			host.close();
			sockOut.close();
			sockIn.close();
		} catch (IOException e) {
			return;
		}
		return;
	}

	/**
	 * Prints prompt.
	 */
	private void prompt()
	{
		try
		{
			String ip = host.getInetAddress().getLocalHost().getHostAddress();
			System.out.print(ip+":"+host.getLocalPort()+" > ");
		} catch (Exception e){
			e.printStackTrace();
		}
	}

	/**
	 * Takes in a socket object and returns the IP of that socket.
	 * @param Socket sock - desired socket to get the IP for.
	 * @return String ip address of the socket.
	 */
	private String getIP(Socket sock)
	{
		SocketAddress s = sock.getRemoteSocketAddress();
		int port = ((InetSocketAddress) s).getPort();
		return sock.getInetAddress().getHostAddress()+":"+port;
	}

	/**
	 * Used to print debug messages.
	 * @param String msg - debug message to be printed out
	 */
	private void debug(String msg)
	{
		if (debug) System.out.println("["+getTime()+"] DEBUG: "+msg);
	}
	
	/**
	 * Used to print debug messages.
	 * @param String msg - debug message to be printed out.
	 * @param String color - desired color for messages.
	 */
	private void debug(String msg, String color)
	{
		if (debug) System.out.println("["+getTime()+"] "+color+"DEBUG: "+msg+ENDC);
	}

	private void testLoop(String book, long milli) 
	{
		try 
		{
			while (true) 
			{
				for (int i=0;i<10;i++)
				{
					sendCmd(clientID+" b"+i+" reserve");
					Thread.sleep(milli);
					sendCmd(clientID+" b"+i+" return");
					Thread.sleep(milli);
				}
			}
		}catch(Exception e){

		}
	}

	/**
	 * Returns the current time in HH:mm:ss
	 */
	private String getTime()
	{
		DateFormat dateFormat = new SimpleDateFormat("HH:mm:ss:SS");
		Date date = new Date();
		return YELLOW+dateFormat.format(date)+ENDC;
	}

	public static void main(String[] args)
	{
		try
		{
			LibraryClient c1= new LibraryClient(args[0]);
		} catch (ArrayIndexOutOfBoundsException e){
			System.out.println("Error: Please provide a client ID. Example: c1.");
		}
	}
}
