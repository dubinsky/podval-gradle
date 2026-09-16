package org.podval.conventions;

import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

/**
 * Versions plugin plus shared {@code rejectVersionIf}. Java 25 toolchain when
 * {@code java} is present (covers {@code java-library} and {@code scala}).
 * Scala 3 flags when {@code scala} is present. Does not apply {@code java} or
 * {@code scala}. Does not skip {@code compileJava}.
 */
public final class ConventionsPlugin implements Plugin<Project> {
  public static final String PLUGIN_ID = "org.podval.conventions";
  public static final String VERSIONS_PLUGIN_ID = "io.github.ben-manes.versions";
  public static final int JVM = 25;

  @Override
  public void apply(Project project) {
    project.getPluginManager().apply(VERSIONS_PLUGIN_ID);
    project.getTasks().withType(DependencyUpdatesTask.class).configureEach(task ->
      task.rejectVersionIf(selection -> VersionFilters.reject(selection.getCandidate().getVersion()))
    );

    project.getPluginManager().withPlugin("java", unused -> {
      JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);
      java.getToolchain().getLanguageVersion().set(JavaLanguageVersion.of(JVM));
    });

    project.getPluginManager().withPlugin("scala", unused -> ScalaFlags.configure(project));
  }
}
