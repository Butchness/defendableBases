package com.defendablebases;

public final class ModConfigValues {
    private ModConfigValues() {}

    // TODO: next step: expose via Forge config (common.toml)
    public static int maxPrivileged() {
        return 16;
    }
}
