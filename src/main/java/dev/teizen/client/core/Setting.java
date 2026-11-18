package dev.teizen.client.core;

import java.util.List;
import java.util.Locale;

/** Einfache Einstellung eines Moduls (Zahl, Schalter oder Text). */
public final class Setting {
    public enum Type { NUMBER, BOOL, TEXT, CHOICE, MULTI, ACTION }

    /** Bei ACTION: was beim Anklicken passiert. */
    public Runnable action;

    public final String name;
    public final String description;
    public final Type type;
    private double number;
    private boolean bool;
    private String text;
    public final double min, max;
    public final List<String> choices;
    /** Anzeigename je Listen-Eintrag (z.B. "village" -> "Dorf"), sonst der Eintrag selbst. */
    private java.util.function.Function<String, String> optionLabel;
    /** Bei CHOICE: Liste kommt live (z.B. Wegpunkte), sonst die feste Liste choices. */
    private java.util.function.Supplier<List<String>> dynamic;

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

    /** Auswahl, deren Einträge erst zur Laufzeit feststehen (z.B. die Wegpunkte von Xaero's Karte). */
    public static Setting dynamicChoice(String name, String desc, String value, java.util.function.Supplier<List<String>> options) {
        Setting s = new Setting(name, desc, Type.CHOICE, 0, false, value, 0, 0, List.of());
        s.dynamic = options;
        return s;
    }

    /** Liste kommt live (z.B. Wegpunkte) -> im Menü als aufklappbare Liste. */
    public boolean isDynamic() { return dynamic != null; }

    /** Die Auswahlliste – live, wenn dynamisch, sonst die feste. Immer mindestens ein Eintrag. */
    public List<String> choiceList() {
        if (dynamic == null) return choices;
        List<String> live = dynamic.get();
        if (type == Type.MULTI) return live == null ? List.of() : live;
        return live == null || live.isEmpty() ? List.of(str() == null ? "" : str()) : live;
    }

    /**
     * Liste zum Abhaken mit Live-Einträgen. Der Text speichert die AUSGESCHALTETEN Einträge (neue Einträge sind
     * also von selbst an). Häkchen = an.
     */
    public static Setting dynamicMulti(String name, String desc, java.util.function.Supplier<List<String>> options) {
        Setting s = new Setting(name, desc, Type.MULTI, 0, false, "", 0, 0, List.of());
        s.dynamic = options;
        s.inverted = true;
        return s;
    }

    /** Text = ausgeschaltete Einträge (dynamicMulti). */
    public boolean inverted;

    /** Hat dieser Eintrag ein Häkchen? */
    public boolean isOn(String option) { return inverted != isSelected(option); }

    /** Knopf im Menü (z.B. "Seedmap öffnen"): kein Wert, Anklicken führt {@code run} aus. */
    public static Setting action(String name, String desc, String label, Runnable run) {
        Setting s = new Setting(name, desc, Type.ACTION, 0, false, label, 0, 0, List.of());
        s.action = run;
        return s;
    }

    /** Mehrfach-Auswahl (z.B. welche Erze): Wert = Komma-Liste. Eigene Einträge außerhalb der Liste bleiben erhalten. */
    public static Setting multi(String name, String desc, String value, String... options) {
        return new Setting(name, desc, Type.MULTI, 0, false, value, 0, 0, List.of(options));
    }

    /** Anzeigenamen für die Listen-Einträge setzen. */
    public Setting labels(java.util.function.Function<String, String> f) {
        optionLabel = f;
        return this;
    }

    public String optionLabel(String option) {
        if (optionLabel == null) return option;
        String l = optionLabel.apply(option);
        return l == null || l.isEmpty() ? option : l;
    }

    /** Ausgewählte Einträge (MULTI/TEXT als Liste). */
    public List<String> selected() {
        List<String> out = new java.util.ArrayList<>();
        if (text == null) return out;
        for (String p : text.split(",")) if (!p.isBlank()) out.add(p.trim().toLowerCase(Locale.ROOT));
        return out;
    }

    public boolean isSelected(String option) { return selected().contains(option.toLowerCase(Locale.ROOT)); }

    /** Eintrag an/aus (MULTI). */
    public void toggleOption(String option) {
        // gleiche Wirkung bei beiden Arten: Häkchen umdrehen
        List<String> sel = selected();
        String o = option.toLowerCase(Locale.ROOT);
        if (!sel.remove(o)) sel.add(o);
        text = String.join(",", sel);
    }

    public boolean allSelected() {
        for (String c : choiceList()) if (!isOn(c)) return false;
        return true;
    }

    /** Alle an – oder, wenn schon alle an sind, alle aus. */
    public void toggleAll() {
        List<String> sel = selected();
        List<String> all = new java.util.ArrayList<>();
        for (String c : choiceList()) all.add(c.toLowerCase(Locale.ROOT));
        boolean turnOff = allSelected();
        // normal: Text = angehakte; inverted: Text = ausgeschaltete
        boolean add = inverted == turnOff;
        if (add) { for (String c : all) if (!sel.contains(c)) sel.add(c); }
        else sel.removeAll(all);
        text = String.join(",", sel);
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
                for (String c : choiceList()) {
                    if (c.equalsIgnoreCase(v)) { setText(c); return null; }
                }
                // nur bei Live-Listen (Wegpunkte) einen Namen zulassen, der gerade nicht geladen ist
                if (dynamic != null) { setText(v); return null; }
                return "Möglich: " + String.join(", ", choices);
            }
            case TEXT -> setText(v);
            case MULTI -> setText(v.replace(" ", ","));
            case ACTION -> { if (action != null) action.run(); }
        }
        return null;
    }

    public String display() {
        return switch (type) {
            case NUMBER -> fmt(number);
            case BOOL -> bool ? "an" : "aus";
            case TEXT, CHOICE, ACTION -> text;
            case MULTI -> {
                List<String> list = choiceList();
                int on = 0;
                for (String c : list) if (isOn(c)) on++;
                if (list.isEmpty()) yield inverted ? "alle" : "keine";
                if (on == list.size()) yield "alle";
                yield on == 0 ? "keine" : on + " von " + list.size();
            }
        };
    }

    public static String fmt(double d) {
        if (d == Math.rint(d)) return String.valueOf((long) d);
        return String.format(Locale.ROOT, "%.2f", d);
    }
}
