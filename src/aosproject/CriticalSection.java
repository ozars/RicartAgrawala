package aosproject;
import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.Selector;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;

public class CriticalSection implements Closeable {
	public static final int CriticalSectionDuration = 1000;
	
	public enum CriticalSectionStatus
	{
		IDLE,
		SCHEDULED,
		REQUESTING,
		REQUESTED,
		ENTERED,
		EXITED
	};
	
	public CriticalSection(BlockingQueue<Event> pEventQueue, int pTimeUnit)
	{
		eventQueue = pEventQueue;
		timeUnit = pTimeUnit;
		requestTask = new RequestTask();
		exitTask = new ExitTask();
	}
	
	public void Schedule(long delay)
	{
		Log("Scheduled within " + delay + "ms");
		setStatus(CriticalSectionStatus.SCHEDULED);
		requestTask = new RequestTask();
		timer.schedule(requestTask, delay);
	}
	
	public void Enter()
	{
		latency = (System.nanoTime() - latency) / 1e6;
		Main.Output("Logical timestamp: " + requestTimestamp);
		Main.Output("Latency: " + latency + "ms");
		setStatus(CriticalSectionStatus.ENTERED);
		exitTask = new ExitTask();
		timer.schedule(exitTask, 3 * timeUnit);
	}
	
	public void Requested(int pRequestTimestamp)
	{
		Log("Request announced with timestamp " + pRequestTimestamp);
		requestTimestamp = pRequestTimestamp;
		setStatus(CriticalSectionStatus.REQUESTED);
	}
	
	public int getCount()
	{
		return count;
	}
	
	public int getRequestTimestamp()
	{
		return requestTimestamp;
	}
	
	public CriticalSectionStatus getStatus()
	{
		synchronized (status) {
			return status;
		}
	}
	
	private void setStatus(CriticalSectionStatus pStatus)
	{
		synchronized (status) {
			status = pStatus;
		}
	}
	
	private class RequestTask extends TimerTask {
		@Override
		public void run() {
			Log("Request initiated.");
			setStatus(CriticalSectionStatus.REQUESTING);
			try {
				latency = System.nanoTime();
				eventQueue.put(new Event(EventType.CRITICAL_SECTION_STATUS_CHANGED, CriticalSectionStatus.REQUESTING));
			} catch (InterruptedException e) {
				throw new RuntimeException("Received an interrupt while adding event.", e);
			}
		}
	}
	
	private class ExitTask extends TimerTask {
		@Override
		public void run() {
			count++;
			Log("Exiting with counter value " + count);
			setStatus(CriticalSectionStatus.EXITED);
			try {
				eventQueue.put(new Event(EventType.CRITICAL_SECTION_STATUS_CHANGED, CriticalSectionStatus.EXITED));
			} catch (InterruptedException e) {
				throw new RuntimeException("Received an interrupt while adding event.", e);
			}
		}
	}
	
	private static void Log(String msg)
	{
		Main.Log("CRITICAL_SECTION: " + msg);
	}
	
	private static void Output(String msg)
	{
		Main.Output("CRITICAL_SECTION: " + msg);
	}
	
	@Override
	public void close() {
		timer.cancel();
	}
	
	private int requestTimestamp;
	private BlockingQueue<Event> eventQueue;
	private CriticalSectionStatus status = CriticalSectionStatus.IDLE;
	private int count = 0;
	private Timer timer = new Timer();
	private TimerTask requestTask;
	private TimerTask exitTask;
	private int timeUnit;
	private double latency;
}
