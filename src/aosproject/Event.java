package aosproject;

public class Event {
	public Event(EventType pEventType, Object pPayload)
	{
		eventType = pEventType;
		payload = pPayload;
	}
	
	public EventType getEventType()
	{
		return eventType;
	}
	
	public Object getPayload()
	{
		return payload;
	}
	
	private EventType eventType;
	private Object payload;
}
