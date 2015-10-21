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
 * TODO:  - Figure out clientid.
 *				- Test client with atleast one instance of server.
 */

public class LibraryClient
{
	private File f; // file object for config file 
	private Socket host; // host that the client is connected to
	private boolean debug = true; // debug flag
	private boolean isConnected; // indicator of connection status
	private PrintWriter sockOut; // for writing out on the socket
	private BufferedReader sockIn; // for reading in from the socket
	private String configFile = ".clientConfig.dat"; // config file location
	private HashMap<String,Boolean> servers; // < [addr:status], ...>
	private String clientID; // clientID given by server
	private final String GREEN = "\033[92m";
	private final String RED = "\033[91m";
	private final String ENDC = "\033[0m";
	private final String YELLOW = "\u001B[33m";
	private final String BLUE = "\u001B[34m"; 

	/**
	 * Constructor method. Initializes the hashmap used to hold the addresses of
	 * the servers as well as their status. Calls initialize. If initialize is
	 * successful, then the program enters its main loop.
	 */
	public LibraryClient()
	{
		servers = new HashMap<String,Boolean>();
		if (initialize()) {
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
			// connect to a random server
		  return connect() ? true : false;
		} catch (IOException e) 
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
		List<String> srvlist = new ArrayList(
		Arrays.asList(servers.keySet().toArray()));
		// randomize the server list
		Collections.shuffle(srvlist);
		for (String server : srvlist)
		{
			// if the server is up, attempt to connect
			/* if (servers.get(server) != false) */
			if (true) // always assume that a server is up
			{
				try
				{
					String[] s = server.split(":");
					String ip = s[0];
					int port = Integer.parseInt(s[1]);
					host = new Socket(ip,port);
					//debug("connect(): socket created on port "+host.getLocalPort());
					sockOut = new PrintWriter(host.getOutputStream(),true);
					sockIn = new BufferedReader(
					new InputStreamReader(host.getInputStream()));
					// send identity notification
					sockOut.println("client");
					// receive ACK.
					//debug("connect(): attempting to connect to "+ip+":"+port);
					char[] buffer = new char[64];
					int r = sockIn.read(buffer,0,64);
					if (r != 0) 
					{
						clientID = new String(buffer);
						isConnected = true;
						debug("Connected to "+getIP(host)+" as "+clientID, GREEN);
					} else {
						System.out.println("Failed to connect to... "+getIP(host));
					}
				} catch (Exception e) 
				{
					debug("connect(): Exception on "+server, RED);
					servers.put(server,false);
					/* e.printStackTrace(); */
				} 
			}
			if (isConnected) break;
		}
		this.isConnected = isConnected;
		return isConnected;
	}

	/**
	 * Sends a command to the server, receives a response. 
	 * @param String cmd - command to be sent to the server.
	 * @return String containing the response from the server.
	 */
	public String sendCmd(String cmd)
	{
		String res = "";
		if (isConnected)
		{
			try 
			{
				sockOut.println(cmd);
				debug("sent command: "+cmd,GREEN);
				char[] buffer = new char[64];
				int r = sockIn.read(buffer,0,buffer.length);
				res = new String(buffer);
				debug("received :"+res,BLUE);
			} catch(IOException e) 
			{
				e.printStackTrace();
				/* servers.put(host.getInetAddress().getHostAddress(), false); */
				return connect() ? sendCmd(cmd) : "SYSTEM IS DOWN.";
			}
		} else { 
			res = "ERROR: No connection."; 
		}
		return res;
	}

	/**
	 * Main loop of the client program.
	 */
	private void mainLoop()
	{
		debug("entering mainLoop().");
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
			System.exit(0);
		}
		System.exit(0);
	}

	/**
	 * Prints prompt.
	 */
	private void prompt()
	{
		try{
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
		if (this.debug) System.out.println("[*] DEBUG: "+msg);
	}
	
	private void debug(String msg, String color)
	{
		if (debug) System.out.println(color+"[*] DEBUG: "+msg+ENDC);
	}

	public static void main(String[] args)
	{
		LibraryClient c1= new LibraryClient();
	}
}
