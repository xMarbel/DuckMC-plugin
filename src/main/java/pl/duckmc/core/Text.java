package pl.duckmc.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Pomocnik do tekstow (MiniMessage). */
public final class Text {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Text() {}

    /** Tekst bez kursywy (do nazw i lore itemow). */
    public static Component c(String s) {
        return MM.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    public static List<Component> c(List<String> list) {
        List<Component> out = new ArrayList<>();
        for (String s : list) out.add(c(s));
        return out;
    }

    /** Zwykly tekst MiniMessage. */
    public static Component raw(String s) {
        return MM.deserialize(s);
    }

    public static String duration(long ms) {
        if (ms < 0) ms = 0;
        long s = ms / 1000;
        long d = s / 86400; s %= 86400;
        long h = s / 3600; s %= 3600;
        long m = s / 60; s %= 60;
        return d + "d, " + h + "h, " + m + "min, " + s + "s";
    }

    public static String playtime(long ticks) {
        long min = ticks / 20 / 60;
        return (min / 60) + "h, " + (min % 60) + "min";
    }

    public static String money(double v) {
        if (v >= 1_000_000_000d) return String.format(Locale.US, "%.2fB", v / 1e9);
        if (v >= 1_000_000d) return String.format(Locale.US, "%.2fM", v / 1e6);
        if (v >= 1_000d) return String.format(Locale.US, "%.2fk", v / 1e3);
        return String.format(Locale.US, "%.2f", v);
    }

    public static String full(double v) {
        return String.format(Locale.US, "%,.0f", v);
    }
}
