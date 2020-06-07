package io.jadon.mappings;

import lombok.SneakyThrows;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.io.MappingFormats;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

public class MappingGenerator {

    @SneakyThrows
    public static void main(String[] args) {
        File dir = new File("versions/");
//        VersionManager.downloadVersionFiles("1.15.2", dir);
//        VersionManager.downloadVersionFiles("20w22a", dir);

        VersionManager.diffVersions(dir, "1.16-pre5", "1.16-pre7");

//        MappingSet client15 = MappingFormats.SRG.read(Paths.get("versions/1.15.2/1.15.2_client.srg"));
//        MappingSet client16 = MappingFormats.SRG.read(Paths.get("versions/1.16-pre5/1.16-pre5_client.srg"));
//        MappingSet obf2obf = client15.merge(client16.reverse());
//        MappingFormats.TSRG.write(obf2obf, Paths.get("beta/15_to_16.tsrg"));
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
