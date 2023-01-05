package dslab.nameserver;

import java.io.PrintStream;
import java.time.LocalDateTime;

public class Logger {
    private static final Object streamLock = new Object();
    public static PrintStream logStream;

    public static void log(String message) {
        var currentTime = LocalDateTime.now();
        String output = "[" + currentTime.getYear()
                + "-" + currentTime.getMonthValue()
                + "-" + currentTime.getDayOfMonth()
                + " " + currentTime.getHour()
                + ":" + currentTime.getMinute()
                + ":" + currentTime.getSecond()
                + "] " + message;

        synchronized (streamLock) {
            logStream.println(output);
        }
    }

    public static void setLogStream(PrintStream stream) {
        synchronized (streamLock) {
            logStream = stream;
        }
    }
}
