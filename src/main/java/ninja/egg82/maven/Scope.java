package ninja.egg82.maven;

public enum Scope {
    COMPILE("compile"),
    PROVIDED("provided"),
    RUNTIME("runtime"),
    TEST("test"),
    SYSTEM("system"),
    IMPORT("import");

    private String name;
    public String getName() { return name; }

    Scope(String name) {
        this.name = name;
    }

    public static Scope fromName(String name) {
        if (name == null || name.isEmpty()) {
            return COMPILE;
        }

        for (Scope s : values()) {
            if (s.name.equalsIgnoreCase(name)) {
                return s;
            }
        }

        return COMPILE;
    }
}
