package org.podval.conventions;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VersionFiltersTest {
  @Test
  void acceptsStableNumericVersions() {
    assertFalse(VersionFilters.reject("1.0.0"));
    assertFalse(VersionFilters.reject("0.0.17"));
    assertFalse(VersionFilters.reject("0.61.0"));
    assertFalse(VersionFilters.reject("1.0.0-r"));
    assertFalse(VersionFilters.reject("v1.2.3"));
  }

  @Test
  void acceptsStableKeywords() {
    assertFalse(VersionFilters.isNonStable("1.0.0.Final"));
    assertFalse(VersionFilters.isNonStable("2.0-RELEASE"));
    assertFalse(VersionFilters.isNonStable("1.0.0-GA"));
  }

  @Test
  void rejectsNonStable() {
    assertTrue(VersionFilters.isNonStable("1.0.0-RC1"));
    assertTrue(VersionFilters.isNonStable("1.0.0-SNAPSHOT"));
    assertTrue(VersionFilters.isNonStable("1.0.0-beta"));
    assertTrue(VersionFilters.reject("1.0.0-RC1"));
  }

  @Test
  void rejectsPaddedNumericComponent() {
    assertTrue(VersionFilters.hasPaddedNumericComponent("0.017"));
    assertTrue(VersionFilters.reject("0.017"));
    assertFalse(VersionFilters.hasPaddedNumericComponent("0.0.17"));
    assertTrue(VersionFilters.hasPaddedNumericComponent("1.2.03"));
  }
}
