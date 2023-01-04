package dslab.nameserver;

import java.io.PrintStream;
import java.time.LocalDateTime;

public class Logger {
    public static PrintStream logStream;

    public static void log(String message) {
        var currentTime = LocalDateTime.now();
        logStream.println("[" + currentTime.getDayOfMonth()
                + "/" + currentTime.getMonthValue()
                + "/" + currentTime.getYear()
                + " " + currentTime.getHour()
                + " " + currentTime.getMinute()
                + ":" + currentTime.getSecond()
                + "] " + message);
    }

    public static void setLogStream(PrintStream stream) {
        logStream = stream;
    }
}
