package com.prisoncore.rank;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public enum Rank {
    ADMIN("Admin", "[Admin] ", "§d[Admin] ", NamedTextColor.LIGHT_PURPLE),
    GUARD("Guard", "[Guard] ", "§9[Guard] ", NamedTextColor.BLUE),
    PCI("PCI", "[PCI] ", "§c[PCI] ", NamedTextColor.RED),
    PRISONER("Prisoner", "[Prisoner] ", "§6[Prisoner] ", NamedTextColor.GOLD);

    private final String name;
    private final String rawPrefix;
    private final String legacyPrefix;
    private final NamedTextColor color;

    Rank(String name, String rawPrefix, String legacyPrefix, NamedTextColor color) {
        this.name = name;
        this.rawPrefix = rawPrefix;
        this.legacyPrefix = legacyPrefix;
        this.color = color;
    }

    public String getName() {
        return name;
    }

    public String getRawPrefix() {
        return rawPrefix;
    }

    public String getLegacyPrefix() {
        return legacyPrefix;
    }

    public NamedTextColor getColor() {
        return color;
    }

    public Component getPrefixComponent() {
        return Component.text(rawPrefix, color, TextDecoration.BOLD);
    }

    public static Rank fromName(String name) {
        for (Rank r : values()) {
            if (r.getName().equalsIgnoreCase(name)) {
                return r;
            }
        }
        return null;
    }
}
