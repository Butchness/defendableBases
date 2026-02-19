package com.defendablebases.client;

public final class ClientConfigCache {
    private ClientConfigCache() {}

    // Default mirrors server default; server will overwrite on login via packet.
    private static int maxPrivileged = 16;

    public static int getMaxPrivileged() {
        return maxPrivileged;
    }

    public static void setMaxPrivileged(int value) {
        maxPrivileged = value;
    }
}
