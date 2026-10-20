package org.podval.conventions;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.testing.Test;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.process.JavaForkOptions;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

/** {@code podval { }} block on {@code org.podval.conventions}. */
public abstract class ConventionsExtension {
  public static final String NAME = "podval";

  static final String NATIVE_ACCESS = "--enable-native-access=ALL-UNNAMED";
  static final String UNSAFE_ACCESS = "--sun-misc-unsafe-memory-access=allow";

  private final List<String> scalaCompilerParameters = new ArrayList<>(List.of(
    "-release:" + ConventionsPlugin.JVM,
    "-new-syntax",
    "-feature",
    "-language:strictEquality",
    "-source:future"
  ));

  /** Task container only. The fork action must not capture this project. */
  @Inject
  protected abstract Project getProject();

  public abstract Property<JavaLanguageVersion> getJvmLanguageVersion();

  public List<String> getScalaCompilerParameters() {
    return scalaCompilerParameters;
  }

  public void setScalaCompilerParameters(List<String> parameters) {
    if (parameters == null) {
      throw new GradleException("podval.scalaCompilerParameters must not be null");
    }
    scalaCompilerParameters.clear();
    scalaCompilerParameters.addAll(parameters);
  }

  public void allowJdk25RestrictedAccess() {
    Project project = getProject();
    project.getTasks().withType(JavaExec.class).configureEach(ConventionsExtension::addRestrictedAccessArgs);
    project.getTasks().withType(Test.class).configureEach(ConventionsExtension::addRestrictedAccessArgs);
  }

  private static void addRestrictedAccessArgs(JavaForkOptions fork) {
    List<String> current = fork.getJvmArgs();
    List<String> have = current == null ? List.of() : current;
    if (!have.contains(NATIVE_ACCESS)) {
      fork.jvmArgs(NATIVE_ACCESS);
    }
    if (!have.contains(UNSAFE_ACCESS)) {
      fork.jvmArgs(UNSAFE_ACCESS);
    }
  }
}
