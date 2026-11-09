package org.podval.conventions;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.plugins.scala.ScalaPluginExtension;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.scala.ScalaCompile;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code podval} extension, Java toolchain convention when {@code java} is present
 * (covers {@code java-library} and {@code scala}), and Scala flags when {@code scala}
 * is present. Does not apply {@code java} or {@code scala}. Does not skip
 * {@code compileJava} by default; {@code compileJavaEnabled = false} disables that
 * project's {@code compileJava} only. Does not call {@code allowJdk25RestrictedAccess}.
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
    podval.getJavaOutputVersion().convention(
      podval.getJvmLanguageVersion().map(JavaLanguageVersion::asInt)
    );
    podval.getCompileJavaEnabled().convention(true);

    project.getPluginManager().withPlugin("java", unused -> {
      JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);
      java.getToolchain().getLanguageVersion().convention(podval.getJvmLanguageVersion());
      project.getTasks().withType(JavaCompile.class).configureEach(task -> {
        if (!JavaPlugin.COMPILE_JAVA_TASK_NAME.equals(task.getName())) {
          return;
        }
        if (!podval.getCompileJavaEnabled().get()) {
          task.setEnabled(false);
        }
      });
    });

    project.getPluginManager().withPlugin("scala", unused -> {
      ScalaPluginExtension scala = project.getExtensions().getByType(ScalaPluginExtension.class);
      scala.getScalaVersion().convention(project.getProviders().gradleProperty("scalaVersion"));
      scala.getZincVersion().convention("1.12.1");
    });

    // After the build script edits the list, including when compileScala is already realized.
    // A later afterEvaluate may still append on the task.
    project.afterEvaluate(ignored -> project.getTasks().withType(ScalaCompile.class).configureEach(
      scalaCompile -> applyScalaFlags(scalaCompile, podval)
    ));
  }

  private static void applyScalaFlags(ScalaCompile scalaCompile, ConventionsExtension podval) {
    List<String> extensionFlags = podval.getScalaCompilerParameters();
    for (String flag : extensionFlags) {
      if (flag.startsWith("-release") || flag.startsWith("-java-output-version")) {
        throw new GradleException(
          "podval.scalaCompilerParameters contains '" + flag + "'; set podval.javaOutputVersion instead"
        );
      }
    }

    String javaOutputFlag = "-java-output-version:" + podval.getJavaOutputVersion().get();
    List<String> current = scalaCompile.getScalaCompileOptions().getAdditionalParameters();
    List<String> merged = new ArrayList<>();
    if (current != null) {
      for (String flag : current) {
        if (flag.startsWith("-release")
          || flag.startsWith("-java-output-version")
          || flag.startsWith("--release")) {
          continue;
        }
        merged.add(flag);
      }
    }
    merged.add(0, javaOutputFlag);
    for (String flag : extensionFlags) {
      if (!merged.contains(flag)) {
        merged.add(flag);
      }
    }
    scalaCompile.getScalaCompileOptions().setAdditionalParameters(merged);
  }
}
