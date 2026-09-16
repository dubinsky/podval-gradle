package org.podval.conventions;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConventionsPluginTest {
  @TempDir
  Path projectDir;

  @Test
  void settingsAppliesFoojayAndMavenCentral() throws IOException {
    writeSettings(
      """
      plugins {
        id 'org.podval.conventions.settings'
      }
      assert pluginManager.hasPlugin('org.gradle.toolchains.foojay-resolver-convention')
      assert pluginManager.hasPlugin('org.podval.conventions.settings')
      assert !pluginManager.hasPlugin('com.gradleup.nmcp.settings')
      assert dependencyResolutionManagement.repositories.size() > 0
      rootProject.name = 'settings-test'
      """
    );
    writeBuild("plugins { id 'org.podval.conventions' }\n");
    BuildResult result = runner("help").build();
    assertTrue(result.getOutput().contains("BUILD SUCCESSFUL"), result.getOutput());
  }

  @Test
  void javaToolchain25AndVersionsTask() throws IOException {
    writeSettingsWithConventions();
    writeBuild(
      """
      plugins {
        id 'java-library'
        id 'org.podval.conventions'
      }
      tasks.register('assertConventions') {
        doLast {
          assert java.toolchain.languageVersion.get() == JavaLanguageVersion.of(25)
          assert tasks.findByName('dependencyUpdates') != null
          assert !gradle.startParameter.excludedTaskNames.contains('compileJava')
          assert !pluginManager.hasPlugin('scala')
        }
      }
      """
    );
    BuildResult result = runner("assertConventions").build();
    assertNotNull(result.task(":assertConventions"));
    assertEquals(TaskOutcome.SUCCESS, result.task(":assertConventions").getOutcome());
  }

  @Test
  void scalaFlagsWhenScalaPresent() throws IOException {
    writeSettingsWithConventions();
    writeBuild(
      """
      plugins {
        id 'scala'
        id 'org.podval.conventions'
      }
      scala.scalaVersion = '3.9.0'
      tasks.register('assertScala') {
        doLast {
          assert java.toolchain.languageVersion.get() == JavaLanguageVersion.of(25)
          def params = tasks.named('compileScala').get().scalaCompileOptions.additionalParameters
          assert params.contains('-release:25')
          assert params.contains('-new-syntax')
          assert params.contains('-feature')
          assert params.contains('-language:strictEquality')
          assert params.contains('-source:future')
        }
      }
      """
    );
    BuildResult result = runner("assertScala").build();
    assertEquals(TaskOutcome.SUCCESS, result.task(":assertScala").getOutcome());
  }

  @Test
  void conventionsWithoutJavaDoesNotApplyJava() throws IOException {
    writeSettingsWithConventions();
    writeBuild(
      """
      plugins {
        id 'org.podval.conventions'
      }
      tasks.register('assertNoJava') {
        doLast {
          assert !pluginManager.hasPlugin('java')
          assert !pluginManager.hasPlugin('scala')
          assert tasks.findByName('dependencyUpdates') != null
        }
      }
      """
    );
    BuildResult result = runner("assertNoJava").build();
    assertEquals(TaskOutcome.SUCCESS, result.task(":assertNoJava").getOutcome());
  }

  @Test
  void publishPomFromExtension() throws IOException {
    writeSettingsWithConventions();
    writeBuild(
      """
      plugins {
        id 'java-library'
        id 'maven-publish'
        id 'signing'
        id 'org.podval.conventions'
        id 'org.podval.conventions.publish'
      }
      description = 'Test library'
      podvalPublish {
        gitHubRepository = 'dubinsky/xml'
        name = 'Podval XML'
        inceptionYear = '2026'
      }
      """
    );
    BuildResult result = runner("generatePomFileForLibraryPublication").build();
    assertEquals(TaskOutcome.SUCCESS, result.task(":generatePomFileForLibraryPublication").getOutcome());
    String pom = Files.readString(projectDir.resolve("build/publications/library/pom-default.xml"));
    assertTrue(pom.contains("<name>Podval XML</name>"), pom);
    assertTrue(pom.contains("<url>https://github.com/dubinsky/xml</url>"), pom);
    assertTrue(pom.contains("<inceptionYear>2026</inceptionYear>"), pom);
    assertTrue(pom.contains("<email>dub@podval.org</email>"), pom);
    assertTrue(pom.contains("<name>Podval Group</name>"), pom);
    assertTrue(pom.contains("<url>https://www.podval.org</url>"), pom);
    assertTrue(pom.contains("Apache-2.0"), pom);
    assertTrue(pom.contains("scm:git:https://github.com/dubinsky/xml.git"), pom);
  }

  @Test
  void publishExtensionOverridesForOpenTorah() throws IOException {
    writeSettingsWithConventions();
    writeBuild(
      """
      plugins {
        id 'java-library'
        id 'maven-publish'
        id 'org.podval.conventions.publish'
      }
      podvalPublish {
        gitHubRepository = 'opentorah/opentorah'
        orgName = 'Open Torah Project'
        orgUrl = 'https://www.opentorah.org'
        developerEmail = 'dub@opentorah.org'
        inceptionYear = '2018'
      }
      """
    );
    runner("generatePomFileForLibraryPublication").build();
    String pom = Files.readString(projectDir.resolve("build/publications/library/pom-default.xml"));
    assertTrue(pom.contains("<email>dub@opentorah.org</email>"), pom);
    assertTrue(pom.contains("<name>Open Torah Project</name>"), pom);
    assertTrue(pom.contains("<url>https://www.opentorah.org</url>"), pom);
    assertFalse(pom.contains("dub@podval.org"), pom);
  }

  @Test
  void publishRequiresGitHubRepository() throws IOException {
    writeSettingsWithConventions();
    writeBuild(
      """
      plugins {
        id 'java-library'
        id 'maven-publish'
        id 'org.podval.conventions.publish'
      }
      """
    );
    BuildResult result = runner("generatePomFileForLibraryPublication").buildAndFail();
    assertTrue(
      result.getOutput().contains("podvalPublish.gitHubRepository is required"),
      result.getOutput()
    );
  }

  @Test
  void scalaJavadocJarBeforeWithJavadocJar() throws IOException {
    writeSettingsWithConventions();
    writeBuild(
      """
      plugins {
        id 'java-library'
        id 'scala'
        id 'maven-publish'
        id 'org.podval.conventions'
        id 'org.podval.conventions.publish'
      }
      scala.scalaVersion = '3.9.0'
      podvalPublish {
        gitHubRepository = 'dubinsky/xml'
      }
      tasks.register('assertJavadocJar') {
        doLast {
          def jar = tasks.named('javadocJar', Jar).get()
          assert jar.archiveClassifier.get() == 'javadoc'
          assert jar.taskDependencies.getDependencies(jar).any { it.name == 'scaladoc' }
        }
      }
      """
    );
    BuildResult result = runner("assertJavadocJar").build();
    assertEquals(TaskOutcome.SUCCESS, result.task(":assertJavadocJar").getOutcome());
  }

  @Test
  void configurationCacheOnHelp() throws IOException {
    writeSettingsWithConventions();
    writeBuild(
      """
      plugins {
        id 'java-library'
        id 'maven-publish'
        id 'signing'
        id 'org.podval.conventions'
        id 'org.podval.conventions.publish'
      }
      podvalPublish {
        gitHubRepository = 'dubinsky/xml'
      }
      """
    );
    BuildResult result = runner("help", "--configuration-cache").build();
    assertTrue(
      result.getOutput().contains("Configuration cache")
        || result.getOutput().contains("Reusing configuration cache"),
      result.getOutput()
    );
  }

  private void writeSettingsWithConventions() throws IOException {
    writeSettings(
      """
      plugins {
        id 'org.podval.conventions.settings'
      }
      rootProject.name = 'conventions-test'
      """
    );
  }

  private void writeSettings(String settingsGradle) throws IOException {
    Files.writeString(
      projectDir.resolve("settings.gradle"),
      settingsGradle,
      StandardCharsets.UTF_8
    );
  }

  private void writeBuild(String buildGradle) throws IOException {
    Files.writeString(projectDir.resolve("build.gradle"), buildGradle, StandardCharsets.UTF_8);
  }

  private GradleRunner runner(String... arguments) {
    return GradleRunner.create()
      .withProjectDir(projectDir.toFile())
      .withPluginClasspath()
      .withArguments(arguments)
      .forwardOutput();
  }
}
