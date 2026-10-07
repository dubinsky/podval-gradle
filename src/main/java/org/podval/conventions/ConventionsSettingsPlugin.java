package org.podval.conventions;

import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask;
import nmcp.NmcpSettings;
import org.gradle.api.Plugin;
import org.gradle.api.initialization.Settings;

/**
 * Foojay toolchain resolver, Maven Central for dependency resolution, and the
 * versions settings plugin with the shared {@code rejectVersionIf}.
 * When {@code podvalCentral=true}, applies {@code com.gradleup.nmcp.settings} and
 * the USER_MANAGED Central Portal block.
 */
public final class ConventionsSettingsPlugin implements Plugin<Settings> {
  public static final String PLUGIN_ID = "org.podval.conventions.settings";
  public static final String FOOJAY_PLUGIN_ID =
    "org.gradle.toolchains.foojay-resolver-convention";
  public static final String VERSIONS_SETTINGS_PLUGIN_ID =
    "io.github.ben-manes.versions.settings";
  public static final String NMCP_SETTINGS_PLUGIN_ID = "com.gradleup.nmcp.settings";
  public static final String CENTRAL_PROPERTY = "podvalCentral";
  static final String USER_MANAGED = "USER_MANAGED";

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
    if (publishesToCentral(settings)) {
      settings.getPluginManager().apply(NMCP_SETTINGS_PLUGIN_ID);
      settings.getExtensions().getByType(NmcpSettings.class).centralPortal(portal -> {
        portal.getPublishingType().set(USER_MANAGED);
        portal.getUsername().set(settings.getProviders().gradleProperty("mavenCentralUsername"));
        portal.getPassword().set(settings.getProviders().gradleProperty("mavenCentralPassword"));
      });
    }
  }

  private static boolean publishesToCentral(Settings settings) {
    return settings.getProviders()
      .gradleProperty(CENTRAL_PROPERTY)
      .map("true"::equals)
      .getOrElse(false);
  }
}
