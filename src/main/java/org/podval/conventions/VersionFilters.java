package org.podval.conventions;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Shared {@code dependencyUpdates} reject rules: non-stable versions, and padded
 * numeric components such as Maven's {@code 0.017} (a padded {@code 0.0.17}).
 * A hook may keep a non-stable version. It cannot keep a padded component.
 */
public final class VersionFilters {
  private static final List<String> STABLE_KEYWORDS = List.of("RELEASE", "FINAL", "GA");
  private static final Pattern NUMERIC = Pattern.compile("^[0-9,.v-]+(-r)?$");
  private static final Pattern PADDED_NUMERIC = Pattern.compile("0\\d+");

  private VersionFilters() {}

  public static boolean reject(String version) {
    return reject(version, ignored -> false);
  }

  public static boolean reject(String version, Predicate<String> acceptNonStable) {
    if (hasPaddedNumericComponent(version)) {
      return true;
    }
    if (!isNonStable(version)) {
      return false;
    }
    return !acceptNonStable.test(version);
  }

  public static boolean isNonStable(String version) {
    // Whole component: "legacy" contains "GA" but is not the keyword.
    boolean stableKeyword = Arrays.stream(version.split("[.-]"))
      .anyMatch(part -> STABLE_KEYWORDS.contains(part.toUpperCase(Locale.ROOT)));
    return !stableKeyword && !NUMERIC.matcher(version).matches();
  }

  public static boolean hasPaddedNumericComponent(String version) {
    return Arrays.stream(version.split("[.-]"))
      .anyMatch(part -> part.length() > 1 && PADDED_NUMERIC.matcher(part).matches());
  }
}
