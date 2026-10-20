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
import java.util.jar.JarFile;
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
      assert pluginManager.hasPlugin('io.github.ben-manes.versions.settings')
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
  void centralPortalWhenPodvalCentralIsTrue() throws IOException {
    Files.writeString(
      projectDir.resolve("gradle.properties"),
      """
      podvalCentral=true
      mavenCentralUsername=the-user
      mavenCentralPassword=the-password
      """,
      StandardCharsets.UTF_8
    );
    writeSettings(
      """
      plugins {
        id 'org.podval.conventions.settings'
      }
      def portal = [:]
      nmcpSettings.centralPortal { options ->
        portal.type = options.publishingType.get()
        portal.username = options.username.get()
        portal.password = options.password.get()
      }
      assert portal.type == 'USER_MANAGED'
      assert portal.username == 'the-user'
      assert portal.password == 'the-password'
      rootProject.name = 'central-test'
      """
    );
    writeBuild(
      """
      plugins {
        id 'java-library'
        id 'org.podval.conventions.publish'
      }
      podvalPublish {
        gitHubRepository = 'dubinsky/xml'
      }
      assert pluginManager.hasPlugin('com.gradleup.nmcp.aggregation')
      assert pluginManager.hasPlugin('com.gradleup.nmcp')
      """
    );
    BuildResult result = runner(
      "help",
      "--configuration-cache",
      "-PmavenCentralUsername=the-user",
      "-PmavenCentralPassword=the-password"
    ).build();
    assertTrue(result.getOutput().contains("BUILD SUCCESSFUL"), result.getOutput());
  }

  @Test
  void centralPortalStaysOffUnlessPropertyIsTrue() throws IOException {
    Files.writeString(
      projectDir.resolve("gradle.properties"),
      "podvalCentral=false\n",
      StandardCharsets.UTF_8
    );
    writeSettings(
      """
      plugins {
        id 'org.podval.conventions.settings'
      }
      assert !pluginManager.hasPlugin('com.gradleup.nmcp.settings')
      rootProject.name = 'central-off'
      """
    );
    writeBuild("plugins { id 'org.podval.conventions' }\n");
    BuildResult result = runner("help").build();
    assertTrue(result.getOutput().contains("BUILD SUCCESSFUL"), result.getOutput());
  }

  @Test
  void versionsReportIsRootOnly() throws IOException {
    writeSettings(
      """
      plugins {
        id 'org.podval.conventions.settings'
      }
      include 'lib'
      rootProject.name = 'versions-layout'
      """
    );
    writeBuild(
      """
      plugins {
        id 'org.podval.conventions'
      }
      gradle.projectsEvaluated {
        assert rootProject.tasks.names.contains('dependencyUpdates')
        def lib = rootProject.project(':lib')
        assert !lib.tasks.names.contains('dependencyUpdates')
        assert lib.tasks.names.contains('partialDependencyUpdates')
      }
      """
    );
    Files.createDirectories(projectDir.resolve("lib"));
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
  void publishAppliesMavenPublishAndSigning() throws IOException {
    writeSettingsWithConventions();
    writeBuild(
      """
      plugins {
        id 'java-library'
        id 'org.podval.conventions.publish'
      }
      podvalPublish {
        gitHubRepository = 'dubinsky/xml'
      }
      tasks.register('assertPublishPlugins') {
        doLast {
          assert pluginManager.hasPlugin('maven-publish')
          assert pluginManager.hasPlugin('signing')
          assert publishing.publications.findByName('library') != null
        }
      }
      """
    );
    BuildResult result = runner("assertPublishPlugins").build();
    assertEquals(TaskOutcome.SUCCESS, result.task(":assertPublishPlugins").getOutcome());
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
  void scalaVersionFromGradleProperties() throws IOException {
    writeSettingsWithConventions();
    Files.writeString(
      projectDir.resolve("gradle.properties"),
      "scalaVersion=3.9.0\n",
      StandardCharsets.UTF_8
    );
    writeBuild(
      """
      plugins {
        id 'scala'
        id 'org.podval.conventions'
      }
      tasks.register('assertScalaVersion') {
        doLast {
          assert scala.scalaVersion.get() == '3.9.0'
        }
      }
      """
    );
    BuildResult result = runner("assertScalaVersion").build();
    assertEquals(TaskOutcome.SUCCESS, result.task(":assertScalaVersion").getOutcome());
  }

  @Test
  void pluginPublishOrderFailureWhenLibraryAlreadyCreated() throws IOException {
    writeSettingsWithConventions();
    writeBuild(
      """
      plugins {
        id 'java-library'
        id 'org.podval.conventions.publish'
        id 'java-gradle-plugin'
      }
      podvalPublish {
        gitHubRepository = 'dubinsky/scalajs-gradle'
      }
      gradlePlugin {
        plugins {
          dummy {
            id = 'conventions.test.dummy'
            implementationClass = 'org.gradle.api.plugins.JavaLibraryPlugin'
          }
        }
      }
      """
    );
    BuildResult result = runner("help").buildAndFail();
    assertTrue(
      result.getOutput().contains(
        "Apply java-gradle-plugin or com.gradle.plugin-publish before org.podval.conventions.publish"
      ),
      result.getOutput()
    );
  }

  @Test
  void userLibraryBesidePluginMavenIsNotConfiguredTwice() throws IOException {
    writeSettingsWithConventions();
    writeBuild(
      """
      plugins {
        id 'java-gradle-plugin'
        id 'org.podval.conventions.publish'
      }
      podvalPublish {
        gitHubRepository = 'dubinsky/scalajs-gradle'
      }
      gradlePlugin {
        plugins {
          dummy {
            id = 'conventions.test.dummy'
            implementationClass = 'org.gradle.api.plugins.JavaLibraryPlugin'
          }
        }
      }
      publishing {
        publications {
          library(MavenPublication) {
            from components.java
          }
        }
      }
      tasks.register('assertPublications') {
        doLast {
          assert publishing.publications.findByName('library') != null
          assert publishing.publications.findByName('pluginMaven') != null
        }
      }
      """
    );
    BuildResult result = runner("assertPublications").build();
    assertEquals(TaskOutcome.SUCCESS, result.task(":assertPublications").getOutcome());
  }

  @Test
  void javaGradlePluginDoesNotCreateLibraryPublication() throws IOException {
    writeSettingsWithConventions();
    writeBuild(
      """
      plugins {
        id 'java-gradle-plugin'
        id 'maven-publish'
        id 'org.podval.conventions.publish'
      }
      podvalPublish {
        gitHubRepository = 'dubinsky/xml'
        name = 'Test Plugin'
      }
      gradlePlugin {
        plugins {
          dummy {
            id = 'conventions.test.dummy'
            implementationClass = 'org.gradle.api.plugins.JavaLibraryPlugin'
          }
        }
      }
      tasks.register('assertPublications') {
        doLast {
          assert publishing.publications.findByName('library') == null
          assert publishing.publications.findByName('pluginMaven') != null
        }
      }
      """
    );
    BuildResult result = runner("assertPublications").build();
    assertEquals(TaskOutcome.SUCCESS, result.task(":assertPublications").getOutcome());
  }

  @Test
  void javaGradlePluginPomAndWebsiteFromExtension() throws IOException {
    writeSettingsWithConventions();
    writeBuild(
      """
      plugins {
        id 'java-gradle-plugin'
        id 'maven-publish'
        id 'org.podval.conventions.publish'
      }
      podvalPublish {
        gitHubRepository = 'dubinsky/xml'
        name = 'Test Plugin'
        inceptionYear = '2026'
      }
      gradlePlugin {
        plugins {
          dummy {
            id = 'conventions.test.dummy'
            implementationClass = 'org.gradle.api.plugins.JavaLibraryPlugin'
          }
        }
      }
      """
    );
    BuildResult result = runner("generatePomFileForPluginMavenPublication").build();
    assertEquals(
      TaskOutcome.SUCCESS,
      result.task(":generatePomFileForPluginMavenPublication").getOutcome()
    );
    String pom = Files.readString(
      projectDir.resolve("build/publications/pluginMaven/pom-default.xml")
    );
    assertTrue(pom.contains("<name>Test Plugin</name>"), pom);
    assertTrue(pom.contains("<url>https://github.com/dubinsky/xml</url>"), pom);
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

  @Test
  void zincConventionFromPlugin() throws IOException {
    writeSettingsWithConventions();
    writeBuild(
      """
      plugins {
        id 'scala'
        id 'org.podval.conventions'
      }
      tasks.register('assertZinc') {
        doLast {
          assert scala.zincVersion.get() == '1.12.1'
        }
      }
      """
    );
    BuildResult result = runner("assertZinc").build();
    assertEquals(TaskOutcome.SUCCESS, result.task(":assertZinc").getOutcome());
  }

  @Test
  void zincConventionYieldsToBuildScriptSet() throws IOException {
    writeSettingsWithConventions();
    writeBuild(
      """
      plugins {
        id 'scala'
        id 'org.podval.conventions'
      }
      scala.zincVersion = '1.13.0'
      tasks.register('assertZinc') {
        doLast {
          assert scala.zincVersion.get() == '1.13.0'
        }
      }
      """
    );
    BuildResult later = runner("assertZinc").build();
    assertEquals(TaskOutcome.SUCCESS, later.task(":assertZinc").getOutcome());

    writeBuild(
      """
      plugins {
        id 'scala'
      }
      scala.zincVersion = '1.12.1'
      pluginManager.apply 'org.podval.conventions'
      tasks.register('assertZinc') {
        doLast {
          assert scala.zincVersion.get() == '1.12.1'
        }
      }
      """
    );
    BuildResult prior = runner("assertZinc").build();
    assertEquals(TaskOutcome.SUCCESS, prior.task(":assertZinc").getOutcome());
  }

  @Test
  void scalaFlagRemovalWhenCompileScalaAlreadyRealized() throws IOException {
    writeSettingsWithConventions();
    writeBuild(
      """
      plugins {
        id 'scala'
      }
      scala.scalaVersion = '3.9.0'
      tasks.named('compileScala').get()
      pluginManager.apply 'org.podval.conventions'
      podval.scalaCompilerParameters.remove('-source:future')
      tasks.register('assertFlags') {
        doLast {
          def params = tasks.named('compileScala').get().scalaCompileOptions.additionalParameters
          assert params.contains('-release:25')
          assert params.contains('-new-syntax')
          assert params.contains('-feature')
          assert params.contains('-language:strictEquality')
          assert !params.contains('-source:future')
        }
      }
      """
    );
    BuildResult result = runner("assertFlags").build();
    assertEquals(TaskOutcome.SUCCESS, result.task(":assertFlags").getOutcome());
  }

  @Test
  void scalaFlagRemovalWhenPluginAppliedFirst() throws IOException {
    writeSettingsWithConventions();
    writeBuild(
      """
      plugins {
        id 'scala'
        id 'org.podval.conventions'
      }
      scala.scalaVersion = '3.9.0'
      podval.scalaCompilerParameters.remove('-source:future')
      tasks.register('assertFlags') {
        doLast {
          def params = tasks.named('compileScala').get().scalaCompileOptions.additionalParameters
          assert params.contains('-release:25')
          assert params.contains('-new-syntax')
          assert params.contains('-feature')
          assert params.contains('-language:strictEquality')
          assert !params.contains('-source:future')
        }
      }
      """
    );
    BuildResult result = runner("assertFlags").build();
    assertEquals(TaskOutcome.SUCCESS, result.task(":assertFlags").getOutcome());
  }

  @Test
  void scalaFlagAppendFromLaterAfterEvaluate() throws IOException {
    writeSettingsWithConventions();
    writeBuild(
      """
      plugins {
        id 'scala'
        id 'org.podval.conventions'
      }
      scala.scalaVersion = '3.9.0'
      afterEvaluate {
        def compileScala = tasks.named('compileScala').get()
        def params = new ArrayList(compileScala.scalaCompileOptions.additionalParameters)
        if (!params.contains('-extra-token')) {
          params.add('-extra-token')
        }
        compileScala.scalaCompileOptions.additionalParameters = params
      }
      tasks.register('assertFlags') {
        doLast {
          def params = tasks.named('compileScala').get().scalaCompileOptions.additionalParameters
          assert params.contains('-release:25')
          assert params.contains('-new-syntax')
          assert params.contains('-feature')
          assert params.contains('-language:strictEquality')
          assert params.contains('-source:future')
          assert params.contains('-extra-token')
        }
      }
      """
    );
    BuildResult result = runner("assertFlags").build();
    assertEquals(TaskOutcome.SUCCESS, result.task(":assertFlags").getOutcome());
  }

  @Test
  void javaToolchainConventionKeepsPriorSet() throws IOException {
    writeSettingsWithConventions();
    writeBuild(
      """
      plugins {
        id 'java-library'
      }
      java.toolchain.languageVersion.set(JavaLanguageVersion.of(21))
      pluginManager.apply 'org.podval.conventions'
      tasks.register('assertToolchain') {
        doLast {
          assert java.toolchain.languageVersion.get() == JavaLanguageVersion.of(21)
        }
      }
      """
    );
    BuildResult result = runner("assertToolchain").build();
    assertEquals(TaskOutcome.SUCCESS, result.task(":assertToolchain").getOutcome());
  }

  @Test
  void javaToolchainLaterSetWins() throws IOException {
    writeSettingsWithConventions();
    writeBuild(
      """
      plugins {
        id 'java-library'
        id 'org.podval.conventions'
      }
      java.toolchain.languageVersion.set(JavaLanguageVersion.of(21))
      tasks.register('assertToolchain') {
        doLast {
          assert java.toolchain.languageVersion.get() == JavaLanguageVersion.of(21)
        }
      }
      """
    );
    BuildResult result = runner("assertToolchain").build();
    assertEquals(TaskOutcome.SUCCESS, result.task(":assertToolchain").getOutcome());
  }

  @Test
  void jvmLanguageVersionConventionFollowsExtension() throws IOException {
    writeSettingsWithConventions();
    writeBuild(
      """
      plugins {
        id 'java-library'
        id 'org.podval.conventions'
      }
      podval.jvmLanguageVersion.set(JavaLanguageVersion.of(21))
      tasks.register('assertToolchain') {
        doLast {
          assert java.toolchain.languageVersion.get() == JavaLanguageVersion.of(21)
          assert podval.jvmLanguageVersion.get() == JavaLanguageVersion.of(21)
        }
      }
      """
    );
    BuildResult result = runner("assertToolchain").build();
    assertEquals(TaskOutcome.SUCCESS, result.task(":assertToolchain").getOutcome());
  }

  @Test
  void configurationCacheReusesJarAndPom() throws IOException {
    writeJarAndPomBuild();
    runner("jar", "generatePomFileForLibraryPublication", "--configuration-cache").build();
    BuildResult second = runner(
      "jar",
      "generatePomFileForLibraryPublication",
      "--configuration-cache"
    ).build();
    assertTrue(second.getOutput().contains("Reusing configuration cache."), second.getOutput());
    Path jar = projectDir.resolve("build/libs/conventions-test-1.2.3.jar");
    assertTrue(Files.isRegularFile(jar), jar.toString());
    try (JarFile jarFile = new JarFile(jar.toFile())) {
      assertEquals(
        "conventions-test",
        jarFile.getManifest().getMainAttributes().getValue("Implementation-Title")
      );
    }
  }

  @Test
  void configurationCacheReusesJarWithoutSigningKey() throws IOException {
    writeJarAndPomBuild();
    assertFalse(Files.exists(projectDir.resolve("gradle.properties")));
    runner("jar", "generatePomFileForLibraryPublication", "--configuration-cache").build();
    BuildResult second = runner(
      "jar",
      "generatePomFileForLibraryPublication",
      "--configuration-cache"
    ).build();
    assertTrue(second.getOutput().contains("Reusing configuration cache."), second.getOutput());
    assertTrue(second.getOutput().contains("BUILD SUCCESSFUL"), second.getOutput());
  }

  @Test
  void jdkRestrictedAccessArgsOnlyWhenHelperCalled() throws IOException {
    writeSettingsWithConventions();
    writeBuild(
      """
      plugins {
        id 'java'
        id 'org.podval.conventions'
      }
      tasks.register('assertNoFlags') {
        doLast {
          def args = tasks.named('test', Test).get().jvmArgs ?: []
          assert !args.contains('--enable-native-access=ALL-UNNAMED')
          assert !args.contains('--sun-misc-unsafe-memory-access=allow')
        }
      }
      """
    );
    BuildResult absent = runner("assertNoFlags").build();
    assertEquals(TaskOutcome.SUCCESS, absent.task(":assertNoFlags").getOutcome());

    writeBuild(
      """
      plugins {
        id 'application'
        id 'org.podval.conventions'
      }
      application {
        mainClass = 'example.Main'
        applicationDefaultJvmArgs = [
          '--enable-native-access=ALL-UNNAMED',
          '--sun-misc-unsafe-memory-access=allow'
        ]
      }
      podval.allowJdk25RestrictedAccess()
      podval.allowJdk25RestrictedAccess()
      tasks.register('app', JavaExec) {
        classpath = sourceSets.main.runtimeClasspath
        mainClass = 'example.Main'
      }
      def once = { args ->
        assert args.count { it == '--enable-native-access=ALL-UNNAMED' } == 1
        assert args.count { it == '--sun-misc-unsafe-memory-access=allow' } == 1
      }
      once(tasks.named('test', Test).get().jvmArgs)
      once(tasks.named('app', JavaExec).get().jvmArgs)
      once(tasks.named('run', JavaExec).get().jvmArgs)
      """
    );
    runner("help", "--configuration-cache").build();
    BuildResult second = runner("help", "--configuration-cache").build();
    assertTrue(second.getOutput().contains("Reusing configuration cache."), second.getOutput());
  }

  private void writeJarAndPomBuild() throws IOException {
    writeSettingsWithConventions();
    writeBuild(
      """
      plugins {
        id 'java-library'
        id 'org.podval.conventions.publish'
      }
      version = '1.2.3'
      podvalPublish {
        gitHubRepository = 'dubinsky/xml'
      }
      """
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
