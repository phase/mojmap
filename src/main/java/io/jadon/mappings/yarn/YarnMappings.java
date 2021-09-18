package io.jadon.mappings.yarn;

import com.google.gson.JsonParser;
import io.jadon.mappings.ByteUtil;
import io.jadon.mappings.GameVersion;
import lombok.SneakyThrows;
import net.fabricmc.lorenztiny.TinyMappingFormat;
import org.cadixdev.lorenz.MappingSet;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class YarnMappings {

    public static final String VERSIONS_URL = "https://meta.fabricmc.net/v1/versions/mappings/";
    public static final String MAPPINGS_URL = "https://maven.fabricmc.net/net/fabricmc/intermediary/%s/intermediary-%s.jar";

    @SneakyThrows
    public static String getYarnVersion(GameVersion gameVersion) {
        URL url = new URL(VERSIONS_URL + gameVersion.getName());
        InputStreamReader reader = new InputStreamReader(url.openStream());
        return JsonParser.parseReader(reader).getAsJsonArray().get(0).getAsJsonObject().get("version").getAsString();
    }

    @SneakyThrows
    public static MappingSet getIntermediaryMappings(GameVersion gameVersion) {
        File cache = gameVersion.getVersionDir();
        cache.mkdirs();
        File jarFile = new File(cache, "/intermediary-" + gameVersion.getName() + ".jar");

        if (!jarFile.exists()) {
            URL url = new URL(MAPPINGS_URL.replaceAll("%s", gameVersion.getName()));
            ReadableByteChannel readChannel = Channels.newChannel(url.openStream());
            FileOutputStream output = new FileOutputStream(jarFile);
            FileChannel writeChannel = output.getChannel();
            writeChannel.transferFrom(readChannel, 0, Long.MAX_VALUE);
        }

        File tinyFile = new File(cache, "/intermediary-" + gameVersion.getName() + ".tiny");

        if (!tinyFile.exists()) {
            JarFile jar = new JarFile(jarFile);
            final Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                final JarEntry entry = entries.nextElement();
                String entryName = entry.getName();
                if (entry.getName().contains("mappings.tiny")) {
                    byte[] bytes = ByteUtil.toByteArray(jar.getInputStream(entry));
                    Files.write(tinyFile.toPath(), bytes);
                    break;
                }
            }
        }

        return TinyMappingFormat.LEGACY.createReader(tinyFile.toPath(), "official", "intermediary").read();
    }

}
