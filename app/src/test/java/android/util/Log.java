package android.util;

/** JVM unit tests: android.jar's Log throws "not mocked"; the patch engine logs while parsing. */
public final class Log {
    public static int v(String tag, String msg) { return 0; }
    public static int d(String tag, String msg) { return 0; }
    public static int d(String tag, String msg, Throwable tr) { return 0; }
    public static int i(String tag, String msg) { return 0; }
    public static int i(String tag, String msg, Throwable tr) { return 0; }
    public static int w(String tag, String msg) { return 0; }
    public static int w(String tag, String msg, Throwable tr) { return 0; }
    public static int w(String tag, Throwable tr) { return 0; }
    public static int e(String tag, String msg) { return 0; }
    public static int e(String tag, String msg, Throwable tr) { return 0; }
    public static int println(int priority, String tag, String msg) { return 0; }
    public static String getStackTraceString(Throwable tr) { return String.valueOf(tr); }
    public static boolean isLoggable(String tag, int level) { return false; }
}
