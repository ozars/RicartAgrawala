package aosproject;
import java.io.Serializable;

public enum MessageType implements Serializable
	{
		HELLO(1),
		REQUEST(2),
		REPLY(3),
		DONE(4),
		EXIT(5);
		
		private MessageType(int pValue)
		{
			value = pValue;
		}
		private int value;
	}
