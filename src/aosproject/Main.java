package aosproject;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Main {
	
	public static Node node;
	
	/**
	 * @param args
	 */
	public static void main(String[] args)
	{
		/* That's not a great practice, but makes things easier to deal with in a small project. */
		Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
			@Override
			public void uncaughtException(Thread thread, Throwable e) {
				Main.Error("An exception occured. See details below.", e);
			}
		});
		
		int tcpPort = -1;
		String[] nodeHostnames = null;
		String configFile = "node.config";
		
		if(args.length == 1) {
			configFile = args[1];
		} else if(args.length != 0) {
			throw new RuntimeException("Usage: java Node [config-file]\n\tDefault configuration file is 'node.config'.");
		}
		
		try(BufferedReader br = new BufferedReader(new FileReader(configFile))) {
			String line;
			while((line = br.readLine()) != null)
			{
				line = line.trim();
				if(line.length() > 0 && line.charAt(0) != '#') {
					String[] params = line.split("\\s*=\\s*");
					if(params.length != 2) {
						throw new RuntimeException("There must be exactly one '=' sign in every line of configuration:\n\t" + line);
					}
					if(params[0].equals("nodes")) {
						nodeHostnames = params[1].split("\\s+");
					} else if(params[0].equals("port")) {
						tcpPort = Integer.parseInt(params[1]);
						if(tcpPort <= 0) {
							throw new RuntimeException("TCP port should be between 1-65535.");
						}
					} else {
						throw new RuntimeException("Unknown configuration: " + params[0]);
					}
				}
			}
			
			if(tcpPort == -1) {
				throw new RuntimeException("TCP port is not configured.");
			}
			if(nodeHostnames == null) {
				throw new RuntimeException("Remote hostnames are not configured.");
			}
		} catch (FileNotFoundException e) {
			throw new RuntimeException("Couldn't find configuration file.", e);
		} catch (IOException e) {
			throw new RuntimeException("I/O problem reading configuration file.", e);
		}

		try(Node node = new Node(tcpPort, nodeHostnames))
		{
			node.start();
		}
	}

	public static synchronized void Error(String msg, Throwable e)
	{
		if(node != null) {
			node.close();
		}
		
		System.err.println(new Date());
		System.err.println("THREAD: " + Thread.currentThread());
		System.err.println("ERROR: " + msg);
		if(e != null) {
			System.err.println("EXCEPTION:");
			e.printStackTrace(System.err);
			for(e = e.getCause(); e != null; e = e.getCause())
			{
				System.err.println("NESTED EXCEPTION:");
				System.err.println(e.toString());
				e.printStackTrace(System.err);
			}
		}
		System.exit(1);
	}

    public static void Log(String msg)
    {
        System.err.println("[LOG]" + dateFormat.format(new Date()) + msg);
        System.err.flush();
    }
    
    public static void Output(String msg)
    {
    	if(System.err != System.out) {
    		Log(msg);
    	}
    	System.out.println(dateFormat.format(new Date()) + msg);
    }

    private static SimpleDateFormat dateFormat = new SimpleDateFormat("[hh:mm:ss.SSS] ");

}
