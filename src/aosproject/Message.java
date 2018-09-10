package aosproject;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;

public class Message implements Serializable {
	public Message(int pNodeId, MessageType pMessageType)
	{
		nodeId = pNodeId;
		messageType = pMessageType;
	}
	
	public Message(int pNodeId, MessageType pMessageType, int pTimestamp)
	{
		nodeId = pNodeId;
		messageType = pMessageType;
		timestamp = pTimestamp;
	}
	
	public Message(int pNodeId, MessageType pMessageType, int pTimestamp, int pRequestTimestamp)
	{
		nodeId = pNodeId;
		messageType = pMessageType;
		timestamp = pTimestamp;
		requestTimestamp = pRequestTimestamp;
	}
	
	public int getNodeId()
	{
		return nodeId;
	}
	
	public MessageType getMessageType()
	{
		return messageType;
	}
	
	public int getTimestamp()
	{
		return timestamp;
	}
	
	public int getRequestTimestamp()
	{
		return requestTimestamp;
	}
	
	@Override
	public String toString()
	{
		return "(" + nodeId + ", " + messageType + ", " + timestamp + ")";
	}
	
	private int nodeId;
	private MessageType messageType;
	private int timestamp;
	private int requestTimestamp;
	
}
