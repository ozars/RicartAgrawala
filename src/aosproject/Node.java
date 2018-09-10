package aosproject;
import java.io.Closeable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import aosproject.CriticalSection.CriticalSectionStatus;

public class Node implements Closeable {
	
	public Node(int pTcpPort, String[] pNodeHostnames)
	{
		tcpPort = pTcpPort;
		remoteNodes = new RemoteNode[pNodeHostnames.length];
		
		for(int i = 0; i < pNodeHostnames.length; i++)
		{
			InetAddress nodeAddress;
			try {
				nodeAddress = InetAddress.getByName(pNodeHostnames[i]);
			} catch (UnknownHostException e) {
				throw new RuntimeException("Couldn't resolve " + pNodeHostnames[i]);
			}
			
			remoteNodes[i] = new RemoteNode(i, nodeAddress);

            try {
                if(nodeAddress.isAnyLocalAddress() || nodeAddress.isLoopbackAddress() || NetworkInterface.getByInetAddress(nodeAddress) != null) {
                    address = nodeAddress;
                    nodeId = i;
                    remoteNodes[nodeId].setGrantedPermission(true);
                }
            } catch(SocketException e) {
                // ignore
            }
		}
		
		if(nodeId == -1) {
			throw new RuntimeException("This host is not included in listed nodes. Check configuration file.");
		}
	}
	
	public void start()
	{
		setupConnections();
		phase(1);
		phase(2);
		gracefulExit();
	}
	
	@Override
	public void close() {
		if(remoteNodes != null) {
			for(RemoteNode remoteNode : remoteNodes)
			{
				remoteNode.close();
			}
		}
	}
	
	private void setupConnections()
	{
		int remainingNodes = remoteNodes.length - 1;
		Message helloMessage = new Message(nodeId, MessageType.HELLO);
		
		for(int i = 0; i < nodeId; i++)
		{
			Main.Log("Connecting to " + remoteNodes[i]);
			remoteNodes[i].connect(tcpPort);
			
			Main.Log("Sending hello message to " + remoteNodes[i]);
			remoteNodes[i].sendMessage(helloMessage);
			sentMessagesCount++;
			
			remoteNodes[i].startListenerThread(eventQueue);
			remainingNodes--;
		}
		
		try(ServerSocket serverSocket = new ServerSocket()) {
			serverSocket.bind(new InetSocketAddress(address, tcpPort));
			
			Main.Log("Bound to port tcp/" + tcpPort);
			
			while(remainingNodes > 0)
			{
				Socket clientSocket = serverSocket.accept();
				
				ObjectInputStream ois = new ObjectInputStream(clientSocket.getInputStream());
				try
				{
					helloMessage = (Message) ois.readObject();
					Main.Log("Received: " + helloMessage);
					if(helloMessage.getMessageType() != MessageType.HELLO) {
						ois.close();
						throw new RuntimeException("Unexpected message type while expecting HELLO message.");
					}
					if(!remoteNodes[helloMessage.getNodeId()].hasSameInetAddress(clientSocket.getInetAddress())) {
						ois.close();
						throw new RuntimeException("HELLO message received from incorrect source.");
					}
					Main.Log("Identified HELLO message from " + remoteNodes[helloMessage.getNodeId()]);
					remoteNodes[helloMessage.getNodeId()].setSocket(clientSocket, ois);
					remoteNodes[helloMessage.getNodeId()].startListenerThread(eventQueue);
				} catch (ClassNotFoundException e) {
					ois.close();
					throw new RuntimeException("Message class not found.", e);
				}
				remainingNodes--;
			}
		} catch (IOException e) {
			throw new RuntimeException("Problem with server socket.", e);
		}
	}
	
	private int getRandomScheduleDelay(int currentPhase)
	{
		double rnd = random.nextDouble();
		switch(currentPhase)
		{
			case 1:
				rnd = 5 * rnd + 5;
				break;
			case 2:
				rnd = (nodeId % 2 == 0 ? 5 * rnd + 5 : 5 * rnd + 45);
				break;
			default:
				throw new RuntimeException("Unknown phase: " + currentPhase);
		}
		return (int)(rnd * TimeUnit);
	}
	
	private void phase(int currentPhase)
	{
		Main.Output("Entering phase " + currentPhase + "...");
		long latency = 0;
		
		try(CriticalSection criticalSection = new CriticalSection(eventQueue, TimeUnit))
		{
			criticalSection.Schedule(getRandomScheduleDelay(currentPhase));
			
			while(criticalSection.getCount() < 20)
			{
				Event event;
				try {
					event = eventQueue.take();
				} catch (InterruptedException e) {
					throw new RuntimeException("Received an interrupt while taking an event from queue.", e);
				}
				
				if(event.getEventType() == EventType.CRITICAL_SECTION_STATUS_CHANGED) {
					CriticalSectionStatus status = (CriticalSectionStatus) event.getPayload();
					if(status == CriticalSectionStatus.REQUESTING) {
						latency = System.nanoTime();
						Main.Log("Handling critical section request event...");
						if(permissionCount < remoteNodes.length) {
							Message requestMessage = createRequestMessage();
							for(int i = 0; i < remoteNodes.length; i++)
							{
								RemoteNode remoteNode = remoteNodes[i];
								if(!remoteNode.hasGrantedPermission()) {
									remoteNode.sendMessage(requestMessage);
									sentMessagesCount++;
								}
							}
						} else {
							increaseTimestamp();
						}
						criticalSection.Requested(timestamp);
						if(permissionCount == remoteNodes.length) {
							Main.Output("Entering " + nodeId);
							Main.Output("Total messages sent: " + sentMessagesCount + ", received: " + receivedMessagesCount);
							criticalSection.Enter();
						}
					} else if(status == CriticalSectionStatus.EXITED) {
						Main.Log("Handling EXITED event...");
						if(deferredNodes.size() > 0) {
							Main.Log("Sending replies to " + deferredNodes.size() + " deferred nodes...");
							Message replyMessage = createTimestampedMessage(MessageType.REPLY);
							for(RemoteNode deferredNode : deferredNodes)
							{
								deferredNode.sendMessage(replyMessage);
								sentMessagesCount++;
								deferredNode.setGrantedPermission(false);
							}
							permissionCount -= deferredNodes.size();
							Main.Log("Permission count: " + permissionCount);
							deferredNodes.clear();
						}
						if(criticalSection.getCount() < 20) {
							Main.Log("Rescheduling critical section...");
							criticalSection.Schedule(getRandomScheduleDelay(currentPhase));
						}
					} else {
						throw new RuntimeException("Unexpected critical section status: " + status);
					}
				} else if(event.getEventType() == EventType.MESSAGE_RECEIVED) {
					receivedMessagesCount++;
					Message msg = (Message) event.getPayload();
					RemoteNode remoteNode = remoteNodes[msg.getNodeId()];
					CriticalSectionStatus status = criticalSection.getStatus();
					
					Main.Log(msg + " popped out from queue, current status: " + status);
					
					increaseTimestamp(msg.getTimestamp());
					switch(msg.getMessageType())
					{
						case REQUEST:
							if(
								status == CriticalSectionStatus.ENTERED ||
								(
									(status == CriticalSectionStatus.REQUESTING	|| status == CriticalSectionStatus.REQUESTED)
									&& (
										criticalSection.getRequestTimestamp() < msg.getRequestTimestamp()
										|| (
											criticalSection.getRequestTimestamp() == msg.getRequestTimestamp() && nodeId < msg.getNodeId()
										)
									)
								)
							) {
								Main.Log("Deferring reply to node " + msg.getNodeId() + ", status: " + status + "(" + criticalSection.getRequestTimestamp() + ")");
								deferredNodes.add(remoteNode);
							} else {
								Main.Log("Sending reply, permission counter: " + permissionCount);
								remoteNode.sendMessage(createTimestampedMessage(MessageType.REPLY, msg.getTimestamp()));
								sentMessagesCount++;
								if(remoteNode.hasGrantedPermission()) {
									if(status == CriticalSectionStatus.REQUESTED && remoteNode.getLastReplyTimestamp() < criticalSection.getRequestTimestamp()) {
										remoteNode.sendMessage(createRequestMessage(criticalSection.getRequestTimestamp()));
										sentMessagesCount++;
									}
									remoteNode.setGrantedPermission(false);
									permissionCount--;
									Main.Log("Permission count: " + permissionCount);
								}
							}
							break;
						case REPLY:
							Main.Log("Reply received, permission counter: " + permissionCount);
							remoteNode.setLastReplyTimestamp(msg.getTimestamp());
							if(!remoteNode.hasGrantedPermission()) {
								remoteNode.setGrantedPermission(true);
								permissionCount++;
								Main.Log("Permission count: " + permissionCount);
							}
 							break;
						case DONE:
							if(nodeId != 0) {
								throw new RuntimeException("Only node 0 can receive done message.");
							} else {
								if(!remoteNode.hasSignaledDone()) {
									remoteNode.setSignaledDone();
									doneNodeCount++;
								} else {
									throw new RuntimeException("Multiple DONE signal received from a node.");
								}
							}
							break;
						default:
							throw new RuntimeException("Unexpected message received.");
					}
				} else {
					throw new RuntimeException("Unexpected event type.");
				}
				
				if(criticalSection.getStatus() == CriticalSectionStatus.REQUESTED && permissionCount == remoteNodes.length) {
					Main.Output("Entering " + nodeId);
					Main.Output("Total messages sent: " + sentMessagesCount + ", received: " + receivedMessagesCount);
					criticalSection.Enter();
				}
			}
		}
		
	}
	
	private void gracefulExit()
	{
		Main.Output("Gracefully exiting...");
		if(nodeId == 0) {
			doneNodeCount++;
			while(doneNodeCount < remoteNodes.length)
			{
				Event event;
				try {
					event = eventQueue.take();
				} catch (InterruptedException e) {
					throw new RuntimeException("Received an interrupt while taking an event from queue.", e);
				}
				if(event.getEventType() != EventType.MESSAGE_RECEIVED) {
					throw new RuntimeException("Only receive events should happen during graceful exit period.");
				}
				receivedMessagesCount++;
				Message msg = (Message) event.getPayload();
				RemoteNode remoteNode = remoteNodes[msg.getNodeId()];
				switch(msg.getMessageType())
				{
					case REQUEST:
						remoteNode.sendMessage(createTimestampedMessage(MessageType.REPLY, msg.getTimestamp()));
						sentMessagesCount++;
						break;
					case DONE:
						if(!remoteNode.hasSignaledDone()) {
							remoteNode.setSignaledDone();
							doneNodeCount++;
						} else {
							throw new RuntimeException("Multiple DONE signal received from a node.");
						}
						break;
					default:
						throw new RuntimeException("Unexpected message type received.");
				}
			}
			for(int i = 1; i < remoteNodes.length; i++)
			{
				remoteNodes[i].prepareToExit();
				remoteNodes[i].sendMessage(createTimestampedMessage(MessageType.EXIT));
				sentMessagesCount++;
			}
			int remotelyClosedSocketsCount = 0;
			while(remotelyClosedSocketsCount < remoteNodes.length - 1)
			{
				Event event;
				try {
					event = eventQueue.take();
				} catch (InterruptedException e) {
					throw new RuntimeException("Received an interrupt while taking an event from queue.", e);
				}
				Message msg = (Message) event.getPayload();
				if(msg == null) {
					remotelyClosedSocketsCount++;
				}
			}
			
		} else {
			for(RemoteNode remoteNode : remoteNodes)
			{
				remoteNode.prepareToExit();
			}
			remoteNodes[0].sendMessage(createTimestampedMessage(MessageType.DONE));
			sentMessagesCount++;
			boolean exitReceived = false;
			while(!exitReceived)
			{
				Event event;
				try {
					event = eventQueue.take();
				} catch (InterruptedException e) {
					throw new RuntimeException("Received an interrupt while taking an event from queue.", e);
				}
				if(event.getEventType() != EventType.MESSAGE_RECEIVED) {
					throw new RuntimeException("Only receive events should happen during graceful exit period.");
				}
				receivedMessagesCount++;
				Message msg = (Message) event.getPayload();
				RemoteNode remoteNode = remoteNodes[msg.getNodeId()];
				switch(msg.getMessageType())
				{
					case REQUEST:
						remoteNode.sendMessage(createTimestampedMessage(MessageType.REPLY, msg.getTimestamp()));
						sentMessagesCount++;
						break;
					case EXIT:
						if(msg.getNodeId() != 0) {
							throw new RuntimeException("Only node 0 can send EXIT messages.");
						}
						exitReceived = true;
						break;
					default:
						throw new RuntimeException("Unexpected message type received.");
				}
			}
			
		}
	}
	
	private int increaseTimestamp()
	{
		//Main.Log("Old timestamp: " + timestamp);
		timestamp += d;
		//Main.Log("New timestamp: " + timestamp);
		return timestamp;
	}
	
	private int increaseTimestamp(int messageTimestamp)
	{
		//Main.Log("Old timestamp: " + timestamp + ", Message timestamp: " + messageTimestamp);
		timestamp += d;
		if(messageTimestamp >= timestamp) {
			timestamp = messageTimestamp + d;
		}
		//Main.Log("New timestamp: " + timestamp);
		return timestamp;
	}
	
	private Message createTimestampedMessage(MessageType messageType)
	{
		return new Message(nodeId, messageType, increaseTimestamp());
	}
	
	private Message createTimestampedMessage(MessageType messageType, int remoteTimestamp)
	{
		return new Message(nodeId, messageType, increaseTimestamp(remoteTimestamp));
	}
	
	private Message createRequestMessage()
	{
		increaseTimestamp();
		return new Message(nodeId, MessageType.REQUEST, timestamp, timestamp);
	}
	
	private Message createRequestMessage(int requestTimestamp)
	{
		return new Message(nodeId, MessageType.REQUEST, increaseTimestamp(), requestTimestamp);
	}

	private BlockingQueue<Event> eventQueue = new LinkedBlockingQueue<Event>();
	private Set<RemoteNode> deferredNodes = new HashSet<RemoteNode>();
	private Random random = new Random();
	private RemoteNode[] remoteNodes;
	private InetAddress address;
	private int tcpPort;
	private int doneNodeCount;
	
	private int permissionCount = 1;
	private int nodeId = -1;
	private int timestamp = 0;
	private final int d = 1;
	private static final int TimeUnit = 10;
	
	private int sentMessagesCount;
	private int receivedMessagesCount;

}
