package io.jadon.mappings;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.SneakyThrows;
import net.md_5.specialsource.Jar;
import net.md_5.specialsource.JarMapping;
import net.md_5.specialsource.JarRemapper;
import net.md_5.specialsource.provider.JarProvider;
import net.md_5.specialsource.provider.JointProvider;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.io.MappingFormats;
import org.cadixdev.lorenz.io.proguard.ProGuardFormat;
import org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Creates diffs between versions
 *
 * @author phase
 */
public class VersionManager {

    private static final List<String> PACKAGE_FILTERS = Arrays.asList("/net/minecraft", "/com/mojang");

    public static void diffVersions(File dir, String versionA, String versionB) {
        File versionADir = new File(dir, versionA);
        File versionBDir = new File(dir, versionB);

        if (!versionADir.exists()) {
            downloadVersionFiles(versionA, dir);
        }
        if (!versionBDir.exists()) {
            downloadVersionFiles(versionB, dir);
        }

        PatchCollection clientPatchCollection = PatchCollection.empty();
        PatchCollection serverPatchCollection = PatchCollection.empty();

        for (String packageFilter : PACKAGE_FILTERS) {
            File versionAClientDir = new File(versionADir, "/decomp/client/" + packageFilter);
            File versionAServerDir = new File(versionADir, "/decomp/server/" + packageFilter);
            File versionBClientDir = new File(versionBDir, "/decomp/client/" + packageFilter);
            File versionBServerDir = new File(versionBDir, "/decomp/server/" + packageFilter);

            // create the patches
            System.out.println("Creating client patches for " + packageFilter);
            clientPatchCollection.merge(createPatches(versionAClientDir, versionBClientDir));
            System.out.println("Creating server patches for " + packageFilter);
            serverPatchCollection.merge(createPatches(versionAServerDir, versionBServerDir));
        }

        // mkdirs
        File patchDir = new File(dir, versionA + "_to_" + versionB);
        File clientPatchDir = new File(patchDir, "client");
        File serverPatchDir = new File(patchDir, "server");

        System.out.println("Writing client patches to " + clientPatchDir.getPath());
        clientPatchCollection.writeToDir(clientPatchDir);
        System.out.println("Writing server patches to " + serverPatchDir.getPath());
        serverPatchCollection.writeToDir(serverPatchDir);
    }

    public static PatchCollection createPatches(File dirA, File dirB) {
        System.out.println("comparing " + dirA.getPath() + " & " + dirB.getPath());
        HashSet<String> fileNames = new HashSet<>();
        List<FileContents> filesA = readSourceFiles(dirA);
        List<FileContents> filesB = readSourceFiles(dirB);

        // add all the file names to the set
        for (FileContents fileContents : filesA) {
            fileNames.add(fileContents.name);
        }
        for (FileContents fileContents : filesB) {
            fileNames.add(fileContents.name);
        }

        List<String> fileChanges = new ArrayList<>();
        List<PatchedFile> patchedFiles = new ArrayList<>();
        for (String fileName : fileNames) {
            // get the correct file contents for each version
            // can be null if a file was added or removed
            FileContents fileContentsA = filesA.stream().filter((s) -> s.name.equals(fileName)).findFirst().orElse(null);
            FileContents fileContentsB = filesB.stream().filter((s) -> s.name.equals(fileName)).findFirst().orElse(null);
            List<String> diff = FileContents.diff(fileContentsA, fileContentsB);

            // skip empty diffs
            if (diff.isEmpty()) {
                continue;
            }

            String mark = "*";
            if (fileContentsA == null) {
                mark = "+";
            } else if (fileContentsB == null) {
                mark = "-";
            }
            fileChanges.add(mark + " " + fileName);

            patchedFiles.add(new PatchedFile(fileName, diff));
        }
        Collections.sort(fileChanges);
        StringBuilder report = new StringBuilder();
        for (String fileChange : fileChanges) {
            report.append(fileChange).append('\n');
        }
        return new PatchCollection(report.toString(), patchedFiles);
    }

    @SneakyThrows
    public static List<FileContents> readSourceFiles(File dir) {
        List<FileContents> files = new ArrayList<>();
        List<Path> paths = new ArrayList<>();
        collectFiles(dir.toPath(), paths);
        for (Path path : paths) {
            String fileName = path.toFile().getPath();
            // remove useless directories
            boolean foundPackage = false;
            for (String packageFilter : PACKAGE_FILTERS) {
                if (fileName.contains(packageFilter)) {
                    fileName = fileName.substring(fileName.indexOf(packageFilter) + 1);
                    foundPackage = true;
                    break;
                }
            }

            // skip if we this class' package wasn't in the filter
            if (!foundPackage) {
                continue;
            }

            try {
                List<String> lines = Files.readAllLines(path);
                FileContents fileContents = new FileContents(fileName, lines);
                files.add(fileContents);
            } catch (Exception e) {
                System.err.println(path);
                throw e;
            }
        }
        return files;
    }

    @Data
    @AllArgsConstructor
    public static class PatchCollection {
        private String report;
        private final List<PatchedFile> patchedFiles;

        public static PatchCollection empty() {
            return new PatchCollection("", new ArrayList<>());
        }

        @SneakyThrows
        public void writeToDir(File dir) {
            dir.mkdirs();
            Files.write(new File(dir, "report.txt").toPath(), report.getBytes());

            for (PatchedFile patchedFile : patchedFiles) {
                File patchFile = new File(dir, patchedFile.name + ".patch");
                patchFile.getParentFile().mkdirs();
                Files.write(patchFile.toPath(), patchedFile.diff);
            }
        }

        /**
         * Merges two PatchCollections together by modifying the current one.
         *
         * @param collection The collection to merge into this collection.
         */
        public void merge(PatchCollection collection) {
            this.patchedFiles.addAll(collection.patchedFiles);
            List<String> lines = Arrays.asList((this.report + collection.report).split("\n"));
            Collections.sort(lines);
            // rebuild report
            StringBuilder report = new StringBuilder();
            for (String fileChange : lines) {
                report.append(fileChange).append('\n');
            }
            this.report = report.toString();
        }
    }

    @Data
    public static class PatchedFile {
        private final String name;
        private final List<String> diff;
    }

    @Data
    public static class FileContents {
        private final String name;
        private final List<String> contents;

        /**
         * Generate a diff between two FileContents.
         *
         * @param a before version, can be null
         * @param b after version, can be null
         * @return lines of the diff between versions
         */
        @SneakyThrows
        public static List<String> diff(FileContents a, FileContents b) {
            Patch<String> patch;
            // create the patch
            if (a == null) {
                patch = DiffUtils.diff(new ArrayList<>(), b.contents);
            } else if (b == null) {
                patch = DiffUtils.diff(a.contents, new ArrayList<>());
            } else {
                patch = DiffUtils.diff(a.contents, b.contents);
            }
            // generate the diff
            List<String> originalLines = a == null ? new ArrayList<>() : a.contents;
            String name = a == null ? b.name : a.name;
            return UnifiedDiffUtils.generateUnifiedDiff(name, name, originalLines, patch, 5);
        }
    }

    /**
     * Reads mapping and jar urls from the client.json and downloads, remaps, & decompiles the client & server.
     *
     * @param version Minecraft version
     * @param dir     directory to dump everything
     */
    @SneakyThrows
    public static void downloadVersionFiles(String version, File dir) {
        File home = new File(dir, version);
        home.mkdirs();
        VersionData versionData = downloadVersionData(version, home);

        System.out.println("Writing " + version + " client srg mappings");
        File clientMappingsFile = new File(home, version + "_client.srg");
        PrintWriter clientWriter = new PrintWriter(new FileWriter(clientMappingsFile));
        MappingFormats.SRG.createWriter(clientWriter).write(versionData.getClientMappings());
        clientWriter.close();

        System.out.println("Writing " + version + " server srg mappings");
        File serverMappingsFile = new File(home, version + "_server.srg");
        PrintWriter serverWriter = new PrintWriter(new FileWriter(serverMappingsFile));
        MappingFormats.SRG.createWriter(serverWriter).write(versionData.getServerMappings());
        serverWriter.close();

        System.out.println("Remapping client jar");
        File clientRemappedJarFile = new File(home, version + "_client_remapped.jar");
        remapJar(
                versionData.getClientJar().getAbsolutePath(),
                clientRemappedJarFile.getAbsolutePath(),
                clientMappingsFile.getAbsolutePath()
        );

        System.out.println("Remapping server jar");
        File serverRemappedJarFile = new File(home, version + "_server_remapped.jar");
        remapJar(
                versionData.getServerJar().getAbsolutePath(),
                serverRemappedJarFile.getAbsolutePath(),
                serverMappingsFile.getAbsolutePath()
        );

        // setup decomp folders
        File decomp = new File(home, "decomp");
        File decompClient = new File(decomp, "client");
        File decompServer = new File(decomp, "server");
        decompClient.mkdirs();
        decompServer.mkdirs();

        decompile(clientRemappedJarFile, decompClient);
        decompile(serverRemappedJarFile, decompServer);

        PatchCollection serverToClientPatchCollection = createPatches(decompServer, decompClient);
        serverToClientPatchCollection.writeToDir(new File(decomp, "server_to_client"));
    }

    /**
     * Decompile a jar into a destination folder using FernFlower.
     *
     * @param jar         jar to decompile
     * @param destination place to dump files
     * @see org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences for the Fernflower args
     */
    public static void decompile(File jar, File destination) {
        if (!destination.exists() || destination.listFiles().length == 0) {
            System.out.println("Decompiling " + jar.getPath() + " to " + destination.getPath());
            ConsoleDecompiler.main(new String[]{
                    "-din=1", // decompile inner classes
                    "-rbr=0", // don't remove bridge
                    "-dgs=1", // decompile generic signatures
                    "-asc=1", // keep names ascii
                    "-hdc=0", // don't hide default constructor
                    "-rsy=1", // remove synthetic
                    "-iec=1", // include entire classpath
                    "-udv=0", // don't use debug names since they're obfuscated
                    "-jvn=1", // use jad var naming
                    "-log=WARN", // TRACE for everything, INFO for class names, WARN for warnings, ERROR for errors
                    jar.getAbsolutePath(), destination.getAbsolutePath()
            });
            System.out.println("Unzipping " + jar.getPath());
            File destinationZip = new File(destination, jar.getName());
            unzip(destinationZip, destination.toPath());
        }
    }

    /**
     * Unzip a jar into a directory
     *
     * @param jar       jar to unzip
     * @param targetDir directory to unzip into
     */
    @SneakyThrows
    public static void unzip(File jar, Path targetDir) {
        try (ZipInputStream zipIn = new ZipInputStream(new FileInputStream(jar))) {
            for (ZipEntry ze; (ze = zipIn.getNextEntry()) != null; ) {
                Path resolvedPath = targetDir.resolve(ze.getName());
                if (ze.isDirectory()) {
                    Files.createDirectories(resolvedPath);
                } else {
                    Files.createDirectories(resolvedPath.getParent());
                    Files.copy(zipIn, resolvedPath);
                }
            }
        }
    }

    /**
     * Remap a jar
     *
     * @param vanillaFile jar to map
     * @param mappedFile  destination of the remapped jar
     * @param srgFile     srg mappings to use
     */
    @SneakyThrows
    public static void remapJar(String vanillaFile, String mappedFile, String srgFile) {
        File destination = new File(mappedFile);
        if (destination.exists()) return;
        destination.getParentFile().mkdirs();

        JarMapping jarMapping = new JarMapping();
        jarMapping.loadMappings(srgFile, false, false, null, null);

        JointProvider inheritanceProviders = new JointProvider();
        jarMapping.setFallbackInheritanceProvider(inheritanceProviders);

        Jar jar = Jar.init(new File(vanillaFile));

        inheritanceProviders.add(new JarProvider(jar));
        JarRemapper jarRemapper = new JarRemapper(jarMapping);
        jarRemapper.remapJar(jar, destination);
    }

    @SneakyThrows
    public static VersionData downloadVersionData(String version, File dir) {
        File versionJsonFile = new File(getMinecraftFolder(), "/versions/" + version + "/" + version + ".json");

        JsonObject versionJson = new JsonParser().parse(new FileReader(versionJsonFile)).getAsJsonObject();
        JsonObject downloads = versionJson.getAsJsonObject("downloads");
        URL clientJarUrl = new URL(downloads.getAsJsonObject("client").get("url").getAsString());
        URL serverJarUrl = new URL(downloads.getAsJsonObject("server").get("url").getAsString());
        URL clientMappingUrl = new URL(downloads.getAsJsonObject("client_mappings").get("url").getAsString());
        URL serverMappingUrl = new URL(downloads.getAsJsonObject("server_mappings").get("url").getAsString());

        System.out.println("Download " + version + " client jar");
        File clientJarFile = new File(dir, version + "_client.jar");
        downloadJar(clientJarUrl, clientJarFile);
        System.out.println("Download " + version + " server jar");
        File serverJarFile = new File(dir, version + "_server.jar");
        downloadJar(serverJarUrl, serverJarFile);
        return new VersionData(downloadMappings(clientMappingUrl), downloadMappings(serverMappingUrl), clientJarFile, serverJarFile);
    }

    /**
     * Data downloaded from the URLs in the client.json
     *
     * @author phase
     */
    @Data
    public static class VersionData {
        private final MappingSet clientMappings;
        private final MappingSet serverMappings;
        private final File clientJar;
        private final File serverJar;
    }

    @SneakyThrows
    public static void downloadJar(URL url, File file) {
        if (file.exists()) return;
        try (BufferedInputStream in = new BufferedInputStream(url.openStream());
             FileOutputStream fileOutputStream = new FileOutputStream(file)) {
            byte dataBuffer[] = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                fileOutputStream.write(dataBuffer, 0, bytesRead);
            }
        }
    }

    @SneakyThrows
    public static MappingSet downloadMappings(URL url) {
        System.out.println("Downloading mappings from " + url.toString());
        URLConnection conn = url.openConnection();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            return new ProGuardFormat().createReader(reader).read().reverse();
        }
    }

    /**
     * @return .minecraft folder for the current OS
     */
    public static File getMinecraftFolder() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return new File(new File(System.getenv("APPDATA")), ".minecraft");
        } else if (os.contains("mac")) {
            return new File(new File(System.getProperty("user.home")), "Library/Application Support/minecraft");
        } else if (os.contains("linux")) {
            return new File(new File(System.getProperty("user.home")), ".minecraft/");
        } else {
            throw new RuntimeException("Failed to determine Minecraft directory for OS: " + os);
        }
    }

    /**
     * Recursively get all the files in a directory
     *
     * @param directory directory to recurse
     * @param all       accumulator
     */
    @SneakyThrows
    public static void collectFiles(Path directory, Collection<Path> all) {
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(directory)) {
            for (Path child : ds) {
                if (Files.isDirectory(child)) {
                    collectFiles(child, all);
                } else {
                    all.add(child);
                }
            }
        }
    }

}
