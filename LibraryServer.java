import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.Set;
import java.util.Iterator;
import java.net.Socket;
import java.nio.channels.SocketChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.Selector;
import java.nio.channels.SelectionKey;
import java.nio.channels.ClosedChannelException;
import java.nio.ByteBuffer;
import java.net.ServerSocket;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.io.File;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

/**
 * Server program meant to operate within a distributed system. Uses Lamport's
 * Mutual Exclusion Algorithm. Distributed system's function is replication.
 * Models a library system. Uses a configuration file 
 *
 * Developed as part of a class assignment (CSE535 -- SUNY Korea)
 *
 * @author Delvison Castillo (delvison.castillo@sunykorea.ac.kr)
 */
public class LibraryServer

{
	private int port = 3000;
	private boolean debug = true; // debug flag
	private HashMap<String,Boolean> servers; // < [addr:status], ...>
	private ArrayList<SocketChannel> clients_sock; // client SocketChannels
	private ArrayList<SocketChannel> servers_sock; // server SocketChannels
	private String configFile = ".serverConfig.dat"; // configuration file
	private ServerSocketChannel serverSocketCh;
	private File f; // configuration file object
	private int clock; // lamport timestamp
	private Selector selector; // for asynchronous I/O

	/**
	 * Constructor method. Initializes servers and clients data structures, calls
	 * initialize method, enters the programs mainloop in initialize is
	 * successful.
	 */
	public void LibraryServer()
	{
		clock = 0;
		servers = new HashMap<String,Boolean>();
		clients_sock = new ArrayList<SocketChannel>();
		servers_sock = new ArrayList<SocketChannel>();
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
			serverSocketCh = ServerSocketChannel.open();
			serverSocketCh.socket().bind(new InetSocketAddress(port));
			// create selector
			Selector selector = Selector.open();
			SelectionKey key =serverSocketCh.register(
			selector, (SelectionKey.OP_READ | SelectionKey.OP_WRITE 
			| SelectionKey.OP_CONNECT | SelectionKey.OP_ACCEPT));
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
				SocketChannel sock = SocketChannel.open();
				sock.configureBlocking(false);
				sock.connect(new InetSocketAddress(ip,port));
				// write ack to socketchannel
				if (!send(sock, "ACK")) servers.put(server.getKey(), false);
				servers_sock.add(sock);
				SelectionKey key =sock.register(
				selector, (SelectionKey.OP_READ | SelectionKey.OP_WRITE
				| SelectionKey.OP_CONNECT | SelectionKey.OP_ACCEPT));
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
	 * Sends a message on the given SocketChannel.
	 * @param SocketChannel sock - desired socketchannel to send message out on.
	 * @param String msg - desired message to be sent.
	 * @return Boolean indicating whether the sent message was successful.
	 */
	private boolean send(SocketChannel sock,String msg) 
	{
		try
		{
			final byte[] by = msg.getBytes();
			ByteBuffer b = ByteBuffer.allocate(by.length);
			b.put(by);
			b.flip();
			while(b.hasRemaining()){
				sock.write(b);
			}
			return true;
		} catch (IOException e)
		{
			e.printStackTrace();
			String ip = sock.socket().getInetAddress().getHostAddress();
			if (servers.containsKey(ip)) servers.put(ip,false);
			return false;
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
				if (send(clientCh, "ACK"))
				{
					clients_sock.add(clientCh);
					SelectionKey key =clientCh.register(
					selector, (SelectionKey.OP_READ | SelectionKey.OP_WRITE
					| SelectionKey.OP_CONNECT | SelectionKey.OP_ACCEPT));
					return true;
				} else {
					return false;
				}
			} catch (ClosedChannelException e){
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
		String recv = "";
		String ip = sock.socket().getInetAddress().getHostAddress();
		try
		{
			if (!servers.containsKey(ip))
			{
				// receiving from a client
				ByteBuffer buf = ByteBuffer.allocate(256);
				int bytesRead = sock.read(buf);
				buf.flip();
				recv = new String(buf.array());
			} else{
				// TODO: receiving from another server
			}
		} catch (IOException e)
		{
			e.printStackTrace();
		}
		return recv;
	}

	/**
	 * Synchronizes current servers with all others on the system.
	 * @return Boolean indicating whether or not the synchronization was
	 * successful.
	 */
	private void synchronize()
	{
		// TODO: Write me!

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
		try 
		{
			// SelectionKey key = channel.register(selector, SelectionKey.OP_READ);
			Set<SelectionKey> selectedKeys = selector.selectedKeys();
			Iterator<SelectionKey> keyIterator = selectedKeys.iterator();
			String cmd = "";
			SocketChannel sc = null;
			while (true)
			{
				SelectionKey key = keyIterator.next();

				if(key.isAcceptable()) 
				{
					// a connection was accepted by a ServerSocketChannel.
					sc = (SocketChannel)key.channel();
				} else if (key.isConnectable()) 
				{
					// a connection was established with a remote server.
					sc = (SocketChannel)key.channel();

				} else if (key.isReadable()) 
				{
					// a channel is ready for reading
					sc = (SocketChannel)key.channel();
					recv(sc);

				} else if (key.isWritable()) 
				{
					// a channel is ready for writing
					sc = (SocketChannel)key.channel();
				}

				keyIterator.remove();
			}
		} catch (Exception e) 
		{
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
			for (SocketChannel sock : servers_sock){
				sock.close();
			}
			for (SocketChannel sock : clients_sock){
				sock.close();
			}
			serverSocketCh.close();
		} catch (IOException e) {
			System.exit(0);
		}
		System.exit(0);
	}
}
