package drools;

import org.kie.api.builder.ReleaseId;

/**
 * "Inspired" from
 * https://github.com/kiegroup/drools/blob/00657c78d252951abbf8b541a7fc2417df8d6fb1/drools-model/drools-model-compiler/src/test/java/org/drools/modelcompiler/KJARUtils.java#L21
 *
 */
public class KJARUtils {
  public static String getPom(final ReleaseId releaseId) {
    final String pom =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
            + "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd\">\n" + "  <modelVersion>4.0.0</modelVersion>\n" + "\n"
            + "  <groupId>" + releaseId.getGroupId() + "</groupId>\n" + "  <artifactId>" + releaseId.getArtifactId() + "</artifactId>\n" + "  <version>" + releaseId.getVersion()
            + "</version>\n" + "</project>";
    return pom;
  }
}