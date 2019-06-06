package ninja.egg82.maven;

import java.io.File;
import java.net.URISyntaxException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ArtifactTests {
    @Test
    public void testBuildArtifactInfiniteAll() {
        Assertions.assertDoesNotThrow(() -> {
            Artifact.builder("com.google.guava", "guava", "27.1-jre")
                    .addRepository("http://central.maven.org/maven2/")
                    .build(new File(getCurrentDirectory(), "cache"), -1, Scope.values());
        });
    }

    @Test
    public void testBuildArtifactSingle() {
        Assertions.assertDoesNotThrow(() -> {
            Artifact.builder("ninja.egg82", "service-locator", "1.0.1")
                    .addRepository("https://nexus.egg82.me/repository/egg82/")
                    .addRepository("https://www.myget.org/F/egg82-java/maven/")
                    .addRepository("https://nexus.egg82.me/repository/maven-central/")
                    .build(new File(getCurrentDirectory(), "cache"), 0);
        });
    }

    @Test
    public void testBuildArtifactStandard() {
        Assertions.assertDoesNotThrow(() -> {
            Artifact webhooks = Artifact.builder("club.minnced", "discord-webhooks", "0.1.7")
                    .addRepository("https://nexus.egg82.me/repository/bintray-jcenter/")
                    .addRepository("https://jcenter.bintray.com/")
                    .addRepository("https://nexus.egg82.me/repository/maven-central/")
                    .build(new File(getCurrentDirectory(), "cache"), 1);

            Artifact okio = Artifact.builder("com.squareup.okio", "okio", "2.2.2")
                    .addRepository("https://nexus.egg82.me/repository/maven-central/")
                    .build(new File(getCurrentDirectory(), "cache"), 2);
        });
    }

    private File getCurrentDirectory() throws URISyntaxException {
        return new File(getClass().getProtectionDomain().getCodeSource().getLocation().toURI()).getParentFile();
    }
}
