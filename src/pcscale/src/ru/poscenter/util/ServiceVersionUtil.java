package ru.poscenter.util;

public class ServiceVersionUtil {

    public static int getVersionInt() {
        return versionStringToInt(ServiceVersion.VERSION);
    }
    
    public static int versionStringToInt(String version) {
        int result;
        String parts[] = version.split("\\.");
        try {
            if (parts.length < 2) {
                return 0;
            }

            result = Integer.parseInt(parts[0]) * 1000000 + 
                    Integer.parseInt(parts[1]) * 1000 + 
                    Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            e.printStackTrace();
            result = 0;
        }
        return result;
    }
}
