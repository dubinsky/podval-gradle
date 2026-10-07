package org.podval.conventions;

import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask;
import org.gradle.api.Plugin;
import org.gradle.api.initialization.Settings;

/**
 * Foojay toolchain resolver, Maven Central for dependency resolution, and the
 * versions settings plugin with the shared {@code rejectVersionIf}.
 * Does not apply {@code com.gradleup.nmcp.settings} — publishing builds keep that line.
 */
public final class ConventionsSettingsPlugin implements Plugin<Settings> {
  public static final String PLUGIN_ID = "org.podval.conventions.settings";
  public static final String FOOJAY_PLUGIN_ID =
    "org.gradle.toolchains.foojay-resolver-convention";
  public static final String VERSIONS_SETTINGS_PLUGIN_ID =
    "io.github.ben-manes.versions.settings";

  @Override
  public void apply(Settings settings) {
    settings.getPluginManager().apply(FOOJAY_PLUGIN_ID);
    settings.getPluginManager().apply(VERSIONS_SETTINGS_PLUGIN_ID);
    settings.getDependencyResolutionManagement().getRepositories().mavenCentral();
    // The settings plugin registers dependencyUpdates on the root project only.
    // Producers in other projects inherit this filter from that task.
    settings.getGradle().rootProject(project ->
      project.getTasks().withType(DependencyUpdatesTask.class).configureEach(task ->
        task.rejectVersionIf(selection ->
          VersionFilters.reject(selection.getCandidate().getVersion())
        )
      )
    );
  }
}
