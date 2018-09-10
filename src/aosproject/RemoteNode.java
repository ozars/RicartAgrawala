package aosproject;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.BlockingQueue;

public class RemoteNode implements Closeable {
	public RemoteNode(int pNodeId, InetAddress pInetAddress)
	{
		inetAddress = pInetAddress;
		nodeId = pNodeId;
		connected = false;
	}
	
	private InetAddress inetAddress;
	private int nodeId;
	private Socket socket;
	private ObjectOutputStream output;
	private ObjectInputStream input;
	private BlockingQueue<Event> eventQueue;
	private boolean connected;
	private final static int connectionRetryDelay = 500;
	private boolean grantedPermission;
	private boolean signaledDone;
	private Thread listenerThread;
	private boolean exiting;
	private int lastReplyTimestamp;
	
	public void connect(int tcpPort) {
		if(connected) {
			throw new RuntimeException("Cannot set socket of a remote node more than once.");
		}
		
		while(!connected)
		{
			Log("Trying to connect...");
			try {
				socket = new Socket(inetAddress, tcpPort);
				connected = true;
			} catch (SocketException e) {
				try {
					Thread.sleep(connectionRetryDelay);
				} catch (InterruptedException e1) {
					throw new RuntimeException("Interrupted connection retry.", e1);
				}
			} catch (IOException e) {
				throw new RuntimeException("Couldn't connect to remote node.", e);
			}
		}
		Log("Connected.");
		
		try {
			output = new ObjectOutputStream(socket.getOutputStream());
		} catch (IOException e) {
			throw new RuntimeException("Couldn't create output stream from socket.", e);
		}
		
		Log("Opened output stream.");
	}
	
	public void setSocket(Socket pSocket, ObjectInputStream pInput)
	{
		if(connected) {
			throw new RuntimeException("Cannot set socket of a remote node more than once.");
		}
		socket = pSocket;
		input = pInput;
		connected = true;
		
		try {
			output = new ObjectOutputStream(socket.getOutputStream());
		} catch (IOException e) {
			throw new RuntimeException("Couldn't create output stream from socket.", e);
		}
	}
	
	public void sendMessage(Message msg) {
		Log("Sending " + msg + " to remote node...");
		try {
			output.writeObject(msg);
		} catch (IOException e) {
			throw new RuntimeException("Couldn't send message through output stream.");
		}
	}
	
	private Message receiveMessage()
	{
		try {
			Message msg = (Message) input.readObject();
			if(msg.getNodeId() != nodeId) {
				throw new RuntimeException("A message with wrong node id received.");
			}
			return msg;
		} catch (ClassNotFoundException e) {
			throw new RuntimeException("Couldn't find Message class.", e);
		} catch(SocketException e) {
			if(!exiting) {
				throw new RuntimeException("Socket closed " + RemoteNode.this, e);
			}
		} catch (IOException e) {
			if(!exiting) {
				throw new RuntimeException("IO problem while receiving message from " + RemoteNode.this, e);
			}
		}
		return null;
	}
	
	public void startListenerThread(BlockingQueue<Event> pEventQueue)
	{
		if(listenerThread != null) {
			throw new RuntimeException("Cannot run listener thread of remote node more than once.");
		}
		if(input == null) {
			try {
				input = new ObjectInputStream(socket.getInputStream());
			} catch (IOException e) {
				throw new RuntimeException("Couldn't create input stream from socket.", e);
			}
		}
		eventQueue = pEventQueue;
		listenerThread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					while(!Thread.interrupted())
					{
						Message msg = receiveMessage();
						Log("Received " + msg + " from socket. Placing into queue...");
						eventQueue.put(new Event(EventType.MESSAGE_RECEIVED, msg));
						if(msg == null) {
							Log("Listener thread returning null!");
							return;
						}
					}
				} catch (InterruptedException e) {
					Log("Listener thread has been interrupted!");
				}
			}
		});
		listenerThread.start();
	}

	public int getNodeId() {
		return nodeId;
	}
	
	public boolean hasGrantedPermission()
	{
		return grantedPermission;
	}
	
	public void setGrantedPermission(boolean pGrantedPermission)
	{
		grantedPermission = pGrantedPermission;
	}
	
	public boolean hasSameInetAddress(InetAddress rhs)
	{
		return inetAddress.equals(rhs);
	}
	
	public void setSignaledDone()
	{
		signaledDone = true;
	}
	
	public boolean hasSignaledDone()
	{
		return signaledDone;
	}
	
	public void setLastReplyTimestamp(int pLastReplyTimestamp)
	{
		lastReplyTimestamp = pLastReplyTimestamp;
	}
	
	public int getLastReplyTimestamp()
	{
		return lastReplyTimestamp;
	}
	
	public void prepareToExit()
	{
		exiting = true;
	}
	
	private void Log(String msg)
	{
		Main.Log("REMOTE_NODE" + nodeId + ": " + msg);
	}
	
	private void Output(String msg)
	{
		Main.Output("REMOTE_NODE" + nodeId + ": " + msg);
	}
	
	@Override
	public String toString()
	{
		return "(" + nodeId + ", " + inetAddress.toString() + ")";
	}

	@Override
	public void close() {
		if(listenerThread != null && listenerThread.isAlive()) {
			try {
				listenerThread.join(1000);
			} catch (InterruptedException e) {
				// ignore
			}
		}
		try {
			if(input != null) {
				input.close();
			}
		} catch (IOException e) {
			// ignore
		}
		
		try {
			if(output != null) {
				output.close();
			}
		} catch (IOException e) {
			// ignore
		}
		
		try {
			if(socket != null) {
				socket.close();
			}
		} catch (IOException e) {
			// ignore
		}
	}
}
