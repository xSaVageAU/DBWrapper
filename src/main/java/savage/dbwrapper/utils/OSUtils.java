package savage.dbwrapper.utils;

public class OSUtils {
    private static final String OS = System.getProperty("os.name").toLowerCase();

    public static boolean isWindows() {
        return OS.contains("win");
    }

    public static boolean isMac() {
        return OS.contains("mac");
    }

    public static boolean isLinux() {
        return OS.contains("linux") || OS.contains("nix") || OS.contains("nux");
    }

    public static boolean isUnix() {
        return isLinux() || isMac();
    }

    public static String getOSName() {
        if (isWindows()) {
            return "winx64"; // MariaDB uses "winx64" in their filenames
        } else if (isMac()) {
            return "macos";
        } else if (isLinux()) {
            return "linux";
        } else {
            return "unknown";
        }
    }

    public static String getArchitecture() {
        String arch = System.getProperty("os.arch").toLowerCase();
        if (arch.contains("64")) {
            return "x64";
        } else if (arch.contains("86")) {
            return "x86";
        } else if (arch.contains("arm")) {
            return "arm";
        } else {
            return "unknown";
        }
    }

    public static String getBinarySuffix() {
        if (isWindows()) {
            return ".exe";
        } else {
            return "";
        }
    }
}