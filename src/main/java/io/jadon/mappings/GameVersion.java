package io.jadon.mappings;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.io.MappingFormats;

import java.io.File;

@Getter
public enum GameVersion {
    v1_14("1.14.4"),
    v1_15("1.15.2"),
    v1_16("1.16");

    public static final File VERSIONS_DIR = new File("versions/");

    private final String name;
    private final File versionDir;

    GameVersion(String name) {
        this.name = name;
        this.versionDir = new File("versions/" + name);
    }

    @SneakyThrows
    public MappingSet getMappings(Side side) {
        File file = new File(versionDir, name + "_" + side + ".srg");
        if (!file.exists()) {
            VersionManager.downloadVersionFiles(name, VERSIONS_DIR);
        }
        return MappingFormats.SRG.read(file.toPath());
    }

    @Override
    public String toString() {
        return "Minecraft " + name;
    }
}
