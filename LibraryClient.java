import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ArrayList;
import java.util.Scanner;
import java.net.Socket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.io.File;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

/**
 * Client program to be used with a distributed system modeled in
 * LibraryServer.java. Requires a special configuration file where the first
 * line indicates how many servers there are and the following lines contain the
 * addresses of the servers with their respective port (ex: 127.0.0.1:1234).
 *
 * Developed as part of a class assignment (CSE535 -- SUNY Korea)
 *
 * @author Delvison Castillo (delvison.castillo@sunykorea.ac.kr)
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
	private HashMap<String,Boolean> servers; // < [ip:status], ...>

	/**
	 * Constructor method. Initializes the hashmap used to hold the addresses of
	 * the servers as well as their status. Calls initialize. If initialize is
	 * successful, then the program enters its main loop.
	 */
	public void LibraryClient()
	{
		servers = new HashMap<String,Boolean>();
		if (initialize()) {
			mainLoop();
		} else {
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
			f = new File(configFile);
			Scanner s = new Scanner(f);
			// n <- read in number of servers available
			int servNum = s.nextInt();
			// add servers to hashmap of servers
			for (int i = 0;i<servNum;i++) servers.put(s.nextLine(), true);
			// connect to a random server
		  return connect() ? true : false;
		} catch (IOException e) 
		{
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * Updates the socket with a server that is live. 
	 * @return boolean indicating whether or not the program was able to connect 
	 * to a server.
	 * TODO: Randomize the server the client connects to.
	 */
	private boolean connect()
	{
		boolean isConnected = false;
		for (Map.Entry<String,Boolean> server : servers.entrySet())
		{
			// if the server is up, attempt to connect
			if (server.getValue() != false)
			{
				try
				{
					String[] s = server.getKey().split(":");
					String ip = s[0];
					int port = Integer.parseInt(s[1]);
					host = new Socket(ip,port);
					sockOut = new PrintWriter(host.getOutputStream(),true);
					sockIn = new BufferedReader(
					new InputStreamReader(host.getInputStream()));
					// receive ACK.
					if (sockIn.readLine() != null) isConnected = true;
				} catch (UnknownHostException e) 
				{
					isConnected = false;
					servers.put(server.getKey(),false);
					e.printStackTrace();
				} catch (IOException e)
				{
					isConnected = false;
					servers.put(server.getKey(),false);
					e.printStackTrace();
				}
			}
			if (isConnected) break;
		}
		this.isConnected = isConnected;
		return isConnected;
	}

	/**
	 * Sends a command to the server. 
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
				res = sockIn.readLine();
			} catch(IOException e) 
			{
				e.printStackTrace();
				servers.put(host.getInetAddress().getHostAddress(), false);
				return connect() ? sendCmd(cmd) : "SYSTEM IS DOWN.";
			}
		} else { res = "ERROR: No connection."; }
		return res;
	}

	/**
	 * Main loop of the client program.
	 */
	private void mainLoop()
	{
		Scanner in = new Scanner(System.in);
		String cmd = "";
		while (!((cmd = in.next()).equals("quit")))
		{
			prompt();
			sendCmd(cmd);
		}
	}

	/**
	 * Terminates the program.
	 */
	public void terminate()
	{
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
		System.out.print(" > ");
	}

	/**
	 * Used to print debug messages.
	 * @param String msg - debug message to be printed out
	 */
	private void debug(String msg)
	{
		if (debug) System.out.println("[*] DEBUG: "+msg);
	}
}
