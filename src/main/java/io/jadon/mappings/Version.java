package io.jadon.mappings;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Version {
    v1_14("1.14.4"),
    v1_15("1.15.2"),
    v1_16("1.16-pre5");

    private final String name;

    @Override
    public String toString() {
        return "Minecraft " + name;
    }
}
