package org.podval.conventions;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaApplication;
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
    "-new-syntax",
    "-feature",
    "-language:strictEquality",
    "-source:future"
  ));

  /** Task container only. The fork action must not capture this project. */
  @Inject
  protected abstract Project getProject();

  public abstract Property<JavaLanguageVersion> getJvmLanguageVersion();

  public abstract Property<Integer> getJavaOutputVersion();

  public abstract Property<Boolean> getCompileJavaEnabled();

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

  public void allowJdkRestrictedAccess() {
    Project project = getProject();
    project.getTasks().withType(JavaExec.class).configureEach(task -> {
      // jvmArgs replaces run's convention from applicationDefaultJvmArgs.
      if ("run".equals(task.getName())) {
        return;
      }
      addRestrictedAccessArgs(task);
    });
    project.getTasks().withType(Test.class).configureEach(ConventionsExtension::addRestrictedAccessArgs);
    // After the build script assigns applicationDefaultJvmArgs. run and the start script read that list.
    project.getPluginManager().withPlugin("application", unused ->
      project.afterEvaluate(ignored -> mergeRestrictedAccessIntoApplicationDefaults(project))
    );
  }

  private static void mergeRestrictedAccessIntoApplicationDefaults(Project project) {
    JavaApplication application = project.getExtensions().getByType(JavaApplication.class);
    List<String> merged = new ArrayList<>();
    Iterable<String> current = application.getApplicationDefaultJvmArgs();
    if (current != null) {
      for (String arg : current) {
        merged.add(arg);
      }
    }
    if (!merged.contains(NATIVE_ACCESS)) {
      merged.add(NATIVE_ACCESS);
    }
    if (!merged.contains(UNSAFE_ACCESS)) {
      merged.add(UNSAFE_ACCESS);
    }
    application.setApplicationDefaultJvmArgs(merged);
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
