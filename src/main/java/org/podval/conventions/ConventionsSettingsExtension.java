package org.podval.conventions;

import org.gradle.api.specs.Spec;
import java.util.ArrayList;
import java.util.List;

/** {@code podvalSettings { }} block on {@code org.podval.conventions.settings}. */
public class ConventionsSettingsExtension {
  public static final String NAME = "podvalSettings";

  private final List<Spec<String>> acceptors = new ArrayList<>();

  public void acceptVersionIf(Spec<String> spec) {
    acceptors.add(spec);
  }

  boolean accepted(String version) {
    for (Spec<String> spec : acceptors) {
      if (spec.isSatisfiedBy(version)) {
        return true;
      }
    }
    return false;
  }
}
