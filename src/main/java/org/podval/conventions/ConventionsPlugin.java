package org.podval.conventions;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

/**
 * Java 25 toolchain when {@code java} is present (covers {@code java-library}
 * and {@code scala}). Scala 3 flags when {@code scala} is present. Does not
 * apply {@code java} or {@code scala}. Does not skip {@code compileJava}.
 * {@code dependencyUpdates} comes from {@code org.podval.conventions.settings}.
 */
public final class ConventionsPlugin implements Plugin<Project> {
  public static final String PLUGIN_ID = "org.podval.conventions";
  public static final int JVM = 25;

  @Override
  public void apply(Project project) {
    project.getPluginManager().withPlugin("java", unused -> {
      JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);
      java.getToolchain().getLanguageVersion().set(JavaLanguageVersion.of(JVM));
    });

    project.getPluginManager().withPlugin("scala", unused -> ScalaFlags.configure(project));
  }
}
