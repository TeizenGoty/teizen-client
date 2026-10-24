package dev.teizen.client.core;

import java.util.List;
import java.util.Locale;

/** Einfache Einstellung eines Moduls (Zahl, Schalter oder Text). */
public final class Setting {
    public enum Type { NUMBER, BOOL, TEXT, CHOICE }

    public final String name;
    public final String description;
    public final Type type;
    private double number;
    private boolean bool;
    private String text;
    public final double min, max;
    public final List<String> choices;

    private Setting(String name, String description, Type type, double number, boolean bool, String text,
                    double min, double max, List<String> choices) {
        this.name = name;
        this.description = description;
        this.type = type;
        this.number = number;
        this.bool = bool;
        this.text = text;
        this.min = min;
        this.max = max;
        this.choices = choices;
    }

    public static Setting number(String name, String desc, double value, double min, double max) {
        return new Setting(name, desc, Type.NUMBER, value, false, null, min, max, List.of());
    }

    public static Setting bool(String name, String desc, boolean value) {
        return new Setting(name, desc, Type.BOOL, 0, value, null, 0, 0, List.of());
    }

    public static Setting text(String name, String desc, String value) {
        return new Setting(name, desc, Type.TEXT, 0, false, value, 0, 0, List.of());
    }

    public static Setting choice(String name, String desc, String value, String... options) {
        return new Setting(name, desc, Type.CHOICE, 0, false, value, 0, 0, List.of(options));
    }

    public double num() { return number; }
    public int integer() { return (int) Math.round(number); }
    public float flt() { return (float) number; }
    public boolean on() { return bool; }
    public String str() { return text; }
    public boolean is(String choice) { return choice.equalsIgnoreCase(text); }

    public void setNumber(double v) { number = Math.max(min, Math.min(max, v)); }
    public void setBool(boolean v) { bool = v; }
    public void setText(String v) { text = v; }

    /** Wert aus Text setzen (für ///set). Gibt eine Fehlermeldung zurück oder null. */
    public String parse(String raw) {
        String v = raw.trim();
        switch (type) {
            case NUMBER -> {
                try {
                    double d = Double.parseDouble(v.replace(',', '.'));
                    if (d < min || d > max) return "Wert muss zwischen " + fmt(min) + " und " + fmt(max) + " liegen";
                    setNumber(d);
                } catch (NumberFormatException e) {
                    return "Das ist keine Zahl: " + v;
                }
            }
            case BOOL -> {
                String l = v.toLowerCase(Locale.ROOT);
                if (l.equals("an") || l.equals("on") || l.equals("true") || l.equals("ja") || l.equals("1")) setBool(true);
                else if (l.equals("aus") || l.equals("off") || l.equals("false") || l.equals("nein") || l.equals("0")) setBool(false);
                else return "Bitte an oder aus angeben";
            }
            case CHOICE -> {
                for (String c : choices) {
                    if (c.equalsIgnoreCase(v)) { setText(c); return null; }
                }
                return "Möglich: " + String.join(", ", choices);
            }
            case TEXT -> setText(v);
        }
        return null;
    }

    public String display() {
        return switch (type) {
            case NUMBER -> fmt(number);
            case BOOL -> bool ? "an" : "aus";
            case TEXT, CHOICE -> text;
        };
    }

    public static String fmt(double d) {
        if (d == Math.rint(d)) return String.valueOf((long) d);
        return String.format(Locale.ROOT, "%.2f", d);
    }
}
