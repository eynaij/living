package com.qingying;

import java.util.Locale;

final class Fmt {
    private Fmt() { }

    static String duration(long ms) {
        if (ms <= 0) return "00:00";
        long total = ms / 1000;
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        if (h > 0) return String.format(Locale.US, "%d:%02d:%02d", h, m, s);
        return String.format(Locale.US, "%02d:%02d", m, s);
    }

    static String size(long bytes) {
        if (bytes <= 0) return "";
        double mb = bytes / 1024.0 / 1024.0;
        if (mb < 1) return String.format(Locale.US, "%.0f KB", bytes / 1024.0);
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb);
        return String.format(Locale.US, "%.2f GB", mb / 1024.0);
    }
}
