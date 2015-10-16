import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ArrayList;
import java.util.Scanner;
import java.net.Socket;
import java.nio.channels.SocketChannel;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.io.File;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

public class LibraryServer
{
	private int port = 3000;
	private boolean debug = true; // debug flag
	private HashMap<String,Boolean> servers; // < [socket:status], ...>
	private ArrayList<Socket> clients_sock; // clients connected to the server
	private ArrayList<Socket> servers_sock; // socket connections to other servers
	private String configFile = ".serverConfig.dat"; // configuration file
	private ServerSocket serverSocket;
	private File f; // configuration file object
	private int clock;

	/**
	 * Constructor method. Initializes servers and clients data structures, calls
	 * initialize method, enters the programs mainloop in initialize is
	 * successful.
	 */
	public void LibraryServer()
	{
		clock = 0;
		servers = new HashMap<String,Boolean>();
		clients_sock = new ArrayList<Socket>();
		servers_sock = new ArrayList<Socket>();
		if (initialize()) {
			mainLoop();
		} else {
			terminate();
		}
	}

	/**
	 * Initializes the server by reading from the data file. 
	 * @return Boolean indicating whether or not initialization was successful.
	 */
	private boolean initialize()
	{
		try 
		{
			// create socket
			serverSocket = new ServerSocket(port);
			f = new File(configFile);
			Scanner s = new Scanner(f);
			// n <- read in number of servers available
			int servNum = s.nextInt();
			// add servers to hashmap of servers
			for (int i = 0;i<servNum;i++) servers.put(s.nextLine(), true);
			// connect to servers
		  return connectToServers() ? true : false;
		} catch (IOException e) 
		{
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * Creates socket connections to all other servers in the distributed system.
	 * @return Boolean indicating success.
	 */
	private boolean connectToServers()
	{
		for (Map.Entry<String,Boolean> server : servers.entrySet())
		{
			try
			{
				String[] s = server.getKey().split(":");
				String ip = s[0];
				int port = Integer.parseInt(s[1]);
				Socket sock = new Socket(ip,port);
				PrintWriter sockOut = new PrintWriter(sock.getOutputStream(),true);
				BufferedReader sockIn = new BufferedReader(
				new InputStreamReader(sock.getInputStream()));
				// send ACK to server. await reply.
				sockOut.println("ACK");
				// update the server's status if no reply
				if (sockIn.readLine() == null) servers.put(server.getKey(), false);
				// add socket to servers_sock arraylist
				servers_sock.add(sock);
			} catch (UnknownHostException e) 
			{
				servers.put(server.getKey(),false);
				e.printStackTrace();
				return false;
			} catch (IOException e)
			{
				servers.put(server.getKey(),false);
				e.printStackTrace();
				return false;
			}
		}
			return true;
	}

	/**
	 * Receives a connection from a client. Sends client an ACK. 
	 * Adds client's socket to the client data structure.
	 * @param Socket c_sock - The newly created socket for the connecting client
	 * produced from ServerSocket.accept().
	 * @return Boolean indicating whether or not the client was successfully
	 * added.
	 */
	private boolean connectClient(Socket c_sock)
	{
		try
		{
			PrintWriter sockOut = new PrintWriter(c_sock.getOutputStream(),true);
			sockOut.println("ACK");
			clients_sock.add(c_sock);
			return true;
		} catch (IOException e) 
		{
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * Receives a command on the socket from a client.
	 * @return String containing the reply to be sent to the client.
	 */
	private String recvCmd(String command) 
	{
		return "command";
	}

	/**
	 * Synchronizes current servers with all others on the system.
	 * @return Boolean indicating whether or not the synchronization was
	 * successful.
	 */
	private void synchronize()
	{

	}

	/**
	 * Updates the configuration file.
	 * @return Boolean indicating whether or not update was successful.
	 */
	private boolean update()
	{
		return true;
	}

	/**
	 * Fetches data from the configuration file.
	 * @return String containing the desired data.
	 */
	private String fetch()
	{
		return "result";
	}

	/**
	 * Main loop of the server program.
	 */
	private void mainLoop()
	{
		Scanner in = new Scanner(System.in);
		String cmd = "";
		while (true)
		{
			// TODO: Asynchronously check all sockets for messages.
		}
		// TODO: Detect keyboardinterupt and terminate.
	}

	/**
	 * Terminates the program.
	 */
	public void terminate()
	{
		try
		{
			for (Socket sock : servers_sock){
				sock.close();
			}
			for (Socket sock : clients_sock){
				sock.close();
			}
			serverSocket.close();
		} catch (IOException e) {
			System.exit(0);
		}
		System.exit(0);
	}
}
