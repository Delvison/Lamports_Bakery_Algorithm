import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.Set;
import java.util.Iterator;
import java.util.Collections;
import java.util.Arrays;
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
 * TODO:  - initialize books from the file
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
	private int clientCount; // counter for how many clients connected
	
	// Lamports Mutex Algorithm variables
	Integer[] processes; // process id's to identify servers
	Boolean[] cs_flag; // critical section flag
	Integer[] vector_clock; // clocks of all processes/servers
	private int pid; // process id of the current server
	
	private final String GREEN = "\033[92m";
	private final String RED = "\033[91m";
	private final String ENDC = "\033[0m";
	private final String YELLOW = "\u001B[33m";
	private final String BLUE = "\u001B[34m"; 
	private final String CYAN = "\u001B[36m";

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
		if (initialize()) {
			// TODO: synchronize book data
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
			debug("Local IP -- "+serverSocketCh.socket().getInetAddress().
			getLocalHost().getHostAddress());
			// create selector (NIO)
			selector = Selector.open();
			int interestSet = serverSocketCh.validOps();
			SelectionKey key =serverSocketCh.register(selector, interestSet);
			// read config file
			f = new File(configFile);
			Scanner s = new Scanner(f);
			// n <- read in number of servers available
			int servNum = s.nextInt();
			debug("amount of servers: "+servNum);

			// initialize cs_flag
			cs_flag = new Boolean[servNum];
			for (int i=0;i<cs_flag.length;i++) cs_flag[i] = false;
			
			// initialize vector clock
			vector_clock = new Integer[servNum];
			for (int i=0;i<vector_clock.length;i++) vector_clock[i] = 0;

			// initialize books
			this.bookNum = s.nextInt();
			debug("amount of books: "+bookNum);
			books = new String[bookNum][3];
			for (int i=0;i<bookNum;i++){
				books[i][0] = "b"+i;
				books[i][1] = "free";
			}

			// initialize servers
			for (int i = 0;i<=servNum;i++) {
				String j = s.nextLine();
				if (j.contains(":")) {
					//debug("read server from config: "+j);
					if (Integer.parseInt(j.split(":")[1]) == this.port) {
						// set process id
						this.pid = i-1;
						debug("PID == "+pid,CYAN);
					} else {
						servers.put(j, true);
					}
				}
			}

			// TODO: get sleep duration and sleep trigger

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
				sock.connect(new InetSocketAddress(ip,port));
				// write ACK to socketchannel
				if (send(sock, ""+this.pid)) {
					// add socketchannel to the arraylist
					servers_sock.add(sock);
					// register the socketchannel to the selector (NIO)
					sock.configureBlocking(false);
					SelectionKey key =sock.register(selector,sock.validOps());
					/* debug("Added SocketChannel: "+getIP(sock),GREEN); */
				}
			} catch (Exception e)
			{
				debug("connectToServers(): Error with "+server.getKey(),RED);
				servers.put(server.getKey(),false);
				/* return false; */
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
		/* if(!getIP(sock).equals("127.0.0.1:"+port))  */
		if(true) 
		{
			/* debug("send(): entering with "+getIP(sock)+" MSG: "+msg); */
			try
			{
				final byte[] by = msg.getBytes();
				ByteBuffer b = ByteBuffer.allocate(by.length);
				b.put(by);
				b.flip();
				while(b.hasRemaining()){
					int p = sock.write(b);
				}
				b.clear();
				debug("send():Sent message to "+getIP(sock)+" >>> "+
				msg,GREEN);
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
				debug("send(): Not connected to "+getIP(sock),RED);
				/* e.printStackTrace(); */
				return false;
			}
		} else {
			if (!sock.socket().isConnected()) {
				debug("send(): NOT CONNECTED to "+getIP(sock),RED);
			}
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
			/* debug("connectClient(): entering with "+getIP(clientCh)); */
			try
			{
				ByteBuffer buf = ByteBuffer.allocate(24);
				int bytesRead = clientCh.read(buf);
				buf.flip();
				String recv = new String(buf.array()).trim();

				// client is connecting
				if (recv.equals("client") && send(clientCh, "c"+clientCount))
				{
					debug("connectClient(): client connected "+getIP(clientCh));
					clients_sock.add(clientCh);
					clientCh.configureBlocking(false);
					SelectionKey key =clientCh.register(selector,clientCh.validOps());
					clientCount++;
					broadcast("CLIENT_COUNT "+this.pid+" "+clientCount+" "+
					vector_clock[this.pid]);
					return true;
				} else {
					// server is connecting
					debug("connectClient(): connected to server: "+recv,CYAN);
					servers_sock.add(clientCh);
					clientCh.configureBlocking(false);
					SelectionKey key =clientCh.register(selector,clientCh.validOps());
					return true;
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
		int port = sock.socket().getLocalPort();
		/* debug("recv(): Entering with "+ip,BLUE); */
		String recv = "";
		try
		{
			ByteBuffer buf = ByteBuffer.allocate(1024);
			int bytesRead = sock.read(buf);
			buf.flip();
			recv = new String(buf.array());
			String[] cmd = recv.trim().split(" ");
			/* if (!servers.containsKey(ip)  || cmd[0].equals("reserve") ||  */
			/* 		cmd[0].equals("reserve")) */
			
			if (clients_sock.contains(sock))
			{
				// receiving from a client
				debug("recv(): received "+recv, BLUE);
				if (cmd.length == 3){
					send(sock, processBook(cmd[1],cmd[2],cmd[0],true));
				} else {
					send(sock, "Invalid command.");	
				}

			} else {
				String sock_ip = getIP(sock);
				// receiving from server.
				if (cmd[0].equals("LOCK")) 
				{
					// LOCK is received when a server in the cluster requests the cs_lock
					// LOCK <Pn> <Pn.clock>
					debug("recv(): received LOCK from server "+sock_ip+"> "+recv,CYAN);
					int p = Integer.parseInt(cmd[1]);
					int clock = Integer.parseInt(cmd[2]);
					updateClock(p,clock);
					lock(p);

				} else if (cmd[0].equals("UNLOCK")) 
				{
					// UNLOCK is received when a server in the cluster requests to release
					// the cs_lock. UNLOCK <Pn> <Pn.clock>
					debug("recv(): received UNLOCK from server "+sock_ip+"> "+recv,CYAN);
					int p = Integer.parseInt(cmd[1]);
					int clock = Integer.parseInt(cmd[2]);
					updateClock(p,clock);
					unlock(p);
				
				} else if (cmd[0].equals("SYNC")) {
					// SYNC is received when a server asks for all book data.
					// SYNC <Pn> <Pn.clock>
					debug("recv(): received SYNC from server "+sock_ip,CYAN);

				} else if (cmd[0].equals("COMMAND")) {
					// COMMAND is received from a server that has executed a book command.
					// it is received and should also be executed on the receiving server
					// in the cluster. COMMAND <Pn> <Pn.clock> (reserve|return) (Cx) (By)
					debug("recv(): received COMMAND from server "+sock_ip+"> "+
					recv,CYAN);
					int p = Integer.parseInt(cmd[1]);
					int clock = Integer.parseInt(cmd[2]);
					String order = cmd[3];
					String c_id = cmd[4];
					String b_id = cmd[5];
					updateClock(p,clock);
					processBook(c_id , b_id, order,false);

				} else if (cmd[0].equals("CLIENT_COUNT")) {
					debug("recv(): received CLIENT_COUNT from server "+sock_ip+"> "+
					recv,CYAN);
					int p = Integer.parseInt(cmd[1]);
					int cc = Integer.parseInt(cmd[2]);
					int clock = Integer.parseInt(cmd[3]);
					if (cc >clientCount) clientCount = cc;
					updateClock(p,clock);

				} else {
					debug("recv(): ERROR: received INVALID_COMMAND from server "
					+sock_ip+ " >> "+recv,RED);
				}
			}
		} catch (IOException e)
		{
			debug("recv():IOException.");
			e.printStackTrace();
		}
		return recv;
	}

	/**
	 * Collects the status of the books each seperated by a colon.
	 * @return String Indicates the status of each book.
	 */
	private String bookDataDump()
	{
		String data = "";

		for (int i=0; i<books.length;i++)
		{
			if (books[i][1].equals("free")) 
				data+= books[i][0]+" "+books[i][1]+"null;";
			if (books[i][1].equals("reserved")) 
				data+= books[i][0]+" "+books[i][1]+" "+books[i][2]+";";
		}
		return data;
	}

	/**
	 * Broadcasts a message to all servers in the cluster.
	 * @param String msg - Message to be broadcasted.
	 */
	private void broadcast(String msg)
	{
		debug("broadcast(): MESSAGE = "+msg);
		for (SocketChannel sock: servers_sock)
		{
			send(sock,msg);
		}
	}

	/**
	* Updates the clock value to be the maximum value found in the vector clock 
	* plus 1.
	*/
	private void updateClock(int process, int val)
	{
		if (process == this.pid)
		{
			int max = (int) Collections.max(Arrays.asList(vector_clock));
			vector_clock[process] = max + 1;
		} else {
			if(vector_clock[process] < val) vector_clock[process] = val;
		}
		debug(getClocks(),CYAN);
	}

	/**
	*	Processes commands coming from clients that have to deal with books.
	*	Firstly, sets the lock flag for this process and enters waitForLock() to
	*	wait for it's turn.
	*	@param String clientID - ID of client submitting command.
	*	@param String bookID - ID of book in question.
	*	@param String cmd - command being submitted (reserve or return).
	*	@param boolean needLock - inidicates whether or not the process requires a
	*	lock for the critical section. The difference is updating books from a
	*	client request, which requires a lock, or just synchronizing with other
	*	servers.
	*	@return String - response to command submitted.
	*/
	private String processBook(String clientID, String bookID, String cmd, 
	boolean needLock)
	{
		/* debug("processBook():"+cmd+" "+clientID+" "+bookID); */
		String ret = "fail "+clientID+" "+bookID;
		// ask for lock for the critical section
		if (needLock) lock(this.pid);
		// ensure that this process has the lock for the critical section first.
		if (!needLock || waitForLock())
		{
			for (int i=0;i<bookNum;i++)
			{
				if (books[i][0].equals(bookID))
				{
					if (cmd.equals("reserve") && books[i][1].equals("free")){
						books[i][1] = "reserved";
						books[i][2] = clientID;
						ret = clientID+" "+bookID;
					} else if (cmd.equals("return") && books[i][1].equals("reserved") 
						&& books[i][2].equals(clientID))
					{
						books[i][1] = "free";
						books[i][2] = null;
						ret = "free "+clientID+" "+bookID;
					} else {
						ret = "fail "+clientID+" "+bookID;
					}
				}
			}
			debug("processBook(): "+ret);
			if (needLock && !ret.substring(0,4).equals("fail")) 
				broadcast("COMMAND "+this.pid+" "+vector_clock[this.pid]+
				" "+cmd+" "+clientID+" "+bookID);
			if (needLock) unlock(this.pid);
		}
		return ret;
	}

	/**
	 * Sets lock for a given process.
	 * @param int process - process asking for the mutex lock
	 */
	private void lock(int process)
	{
		debug("lock(): setting lock for process "+process,YELLOW);
		if (this.pid == process) broadcast("LOCK "+this.pid+" "+
			vector_clock[this.pid]);
		cs_flag[process] = true;
	}

	/**
	 * Releases lock for a given process.
	 * @param int process - process asking to release lock.
	 */
	private void unlock(int process)
	{
		debug("unlock(): unlocking process "+process,YELLOW);
		cs_flag[process] = false;
		updateClock(process, vector_clock[process]);
		if (this.pid == process) {
			broadcast("UNLOCK "+this.pid+" "+vector_clock[this.pid]);
		}
	}
	
	/**
	 * Waits until all other processes with a lower clock count execute and set
	 * their cs_flag to false.
	 */
	private boolean waitForLock()
	{
		debug("waitForLock(): entering.",YELLOW);
		boolean lock = true;
		while (lock)
		{
			boolean othersWaiting = false;
			int lowestClock = (int) Collections.min(Arrays.asList(vector_clock));
			int lowestProcessWaiting = this.pid;

			// check that other processes have not asked to access critical section
			for (int i=0; i< cs_flag.length; i++) 
			{
				if (i != pid && cs_flag[i]) {
					othersWaiting = true;
					if (i < pid) lowestProcessWaiting = i;
				}
			}

			if (lowestClock == vector_clock[this.pid] && !othersWaiting ||
					this.pid <= lowestProcessWaiting) 
			{
				// if others are not waiting and this process has lowest clock then the
				// lock for the critical section should be given to this section. 			
				// if another process is asking for the lock yet it has the same clock
				// count, then the one with the lowest process id takes precedence.
				lock = false;
		  } else {
				// Else, check sockets for updates.
				checkSockets();
			}
		}
		debug("waitForLock(): lock received.",YELLOW);
		return true;
	}

	private  void mainLoop()
	{
		// catch keyboardInterrupt
		Runtime.getRuntime().addShutdownHook(new Thread() {
				public void run() {
					terminate();
				}
		 });

		/* debug("Entering mainLoop()"); */
		while (true)
		{
			checkSockets();
		}
	}

	/**
	 * Main loop of the server program.
	 */
	private void checkSockets()
	{
		try 
		{
			String cmd = "";
			SocketChannel sc = null;
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
					sc = ssc.accept();
					/* debug("checkSockets(): accepted connection "+getIP(sc)); */
					connectClient(sc);
				} else if (key.isConnectable()) 
				{
					// a connection was established with a remote server.
					sc = (SocketChannel)key.channel();

				} else if (key.isReadable()) 
				{
					// a channel is ready for reading
					sc = (SocketChannel)key.channel();
					/* debug("checkSockets(): receiving."+getIP(sc)); */
					// receive data on SocketChannel
					recv(sc);
				} else if (key.isWritable()) 
				{
					// a channel is ready for writing
					sc = (SocketChannel)key.channel();
				}

				// remove key from selected keys after processing.
				keyIterator.remove();
			}
		} catch (Exception e) 
		{
			debug("mainLoop(): Exception ");
			e.printStackTrace();
			/* terminate(); */
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
			serverSocketCh.close();
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
			System.out.println("Program terminated.");
			return;
		} catch (IOException e) {
			debug("terminate():IOException.");
			return;
		}
	}

	/**
	 * Returns the IP address and port of of a given SocketChannel.
	 * @param SocketChannel sock - the desired SocketChannel to the ip address for
	 * @return String IP of the SocketChannel given.
	 */
	private String getIP(SocketChannel sock)
	{
		Socket socket = sock.socket();
		String ip = sock.socket().getInetAddress().getHostAddress();
		int port = socket.getPort();
		/* debug("getIP(): port = "+port); */
		/* debug("getIP(): Local port = "+socket.getLocalPort()); */
		/* if (!servers.containsKey(ip+":"+socket.getLocalPort())) */
		/* { */
		/* 	port = socket.getPort(); */
		/* } else { */
		/* 	port = socket.getLocalPort(); */
		/* } */
		return ip+":"+port;
	}


	/**
	 * Returns the vector_clock in string form.
	 * @return String - vector_clock in String form.
	 */
	private String getClocks()
	{
		String ret = "Clocks: [  ";
		for(int i=0;i<vector_clock.length;i++)
			ret += vector_clock[i]+"  ";
		return ret+"]";
	}

	/**
	 * Used to print debug messages.
	 * @param String msg - debug message to be printed out
	 */
	private void debug(String msg)
	{
		if (debug) System.out.println("[*] DEBUG: "+msg);
	}
	
	private void debug(String msg, String color)
	{
		if (debug) System.out.println(color+"[*] DEBUG: "+msg+ENDC);
	}



	public static void main(String[] args)
	{
		LibraryServer s1= new LibraryServer(Integer.parseInt(args[0]));
	}
}
