package org.podval.conventions;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.plugins.scala.ScalaPluginExtension;
import org.gradle.api.tasks.scala.ScalaCompile;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import java.util.List;

/**
 * {@code podval} extension, Java toolchain convention when {@code java} is present
 * (covers {@code java-library} and {@code scala}), and Scala flags when {@code scala}
 * is present. Does not apply {@code java} or {@code scala}. Does not skip
 * {@code compileJava}. Does not call {@code allowJdk25RestrictedAccess}.
 * {@code dependencyUpdates} comes from {@code org.podval.conventions.settings}.
 */
public final class ConventionsPlugin implements Plugin<Project> {
  public static final String PLUGIN_ID = "org.podval.conventions";
  public static final int JVM = 25;

  @Override
  public void apply(Project project) {
    ConventionsExtension podval =
      project.getExtensions().create(ConventionsExtension.NAME, ConventionsExtension.class);
    podval.getJvmLanguageVersion().convention(JavaLanguageVersion.of(JVM));

    project.getPluginManager().withPlugin("java", unused -> {
      JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);
      java.getToolchain().getLanguageVersion().convention(podval.getJvmLanguageVersion());
    });

    project.getPluginManager().withPlugin("scala", unused -> {
      ScalaPluginExtension scala = project.getExtensions().getByType(ScalaPluginExtension.class);
      scala.getScalaVersion().convention(project.getProviders().gradleProperty("scalaVersion"));
      scala.getZincVersion().convention("1.12.1");
    });

    // After the build script edits the list, including when compileScala is already realized.
    // The copy is the task input; a later afterEvaluate may still append on the task.
    project.afterEvaluate(ignored -> {
      List<String> flags = List.copyOf(podval.getScalaCompilerParameters());
      project.getTasks().configureEach(task -> {
        if (task instanceof ScalaCompile scalaCompile) {
          scalaCompile.getScalaCompileOptions().setAdditionalParameters(flags);
        }
      });
    });
  }
}
