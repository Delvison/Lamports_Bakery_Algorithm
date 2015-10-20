import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.Set;
import java.util.Iterator;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.Selector;
import java.nio.channels.SelectionKey;
import java.nio.channels.ClosedChannelException;
import java.nio.ByteBuffer;
import java.io.File;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

/**
 * FILE: LibraryServer.java
 *
 * Server program meant to operate within a distributed system. Uses Lamport's
 * Mutual Exclusion Algorithm. Distributed system's function is replication.
 * Models a library system. Uses a configuration file where the first line has 2
 * space seperated values n and z. n = number of servers. z = number of books in
 * the library. The id's of the book are preceded with a b followed by an
 * integer value. The next n number of lines contain the addresses to the
 * servers in the distributed system followed by a port number such as
 * 127.0.0.1:1234. The rest of the file will have 3 values per each line. The
 * first value will have the server id. The 2nd value is a non-negative integer
 * that tells the server to become unresponsive after receiving the kth message.
 * The 3rd value contains a duration in milliseconds for which the given server
 * would become unresponsive - to all clients and servers.
 *
 * Developed as part of a class assignment (CSE535 -- SUNY Korea)
 *
 * @author Delvison Castillo (delvison.castillo@sunykorea.ac.kr)
 *
 * TODO:  - set up lamports mutex algorithm
 *				- set up system of ACKs in between servers when they connect.
 *				- set up synchronize message between servers
 *				- test with atleast one client
 *				- initialize books from the file
 *				- implement timeout feature. extract own timeout value from configFile
 */
public class LibraryServer

{
	private int port; // port number for the server to listen to
	private boolean debug = true; // debug flag
	private HashMap<String,Boolean> servers; // < [addr:status], ...>
	private ArrayList<SocketChannel> clients_sock; // client SocketChannels
	private ArrayList<SocketChannel> servers_sock; // server SocketChannels
	private String configFile = ".serverConfig.dat"; // configuration file
	private ServerSocketChannel serverSocketCh;
	private File f; // configuration file object
	private int clock; // lamport timestamp
	private Selector selector; // for asynchronous I/O
	private String[][] books; // data structure for books
	private int bookNum; // number of books in the library
	private int timeout; // time in milliseconds to become unresponsive
	private int clientCount;

	/**
	 * Constructor method. Initializes servers and clients data structures, calls
	 * initialize method, enters the programs mainloop in initialize is
	 * successful.
	 */
	public LibraryServer(int port)
	{
		this.port = port;
		clientCount = 0;
		clock = 0;
		servers = new HashMap<String,Boolean>();
		clients_sock = new ArrayList<SocketChannel>();
		servers_sock = new ArrayList<SocketChannel>();
		if (initialize() && synchronize()) {
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
			System.out.println("Initializing...127.0.0.1:"+port);
			// create socket
			serverSocketCh = ServerSocketChannel.open();
			serverSocketCh.bind(new InetSocketAddress(port));
			serverSocketCh.configureBlocking(false);
			// create selector (NIO)
			selector = Selector.open();
			/* int interestSet = (SelectionKey.OP_READ | SelectionKey.OP_WRITE  */
			/* | SelectionKey.OP_CONNECT | SelectionKey.OP_ACCEPT); */
			int interestSet = serverSocketCh.validOps();
			SelectionKey key =serverSocketCh.register(selector, interestSet);
			// read config file
			f = new File(configFile);
			Scanner s = new Scanner(f);
			// n <- read in number of servers available
			int servNum = s.nextInt();
			debug("amount of servers: "+servNum);
			this.bookNum = s.nextInt();
			debug("amount of books: "+bookNum);
			books = new String[bookNum][3];
			// populate books
			for (int i=0;i<bookNum;i++){
				books[i][0] = "b"+i;
				books[i][1] = "free";
			}
			// add servers to hashmap of servers
			for (int i = 0;i<servNum;i++) {
				String j = s.nextLine();
				if (j.contains(":")) {
					debug("read server from config: "+j);
					servers.put(j, true);
				}
			}
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
				SocketChannel sock = SocketChannel.open();
				sock.configureBlocking(false);
				sock.connect(new InetSocketAddress(ip,port));
				// write ACK to socketchannel
				if (!send(sock, "ACK")) servers.put(server.getKey(), false);
				// add socketchannel to the arraylist
				servers_sock.add(sock);
				// register the socketchannel to the selector (NIO)
				SelectionKey key =sock.register(selector,sock.validOps());
				/* selector, (SelectionKey.OP_READ | SelectionKey.OP_WRITE */
				/* | SelectionKey.OP_CONNECT | SelectionKey.OP_ACCEPT)); */
				debug("Added SocketChannel: "+getIP(sock));
			} catch (UnknownHostException e) 
			{
				debug("connectToServers():UnknownHostException "+server.getKey());
				servers.put(server.getKey(),false);
				e.printStackTrace();
				return false;
			} catch (IOException e)
			{
				debug("connectToServers():IOException.");
				servers.put(server.getKey(),false);
				e.printStackTrace();
				return false;
			}
		}
			return true;
	}

	/**
	 * Sends a message on the given SocketChannel.
	 * @param SocketChannel sock - desired socketchannel to send message out on.
	 * @param String msg - desired message to be sent.
	 * @return Boolean indicating whether the sent message was successful.
	 */
	private boolean send(SocketChannel sock,String msg) 
	{
		if(!getIP(sock).equals("127.0.0.1:"+serverSocketCh.socket().getLocalPort())) 
		{
			try
			{
				final byte[] by = msg.getBytes();
				ByteBuffer b = ByteBuffer.allocate(by.length);
				b.put(by);
				b.flip();
				while(b.hasRemaining()){
					int p = sock.write(b);
					debug("send():sent bytes: "+p);
				}
				debug("send():Sent message to SocketChannel: "+getIP(sock)+": "+msg);
				return true;
			} catch (IOException e)
			{
				debug("send():IOException sending to "+getIP(sock));
				/* e.printStackTrace(); */
				String ip = sock.socket().getInetAddress().getHostAddress();
				if (servers.containsKey(ip)) servers.put(ip,false);
				return false;
			} catch (java.nio.channels.NotYetConnectedException e)
			{
				debug("send():Not yet connected to "+getIP(sock));
				/* e.printStackTrace(); */
				return false;
			}
		} else {
			return true;
		}
	}

	/**
	 * Receives a connection from a client. Sends client an ACK. 
	 * Adds client's socket to the client data structure.
	 * @param Socket c_sock - The newly created socket for the connecting client
	 * produced from ServerSocket.accept().
	 * @return Boolean indicating whether or not the client was successfully
	 * added.
	 */
	private boolean connectClient(SocketChannel clientCh)
	{
			try
			{
				if (send(clientCh, "c"+clientCount))
				{
					clients_sock.add(clientCh);
					clientCh.configureBlocking(false);
					SelectionKey key =clientCh.register(selector,clientCh.validOps());
					clientCount++;
					return true;
				} else {
					return false;
				}
			} catch (ClosedChannelException e){
				debug("connectClient():ClosedChannelException.");
				e.printStackTrace();
				return false;
			} catch (IOException e){
				debug("connectClient():ClosedChannelException.");
				e.printStackTrace();
				return false;
			}
	}

	/**
	 * Receives a command on the socket from a client.
	 * @param SocketChannel sock - SocketChannel receiving from.
	 * @return String containing the reply to be sent to the client.
	 */
	private String recv(SocketChannel sock) 
	{
		String ip = getIP(sock);
		debug("recv(): Entering with "+ip);
		String recv = "";
		try
		{
			if (!servers.containsKey(ip))
			{
				// receiving from a client
				ByteBuffer buf = ByteBuffer.allocate(256);
				int bytesRead = sock.read(buf);
				buf.flip();
				recv = new String(buf.array());
				String[] cmd = recv.trim().split(" ");
				debug("recv(): received "+recv);
				if (cmd.length == 3){
					send(sock, processBook(cmd[1],cmd[2],cmd[0]));
				} else {
					send(sock, "Invalid command.");	
				}
			} else{
				// TODO: receiving from server.
				// TODO: check for connect ACK. if so, open socket and change status.
			}
		} catch (IOException e)
		{
			debug("recv():IOException.");
			e.printStackTrace();
		}
		debug("recv(): Exiting with "+ip);
		return recv;
	}

	/**
	 * Synchronizes current servers with all others on the system.
	 * @return Boolean indicating whether or not the synchronization was
	 * successful.
	 */
	private boolean synchronize()
	{
		// TODO: Write me!
		return true;
	}

	/**
	 * Updates the local configuration file.
	 * @return Boolean indicating whether or not update was successful.
	 */
	private boolean update()
	{
		// TODO: Write me!
		return true;
	}

	private String processBook(String clientID, String bookID, String cmd)
	{
		debug("processBook():"+cmd+" "+clientID+" "+bookID);
		for (int i=0;i<bookNum;i++)
		{
			if (books[i][0].equals(bookID))
			{
				debug("processBook(): book found");
				if (cmd.equals("reserve") && books[i][1].equals("free")){
					books[i][1] = "reserved";
					books[i][2] = clientID;
					return clientID+" "+bookID;
				}

				if (cmd.equals("return") && books[i][1].equals("reserved") 
					&& books[i][2].equals(clientID)){
					books[i][1] = "free";
					books[i][2] = null;
					return "free "+clientID+" "+bookID;
				}
			}
		}
		return "fail "+clientID+" "+bookID;
	}

	/**
	 * Fetches data from the configuration file.
	 * @return String containing the desired data.
	 */
	private String fetch()
	{
		// TODO: Write me!
		return "result";
	}

	/**
	 * Main loop of the server program.
	 */
	private void mainLoop()
	{
		debug("Entering mainLoop()");
		try 
		{
			String cmd = "";
			SocketChannel sc = null;
			while (true)
			{
				selector.select();
				Set<SelectionKey> selectedKeys = selector.selectedKeys();
				Iterator<SelectionKey> keyIterator = selectedKeys.iterator();
				while (keyIterator.hasNext())
				{
					SelectionKey key = keyIterator.next();

					if(key.isAcceptable()) 
					{
						// a connection was accepted by a ServerSocketChannel.
						ServerSocketChannel ssc = (ServerSocketChannel)key.channel();
						/* debug("Socketchannel is acceptable: "); */
						connectClient(ssc.accept());
					} else if (key.isConnectable()) 
					{
						// a connection was established with a remote server.
						sc = (SocketChannel)key.channel();

					} else if (key.isReadable()) 
					{
						// a channel is ready for reading
						sc = (SocketChannel)key.channel();
						debug("Socketchannel is readable: "+getIP(sc));
						// receive data on SocketChannel
						recv(sc);
					} else if (key.isWritable()) 
					{
						// a channel is ready for writing
						sc = (SocketChannel)key.channel();
						/* debug("Socketchannel is writable: "+getIP(sc)); */
						// write data on SocketChannel
					}

					// remove key from selected keys after processing.
					keyIterator.remove();
				}
			}
		} catch (Exception e) 
		{
			debug("mainLoop(): Exception ");
			e.printStackTrace();
			terminate();
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
			// close all server sockets
			for (SocketChannel sock : servers_sock){
				System.out.println("Closing server connection to..."+getIP(sock));
				sock.close();
			}
			// close all client sockets 
			for (SocketChannel sock : clients_sock){
				System.out.println("Closing client connection to..."+getIP(sock));
				sock.close();
			}
			// close listening socket
			serverSocketCh.close();
			System.out.println("Program terminated.");
		} catch (IOException e) {
			debug("terminate():IOException.");
			System.exit(0);
		}
		System.exit(0);
	}

	/**
	 * Returns the IP address and port of of a given SocketChannel.
	 * @param SocketChannel sock - the desired SocketChannel to the ip address for
	 * @return String IP of the SocketChannel given.
	 */
	private String getIP(SocketChannel sock)
	{
		Socket socket = sock.socket();
		/* SocketAddress s = socket.getRemoteSocketAddress(); */
		/* int port = ((InetSocketAddress) s).getPort(); */
		int port = socket.getPort();
		return sock.socket().getInetAddress().getHostAddress()+":"+port;
	}

	/**
	 * Used to print debug messages.
	 * @param String msg - debug message to be printed out
	 */
	private void debug(String msg)
	{
		if (debug) System.out.println("[*] DEBUG: "+msg);
	}



	public static void main(String[] args)
	{
		LibraryServer s1= new LibraryServer(3001);
	}
}

