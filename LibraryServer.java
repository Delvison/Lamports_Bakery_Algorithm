import java.util.HashMap;
import java.util.ArrayList;
import java.util.Scanner;
import java.net.Socket;
import java.io.File;
import java.io.IOException;

public class LibraryServer
{
	HashMap<String,Boolean> servers; // < [ip:status], ...>
	ArrayList<Socket> clients;
	String configFile = ".serverConfig.dat";
	boolean debug = true;

	public void LibraryServer()
	{
		servers = new HashMap<String,Boolean>();
		clients = new ArrayList<Socket>();
		initialize();
	}

	/**
	 * Initializes the server by reading from the data file. Returns a boolean
	 * indicating whether or not initialization was successful.
	 **/
	private boolean initialize()
	{
		try {
			File f = new File(configFile);
			Scanner s = new Scanner(f);
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		
	}

	/**
	 * Receives a command, processes it and returns a String.
	 **/
	private String recvCmd(String command) 
	{
		return "command";
	}

	/**
	 * Synchronizes current servers with all others on the system
	 */
	private void synchronize()
	{

	}
}
