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
 * @author Delvison Castillo (delvison.castillo@sunykorea.ac.kr)
 **/

public class LibraryClient
{
	File f; // file object for config file 
	Socket host; // host that the client is connected to
	boolean debug = true; // debug flag
	PrintWriter sockOut; // for writing out on the socket
	BufferedReader sockIn; // for reading in from the socket
	String configFile = ".clientConfig.dat"; // config file location
	HashMap<String,Boolean> servers; // < [ip:status], ...>

	public void LibraryClient()
	{
		servers = new HashMap<String,Boolean>();
		if (initialize()){
			mainLoop();
		} else {
			terminate();
		}
	}

	/**
	 * Initializes the program. Returns a boolean indicating whether or not the
	 * program successfully initialized.
	 **/
	public boolean initialize()
	{
		try {
			f = new File(configFile);
			Scanner s = new Scanner(f);
			// n <- read in number of servers available
			int servNum = s.nextInt();
			// add servers to hashmap of servers
			for (int i = 0;i<servNum;i++) servers.put(s.nextLine(), true);
			// connect to a random server
		  return connect() ? true : false;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * Updates the socket with a server that is live. Returns true if
	 * there is a server in the hashmap that is still not confirmed to be down.
	 **/
	public boolean connect()
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
					sockOut.println("ACK");
					if (sockIn.readLine() != null) isConnected = true;
				} catch (UnknownHostException e) {
					isConnected = false;
					servers.put(server.getKey(),false);
					e.printStackTrace();
				} catch (IOException e){
					isConnected = false;
					servers.put(server.getKey(),false);
					e.printStackTrace();
				}
			}
			if (isConnected) break;
		}
			return isConnected;
	}

	/**
	 * Sends a command to the server. Returns a string (response from the server).
	 **/
	public String sendCmd(String cmd)
	{
		String res = "";
		try {
			sockOut.println(cmd);
			res = sockIn.readLine();
		} catch(IOException e) {
			e.printStackTrace();
			servers.put(host.getInetAddress().getHostAddress(), false);
		  return connect() ? sendCmd(cmd) : "SYSTEM IS DOWN.";
		}
		return res;
	}

	/**
	 * Main loop of the client program.
	 **/
	public void mainLoop()
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
	 **/
	public void terminate()
	{
		try{
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
	 **/
	public void prompt()
	{
		System.out.print(" > ");
	}

	/**
	 * Used to print debug messages.
	 **/
	public void debug(String msg)
	{
		if (debug) System.out.println("[*] DEBUG: "+msg);
	}
}
