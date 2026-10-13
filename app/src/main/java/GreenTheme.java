package com.zeytindefteri.app;

/** Shared Java/XML palette: the actual colors live in res/values/colors.xml. */
public final class GreenTheme {
    public static int parse(String value) {
        if (value == null) throw new IllegalArgumentException("color");
        switch (value.toUpperCase(java.util.Locale.ROOT)) {
            case "#0B1D28": return color(R.color.zd_bg);
            case "#102632": case "#102733": case "#122D38": return color(R.color.zd_surface);
            case "#132A38": return color(R.color.zd_card);
            case "#17313D": return color(R.color.zd_input);
            case "#79C95A": case "#72B957": return color(R.color.zd_green);
            case "#19392F": return color(R.color.zd_green_soft);
            case "#F4F7F4": case "#F2F7F0": case "#EAF5E7": return color(R.color.zd_text);
            case "#9AABA8": case "#7F918E": return color(R.color.zd_muted);
            case "#C4CFCC": return color(R.color.zd_text_soft);
            case "#29434D": return color(R.color.zd_border);
            default: return android.graphics.Color.parseColor(value);
        }
    }
    private GreenTheme() { }
    public static int color(int resource) {
        return ZeytinApplication.paletteResources().getColor(resource, null);
    }
}
