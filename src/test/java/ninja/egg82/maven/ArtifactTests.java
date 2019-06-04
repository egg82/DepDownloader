package ninja.egg82.maven;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ArtifactTests {
    @Test
    public void testBuildArtifact() {
        Assertions.assertDoesNotThrow(() -> {
            Artifact.builder("ninja.egg82", "service-locator", "1.0.1")
                    .addRepository("https://nexus.egg82.me/repository/egg82/")
                    .addRepository("https://www.myget.org/F/egg82-java/maven/")
                    .addRepository("https://nexus.egg82.me/repository/maven-central/")
                    .build(Scope.values());
        });

        Assertions.assertDoesNotThrow(() -> {
            Artifact.builder("com.google.guava", "guava", "27.1-jre")
                    .addRepository("http://central.maven.org/maven2/")
                    .build(Scope.values());
        });
    }
}
