package org.podval.conventions;

import org.gradle.api.Plugin;
import org.gradle.api.initialization.Settings;

/**
 * Foojay toolchain resolver and Maven Central for dependency resolution.
 * Does not apply {@code com.gradleup.nmcp.settings} — publishing builds keep that line.
 */
public final class ConventionsSettingsPlugin implements Plugin<Settings> {
  public static final String PLUGIN_ID = "org.podval.conventions.settings";
  public static final String FOOJAY_PLUGIN_ID =
    "org.gradle.toolchains.foojay-resolver-convention";

  @Override
  public void apply(Settings settings) {
    settings.getPluginManager().apply(FOOJAY_PLUGIN_ID);
    settings.getDependencyResolutionManagement().getRepositories().mavenCentral();
  }
}
