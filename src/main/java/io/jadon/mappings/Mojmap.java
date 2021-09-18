package io.jadon.mappings;

import com.google.common.collect.Lists;
import io.jadon.mappings.yarn.YarnMappings;
import lombok.SneakyThrows;
import org.cadixdev.bombe.type.FieldType;
import org.cadixdev.bombe.type.signature.FieldSignature;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.io.MappingFormats;
import org.cadixdev.lorenz.model.FieldMapping;
import org.cadixdev.lorenz.model.InnerClassMapping;
import org.cadixdev.lorenz.model.MethodMapping;
import org.cadixdev.lorenz.model.TopLevelClassMapping;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

public class Mojmap {

    @SneakyThrows
    public static void main(String[] args) {
        File dir = new File("versions/");
//        VersionManager.downloadVersionFiles("1.15.2", dir);
//        VersionManager.downloadVersionFiles("20w22a", dir);


//        VersionManager.downloadVersionFiles("1.13.2", dir);
//        for (String version : Lists.newArrayList("1.7.10", "1.8.9", "1.9.4", "1.10.2", "1.11.2", "1.12.2")) {
//            VersionManager.downloadVersionFiles(version, dir);
//        }
//        VersionManager.diffVersions(dir, "1.16.1", "1.16.2-rc1");
//        VersionManager.diffVersions(dir, "1.16.2-rc1", "1.16.2-rc2");
//        VersionManager.diffVersions(dir, "1.15.2", "1.16.2-pre1");
//        generateLunarMappings(GameVersion.v1_16);
//        VersionManager.diffVersions(dir, "21w08b", "21w10a");
        VersionManager.diffVersions(dir, "1.17.1", "21w37a");
//        VersionManager.downloadVersionFiles("18w43a", dir);

//        MappingSet client15 = MappingFormats.SRG.read(Paths.get("versions/1.15.2/1.15.2_client.srg"));
//        MappingSet client16 = MappingFormats.SRG.read(Paths.get("versions/1.16-pre5/1.16-pre5_client.srg"));
//        MappingSet obf2obf = client15.merge(client16.reverse());
//        MappingFormats.TSRG.write(obf2obf, Paths.get("beta/15_to_16.tsrg"));
    }

    @SneakyThrows
    public static void generateLunarMappings(GameVersion gameVersion) {
        System.out.println("Building Lunar Client specific mappings for " + gameVersion.getName());
        File home = gameVersion.getVersionDir();
        // grab fabric's intermediary mappings
        MappingSet yarnMappings = YarnMappings.getIntermediaryMappings(gameVersion);
        MappingFormats.TSRG.write(yarnMappings, new File(home, "intermediary.tsrg").toPath());
        MappingSet mojangMappings = gameVersion.getMappings(Side.CLIENT);
        MappingSet yarn2mojang = removeFieldDescriptors(yarnMappings).reverse().merge(mojangMappings);
        MappingFormats.SRG.write(yarn2mojang, new File(home, "intermediary2mojang.srg").toPath());
        MappingFormats.SRG.write(yarn2mojang.reverse(), new File(home, "mojang2intermediary.srg").toPath());
    }

    /**
     * Removes the field descriptors from a MappingSet.
     *
     * @param mappingSet mapping set to filter
     * @return a new mapping set without field descriptors
     */
    public static MappingSet removeFieldDescriptors(MappingSet mappingSet) {
        MappingSet filtered = MappingSet.create();
        for (TopLevelClassMapping topLevelClassMapping : mappingSet.getTopLevelClassMappings()) {
            TopLevelClassMapping filteredTopLevelClassMapping = filtered.createTopLevelClassMapping(topLevelClassMapping.getFullObfuscatedName(), topLevelClassMapping.getFullDeobfuscatedName());
            for (FieldMapping fieldMapping : topLevelClassMapping.getFieldMappings()) {
                filteredTopLevelClassMapping.createFieldMapping(new FieldSignature(fieldMapping.getObfuscatedName()))
                        .setDeobfuscatedName(fieldMapping.getDeobfuscatedName());
            }
            for (MethodMapping methodMapping : topLevelClassMapping.getMethodMappings()) {
                filteredTopLevelClassMapping.createMethodMapping(methodMapping.getSignature(), methodMapping.getDeobfuscatedName());
            }
            for (InnerClassMapping innerClassMapping : topLevelClassMapping.getInnerClassMappings()) {
                InnerClassMapping filteredInnerClassMapping = filteredTopLevelClassMapping.createInnerClassMapping(innerClassMapping.getObfuscatedName(), innerClassMapping.getDeobfuscatedName());
                for (FieldMapping fieldMapping : innerClassMapping.getFieldMappings()) {
                    filteredInnerClassMapping.createFieldMapping(new FieldSignature(fieldMapping.getObfuscatedName()))
                            .setDeobfuscatedName(fieldMapping.getDeobfuscatedName());
                }
                for (MethodMapping methodMapping : innerClassMapping.getMethodMappings()) {
                    filteredInnerClassMapping.createMethodMapping(methodMapping.getSignature(), methodMapping.getDeobfuscatedName());
                }
            }
        }
        return filtered;
    }

    protected static void betaTesting() throws IOException {
        MappingFormats.TSRG.write(
                MappingFormats.TSRG.read(Paths.get("beta/serverToClientObf.tsrg")),
                Paths.get("beta/test.tsrg")
        );

        MappingSet client = MappingFormats.TSRG.read(Paths.get("beta/client.tsrg"));
        MappingSet clientToServer = MappingFormats.TSRG.read(Paths.get("beta/serverToClientObf.tsrg")).reverse();
        MappingFormats.TSRG.write(clientToServer, Paths.get("beta/clientToServerObf.tsrg"));
        MappingSet server = client.merge(clientToServer);
        MappingFormats.TSRG.read(server, Paths.get("beta/server.tsrg"));

        MappingFormats.TSRG.write(server, Paths.get("beta/full_server.tsrg"));
    }
}
