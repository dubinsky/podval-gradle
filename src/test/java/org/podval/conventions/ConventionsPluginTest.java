package org.podval.conventions;

import groovy.json.JsonSlurper;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
  void compileJavaOptInIsPerProject() throws IOException {
    writeSettings(
      """
      plugins {
        id 'org.podval.conventions.settings'
      }
      include 'lib'
      rootProject.name = 'compile-java-opt-in'
      """
    );
    writeBuild(
      """
      plugins {
        id 'java-library'
        id 'org.podval.conventions'
      }
      gradle.projectsEvaluated {
        assert rootProject.tasks.named('compileJava').get().enabled
        assert !gradle.startParameter.excludedTaskNames.contains('compileJava')
        def lib = rootProject.project(':lib')
        assert !lib.tasks.named('compileJava').get().enabled
        assert lib.tasks.named('compileTestJava').get().enabled
      }
      """
    );
    Files.createDirectories(projectDir.resolve("lib"));
    Files.writeString(
      projectDir.resolve("lib/build.gradle"),
      """
      plugins {
        id 'java-library'
        id 'org.podval.conventions'
      }
      podval.compileJavaEnabled = false
      """,
      StandardCharsets.UTF_8
    );
    BuildResult result = runner("help").build();
    assertTrue(result.getOutput().contains("BUILD SUCCESSFUL"), result.getOutput());
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
          assert params[0] == '-java-output-version:25'
          assert params.contains('-java-output-version:25')
          assert !params.any { it.startsWith('-release') }
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
  void scalaBeforePublishIsRequired() throws IOException {
    writeSettingsWithConventions();
    writeBuild(
      """
      plugins {
        id 'java-library'
        id 'org.podval.conventions.publish'
        id 'scala'
      }
      """
    );
    BuildResult result = runner("help").buildAndFail();
    assertTrue(
      result.getOutput().contains(
        "Apply scala before org.podval.conventions.publish so javadocJar is built from ScalaDoc"
      ),
      result.getOutput()
    );
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
          assert params.contains('-java-output-version:25')
          assert !params.any { it.startsWith('-release') }
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
          assert params.contains('-java-output-version:25')
          assert !params.any { it.startsWith('-release') }
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
          assert params.contains('-java-output-version:25')
          assert !params.any { it.startsWith('-release') }
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
  void javaOutputVersionFollowsJvmLanguageVersionUntilSet() throws IOException {
    writeSettingsWithConventions();
    writeBuild(
      """
      plugins {
        id 'scala'
        id 'org.podval.conventions'
      }
      scala.scalaVersion = '3.9.0'
      podval.jvmLanguageVersion.set(JavaLanguageVersion.of(21))
      tasks.register('assertFlags') {
        doLast {
          assert podval.javaOutputVersion.get() == 21
          def params = tasks.named('compileScala').get().scalaCompileOptions.additionalParameters
          assert params.contains('-java-output-version:21')
          assert !params.any { it.startsWith('-release') }
          assert java.toolchain.languageVersion.get() == JavaLanguageVersion.of(21)
        }
      }
      """
    );
    BuildResult followed = runner("assertFlags").build();
    assertEquals(TaskOutcome.SUCCESS, followed.task(":assertFlags").getOutcome());

    writeBuild(
      """
      plugins {
        id 'scala'
        id 'org.podval.conventions'
      }
      scala.scalaVersion = '3.9.0'
      podval {
        jvmLanguageVersion.set(JavaLanguageVersion.of(21))
        javaOutputVersion = 17
      }
      tasks.register('assertFlags') {
        doLast {
          assert podval.javaOutputVersion.get() == 17
          def params = tasks.named('compileScala').get().scalaCompileOptions.additionalParameters
          assert params.contains('-java-output-version:17')
          assert !params.any { it.startsWith('-release') }
          assert java.toolchain.languageVersion.get() == JavaLanguageVersion.of(21)
        }
      }
      """
    );
    BuildResult overridden = runner("assertFlags").build();
    assertEquals(TaskOutcome.SUCCESS, overridden.task(":assertFlags").getOutcome());
  }

  @Test
  void javaOutputVersionFlagInExtensionListFails() throws IOException {
    writeSettingsWithConventions();
    writeBuild(
      """
      plugins {
        id 'scala'
        id 'org.podval.conventions'
      }
      scala.scalaVersion = '3.9.0'
      podval.scalaCompilerParameters.add('-release:17')
      tasks.named('compileScala').get()
      """
    );
    BuildResult release = runner("help").buildAndFail();
    assertTrue(
      release.getOutput().contains(
        "podval.scalaCompilerParameters contains '-release:17'; set podval.javaOutputVersion instead"
      ),
      release.getOutput()
    );

    writeBuild(
      """
      plugins {
        id 'scala'
        id 'org.podval.conventions'
      }
      scala.scalaVersion = '3.9.0'
      podval.scalaCompilerParameters.add('-java-output-version:17')
      tasks.named('compileScala').get()
      """
    );
    BuildResult outputVersion = runner("help").buildAndFail();
    assertTrue(
      outputVersion.getOutput().contains(
        "podval.scalaCompilerParameters contains '-java-output-version:17'; set podval.javaOutputVersion instead"
      ),
      outputVersion.getOutput()
    );
  }

  @Test
  void foreignScalaFlagSurvivesMerge() throws IOException {
    writeSettingsWithConventions();
    writeBuild(
      """
      plugins {
        id 'scala'
      }
      scala.scalaVersion = '3.9.0'
      def compileScala = tasks.named('compileScala').get()
      compileScala.scalaCompileOptions.additionalParameters = ['-release:17', '-scalajs']
      pluginManager.apply 'org.podval.conventions'
      tasks.register('assertFlags') {
        doLast {
          def params = tasks.named('compileScala').get().scalaCompileOptions.additionalParameters
          assert params.contains('-scalajs')
          assert params.contains('-new-syntax')
          assert params.contains('-java-output-version:25')
          assert params.count {
            it.startsWith('-java-output-version') || it.startsWith('-release') || it.startsWith('--release')
          } == 1
          assert !params.any { it.startsWith('-release') }
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
  void implementationTitleUsesPublishName() throws IOException {
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
        name = 'Podval XML'
      }
      """
    );
    BuildResult result = runner("jar").build();
    assertEquals(TaskOutcome.SUCCESS, result.task(":jar").getOutcome());
    Path jar = projectDir.resolve("build/libs/conventions-test-1.2.3.jar");
    assertTrue(Files.isRegularFile(jar), jar.toString());
    try (JarFile jarFile = new JarFile(jar.toFile())) {
      assertEquals(
        "Podval XML",
        jarFile.getManifest().getMainAttributes().getValue("Implementation-Title")
      );
    }
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
  void configurationCacheSignsWithDummyKey() throws IOException {
    // Throwaway key in its own GNUPGHOME. Do not use the default ~/.gnupg.
    Path gnupgHome = Files.createTempDirectory("podval-signing-gnupg-");
    Files.setPosixFilePermissions(
      gnupgHome,
      Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE)
    );
    String passphrase = "throwaway-signing-passphrase";
    try {
      String key = exportThrowawaySigningKey(gnupgHome, passphrase);
      writeJarAndPomBuild();
      writeSigningProperties(key, passphrase);
      runSigning(key, passphrase, "signLibraryPublication", "--configuration-cache");
      BuildResult second = runSigning(key, passphrase, "signLibraryPublication", "--configuration-cache");
      String output = redact(second.getOutput(), key, passphrase);
      assertTrue(output.contains("Reusing configuration cache."), output);
      assertSignatureFiles();
      assertSignedByThrowawayKey(gnupgHome);
    } finally {
      killGnupg(gnupgHome);
      deleteTree(gnupgHome);
    }
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
      podval.allowJdkRestrictedAccess()
      podval.allowJdkRestrictedAccess()
      application {
        mainClass = 'example.Main'
        applicationDefaultJvmArgs = ['-Xss8m']
      }
      tasks.register('app', JavaExec) {
        classpath = sourceSets.main.runtimeClasspath
        mainClass = 'example.Main'
      }
      tasks.register('assertFlags') {
        def nativeAccess = '--enable-native-access=ALL-UNNAMED'
        def unsafeAccess = '--sun-misc-unsafe-memory-access=allow'
        def applicationArgs = application.applicationDefaultJvmArgs.toList()
        def startScriptsOpts = tasks.named('startScripts', CreateStartScripts).get().defaultJvmOpts.toList()
        def runArgs = (tasks.named('run', JavaExec).get().jvmArgs ?: []).toList()
        def testArgs = (tasks.named('test', Test).get().jvmArgs ?: []).toList()
        def appArgs = (tasks.named('app', JavaExec).get().jvmArgs ?: []).toList()
        doLast {
          def once = { args ->
            assert args.count { it == nativeAccess } == 1
            assert args.count { it == unsafeAccess } == 1
          }
          def withStack = { args ->
            assert args.contains('-Xss8m')
            once(args)
          }
          withStack(applicationArgs)
          withStack(startScriptsOpts)
          withStack(runArgs)
          once(testArgs)
          once(appArgs)
          assert !appArgs.contains('-Xss8m')
        }
      }
      """
    );
    BuildResult first = runner("assertFlags", "--configuration-cache").build();
    assertEquals(TaskOutcome.SUCCESS, first.task(":assertFlags").getOutcome());
    BuildResult second = runner("assertFlags", "--configuration-cache").build();
    assertEquals(TaskOutcome.SUCCESS, second.task(":assertFlags").getOutcome());
    assertTrue(second.getOutput().contains("Reusing configuration cache."), second.getOutput());
  }

  @Test
  void acceptHookOnSubprojectReport() throws IOException {
    writeFakeWidgetRepo();
    Files.createDirectories(projectDir.resolve("lib"));
    writeBuild("");
    Files.writeString(
      projectDir.resolve("lib/build.gradle"),
      """
      plugins {
        id 'java-library'
      }
      dependencies {
        implementation 'com.example.fake:widget:9.0.0-core.0.4'
      }
      """,
      StandardCharsets.UTF_8
    );
    writeAcceptHookSettings(true);
    BuildResult withHook = dependencyUpdates();
    assertTrue(withHook.getOutput().contains("BUILD SUCCESSFUL"), withHook.getOutput());
    Path report = projectDir.resolve("build/dependencyUpdates/report.json");
    assertTrue(Files.isRegularFile(report), withHook.getOutput());
    assertEquals(
      "9.0.0-core.0.5",
      availableRelease(report, "com.example.fake", "widget"),
      Files.readString(report)
    );
    // The weekly workflow passes no --revision. The default is milestone, which files the same
    // candidate under available.milestone and leaves available.release null.
    BuildResult defaultRevision = runner("dependencyUpdates", "--output-formatter=json").build();
    assertTrue(defaultRevision.getOutput().contains("BUILD SUCCESSFUL"), defaultRevision.getOutput());
    assertEquals(
      "9.0.0-core.0.5",
      outdatedAvailable(report, "com.example.fake", "widget"),
      Files.readString(report)
    );

    runner("help", "--configuration-cache").build();
    BuildResult cached = runner("help", "--configuration-cache").build();
    assertTrue(cached.getOutput().contains("Reusing configuration cache."), cached.getOutput());

    writeAcceptHookSettings(false);
    BuildResult withoutHook = dependencyUpdates();
    assertTrue(withoutHook.getOutput().contains("BUILD SUCCESSFUL"), withoutHook.getOutput());
    assertNull(
      availableRelease(report, "com.example.fake", "widget"),
      Files.readString(report)
    );
  }

  @Test
  void acceptVersionIfAnySpecKeepsTheVersion() {
    ConventionsSettingsExtension extension = new ConventionsSettingsExtension();
    assertFalse(extension.accepted("1.0.0-RC1"));
    extension.acceptVersionIf(version -> version.contains("-core."));
    extension.acceptVersionIf(version -> version.contains("RC"));
    assertTrue(extension.accepted("9.0.0-core.0.5"));
    assertTrue(extension.accepted("1.0.0-RC1"));
    assertFalse(extension.accepted("1.0.0-legacy"));
  }

  private void writeFakeWidgetRepo() throws IOException {
    Path module = projectDir.resolve("repo/com/example/fake/widget");
    for (String version : new String[] {"9.0.0-core.0.4", "9.0.0-core.0.5"}) {
      Path versionDir = module.resolve(version);
      Files.createDirectories(versionDir);
      Files.writeString(
        versionDir.resolve("widget-" + version + ".pom"),
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <project>
          <modelVersion>4.0.0</modelVersion>
          <groupId>com.example.fake</groupId>
          <artifactId>widget</artifactId>
          <version>%s</version>
        </project>
        """.formatted(version),
        StandardCharsets.UTF_8
      );
      try (JarOutputStream ignored = new JarOutputStream(
        Files.newOutputStream(versionDir.resolve("widget-" + version + ".jar"))
      )) {
        // Manifest only. The versions plugin resolves the jar.
      }
    }
    Files.writeString(
      module.resolve("maven-metadata.xml"),
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <metadata>
        <groupId>com.example.fake</groupId>
        <artifactId>widget</artifactId>
        <versioning>
          <latest>9.0.0-core.0.5</latest>
          <release>9.0.0-core.0.5</release>
          <versions>
            <version>9.0.0-core.0.4</version>
            <version>9.0.0-core.0.5</version>
          </versions>
          <lastUpdated>20261109000000</lastUpdated>
        </versioning>
      </metadata>
      """,
      StandardCharsets.UTF_8
    );
  }

  private void writeAcceptHookSettings(boolean acceptCore) throws IOException {
    String hook = acceptCore
      ? """
        podvalSettings {
          acceptVersionIf { it.contains('-core.') }
        }
        """
      : "";
    writeSettings(
      """
      plugins {
        id 'org.podval.conventions.settings'
      }
      """
      + hook
      + """
      def widgetRepo = uri('repo')
      dependencyResolutionManagement {
        repositories {
          exclusiveContent {
            forRepository {
              maven { url = widgetRepo }
            }
            filter {
              includeModule('com.example.fake', 'widget')
            }
          }
        }
      }
      include 'lib'
      rootProject.name = 'accept-hook'
      """
    );
  }

  private BuildResult dependencyUpdates() {
    // Default revision is milestone, which leaves available.release null.
    return runner(
      "dependencyUpdates",
      "--output-formatter=json",
      "--revision=release",
      "--no-check-for-gradle-update"
    ).build();
  }

  private static String availableRelease(Path reportFile, String group, String name) {
    Map<?, ?> versions = availableVersions(reportFile, group, name);
    if (versions == null) {
      return null;
    }
    Object release = versions.get("release");
    return release == null ? null : release.toString();
  }

  private static String outdatedAvailable(Path reportFile, String group, String name) {
    Map<?, ?> versions = availableVersions(reportFile, group, name);
    if (versions == null) {
      return null;
    }
    Object release = versions.get("release");
    if (release != null) {
      return release.toString();
    }
    Object milestone = versions.get("milestone");
    return milestone == null ? null : milestone.toString();
  }

  private static Map<?, ?> availableVersions(Path reportFile, String group, String name) {
    Object parsed = new JsonSlurper().parse(reportFile.toFile());
    if (!(parsed instanceof Map<?, ?> report)) {
      return null;
    }
    Object outdated = report.get("outdated");
    if (!(outdated instanceof Map<?, ?> outdatedGroup)) {
      return null;
    }
    Object dependencies = outdatedGroup.get("dependencies");
    if (!(dependencies instanceof Iterable<?> rows)) {
      return null;
    }
    for (Object row : rows) {
      if (!(row instanceof Map<?, ?> dependency)) {
        continue;
      }
      if (!group.equals(dependency.get("group")) || !name.equals(dependency.get("name"))) {
        continue;
      }
      Object available = dependency.get("available");
      if (!(available instanceof Map<?, ?> versions)) {
        return null;
      }
      return versions;
    }
    return null;
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

  private void writeSigningProperties(String key, String passphrase) throws IOException {
    Properties properties = new Properties();
    properties.setProperty("signingKey", key);
    properties.setProperty("signingPassword", passphrase);
    try (OutputStream out = Files.newOutputStream(projectDir.resolve("gradle.properties"))) {
      properties.store(out, null);
    }
  }

  private BuildResult runSigning(String key, String passphrase, String... arguments) {
    try {
      return GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments(arguments)
        .withEnvironment(signingRunnerEnvironment())
        .build();
    } catch (RuntimeException failure) {
      throw new AssertionError(redact(String.valueOf(failure.getMessage()), key, passphrase));
    }
  }

  private static Map<String, String> signingRunnerEnvironment() {
    // These outrank the temp project's gradle.properties and would sign with another key.
    Map<String, String> environment = new HashMap<>(System.getenv());
    environment.remove("ORG_GRADLE_PROJECT_signingKey");
    environment.remove("ORG_GRADLE_PROJECT_signingPassword");
    environment.remove("org.gradle.project.signingKey");
    environment.remove("org.gradle.project.signingPassword");
    return environment;
  }

  private void assertSignatureFiles() throws IOException {
    List<Path> signatures;
    try (Stream<Path> walk = Files.walk(projectDir.resolve("build"))) {
      signatures = walk
        .filter(Files::isRegularFile)
        .filter(path -> path.getFileName().toString().endsWith(".asc"))
        .toList();
    }
    assertTrue(signatures.size() > 1, signatures.toString());
    for (Path signature : signatures) {
      String text = Files.readString(signature);
      assertTrue(text.contains("-----BEGIN PGP SIGNATURE-----"), signature.toString());
    }
  }

  private void assertSignedByThrowawayKey(Path gnupgHome) throws IOException {
    Path jar = projectDir.resolve("build/libs/conventions-test-1.2.3.jar");
    Path signature = projectDir.resolve("build/libs/conventions-test-1.2.3.jar.asc");
    assertTrue(Files.isRegularFile(jar), jar.toString());
    assertTrue(Files.isRegularFile(signature), signature.toString());
    // Only the public key in this homedir can verify. Do not retrieve any other key.
    runGpg(
      gnupgHome,
      List.of(
        "gpg",
        "--batch",
        "--homedir", gnupgHome.toString(),
        "--trust-model", "always",
        "--no-auto-key-retrieve",
        "--verify",
        signature.toString(),
        jar.toString()
      ),
      false
    );
  }

  private static String exportThrowawaySigningKey(Path gnupgHome, String passphrase) throws IOException {
    runGpg(
      gnupgHome,
      List.of(
        "gpg",
        "--batch",
        "--pinentry-mode", "loopback",
        "--homedir", gnupgHome.toString(),
        "--passphrase", passphrase,
        "--quick-gen-key", "Podval Test <podval-test@example.invalid>",
        "rsa3072", "sign", "never"
      ),
      false
    );
    String armored = runGpg(
      gnupgHome,
      List.of(
        "gpg",
        "--batch",
        "--pinentry-mode", "loopback",
        "--homedir", gnupgHome.toString(),
        "--passphrase", passphrase,
        "--armor",
        "--export-secret-keys"
      ),
      true
    );
    if (!armored.contains("-----BEGIN PGP PRIVATE KEY BLOCK-----")
      || !armored.contains("-----END PGP PRIVATE KEY BLOCK-----")) {
      throw new IOException("gpg did not export an armored secret key");
    }
    return armored;
  }

  private static String runGpg(Path gnupgHome, List<String> command, boolean captureOutput) throws IOException {
    Path stdoutFile = gnupgHome.resolve(captureOutput ? "export-stdout" : "gpg-stdout");
    Path stderrFile = gnupgHome.resolve(captureOutput ? "export-stderr" : "gpg-stderr");
    ProcessBuilder builder = new ProcessBuilder(command);
    builder.environment().put("GNUPGHOME", gnupgHome.toString());
    builder.redirectOutput(stdoutFile.toFile());
    builder.redirectError(stderrFile.toFile());
    try {
      Process process = builder.start();
      if (!process.waitFor(60, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        throw new IOException("gpg timed out");
      }
      if (process.exitValue() != 0) {
        throw new IOException("gpg exited " + process.exitValue());
      }
      return captureOutput ? Files.readString(stdoutFile) : "";
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IOException("gpg interrupted", exception);
    } finally {
      Files.deleteIfExists(stdoutFile);
      Files.deleteIfExists(stderrFile);
    }
  }

  private static void killGnupg(Path gnupgHome) {
    ProcessBuilder builder = new ProcessBuilder(
      "gpgconf", "--homedir", gnupgHome.toString(), "--kill", "all"
    );
    builder.environment().put("GNUPGHOME", gnupgHome.toString());
    builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
    builder.redirectError(ProcessBuilder.Redirect.DISCARD);
    try {
      Process process = builder.start();
      if (!process.waitFor(30, TimeUnit.SECONDS)) {
        process.destroyForcibly();
      }
    } catch (IOException | InterruptedException exception) {
      if (exception instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private static void deleteTree(Path root) throws IOException {
    if (!Files.exists(root)) {
      return;
    }
    try (Stream<Path> walk = Files.walk(root)) {
      List<Path> paths = walk.sorted(Comparator.reverseOrder()).toList();
      for (Path path : paths) {
        Files.deleteIfExists(path);
      }
    }
  }

  private static String redact(String text, String key, String passphrase) {
    String redacted = text.replace(passphrase, "[redacted]");
    redacted = redacted.replace(key, "[redacted]");
    redacted = redacted.replace(key.replace("\r\n", "\n").replace("\n", "\\n"), "[redacted]");
    for (String line : key.split("\\R")) {
      String trimmed = line.trim();
      if (trimmed.length() >= 16 && !trimmed.startsWith("-----")) {
        redacted = redacted.replace(trimmed, "[redacted]");
      }
    }
    return redacted;
  }
}
