package ninja.egg82.maven;

import java.io.File;
import java.net.URISyntaxException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ArtifactTests {
    @Test
    public void testBuildArtifactComplex() {
        Assertions.assertDoesNotThrow(() -> {
            Artifact.builder("com.google.guava", "guava", "27.1-jre", new File(getCurrentDirectory(), "cache"))
                    .addRepository("http://central.maven.org/maven2/")
                    .build();
        });
    }

    @Test
    public void testBuildArtifactSimple() {
        Assertions.assertDoesNotThrow(() -> {
            Artifact.builder("ninja.egg82", "service-locator", "1.0.1", new File(getCurrentDirectory(), "cache"))
                    .addRepository("https://nexus.egg82.me/repository/egg82/")
                    .addRepository("https://www.myget.org/F/egg82-java/maven/")
                    .addRepository("https://nexus.egg82.me/repository/maven-central/")
                    .build();
        });
    }

    @Test
    public void testBuildArtifactsStandard() {
        Assertions.assertDoesNotThrow(() -> {
            Artifact webhooks = Artifact.builder("club.minnced", "discord-webhooks", "0.1.7", new File(getCurrentDirectory(), "cache"))
                    .addRepository("https://nexus.egg82.me/repository/bintray-jcenter/")
                    .addRepository("https://jcenter.bintray.com/")
                    .addRepository("https://nexus.egg82.me/repository/maven-central/")
                    .build();

            for (Artifact dep : webhooks.getDependencies()) {
                System.out.println("Dep: " + dep);
                for (Artifact d : dep.getDependencies()) {
                    System.out.println("Inner dep: " + d);
                }
            }
        });
    }

    @Test
    public void testGetSnapshot() {
        Assertions.assertDoesNotThrow(() -> {
            Artifact acfCore = Artifact.builder("co.aikar", "acf-core", "0.5.0-SNAPSHOT", new File(getCurrentDirectory(), "cache"))
                    .addRepository("https://nexus.egg82.me/repository/aikar/")
                    .addRepository("https://repo.aikar.co/nexus/content/groups/aikar/")
                    .addRepository("https://nexus.egg82.me/repository/maven-central/")
                    .build();

            System.out.println("ACF Core version: " + acfCore.getRealVersion());
        });
    }

    @Test
    public void testGetLatest() {
        Assertions.assertDoesNotThrow(() -> {
            Artifact taskchainCore = Artifact.builder("co.aikar", "taskchain-core", "latest", new File(getCurrentDirectory(), "cache"))
                    .addRepository("https://nexus.egg82.me/repository/aikar/")
                    .addRepository("https://repo.aikar.co/nexus/content/groups/aikar/")
                    .addRepository("https://nexus.egg82.me/repository/maven-central/")
                    .build();

            System.out.println("Taskchain version: " + taskchainCore.getRealVersion());
        });
    }

    @Test
    public void testGetRelease() {
        Assertions.assertDoesNotThrow(() -> {
            Artifact taskchainCore = Artifact.builder("co.aikar", "taskchain-core", "release", new File(getCurrentDirectory(), "cache"))
                    .addRepository("https://nexus.egg82.me/repository/aikar/")
                    .addRepository("https://repo.aikar.co/nexus/content/groups/aikar/")
                    .addRepository("https://nexus.egg82.me/repository/maven-central/")
                    .build();

            System.out.println("Taskchain version: " + taskchainCore.getRealVersion());
        });
    }

    private File getCurrentDirectory() throws URISyntaxException {
        return new File(getClass().getProtectionDomain().getCodeSource().getLocation().toURI()).getParentFile();
    }
}
