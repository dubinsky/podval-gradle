package org.podval.conventions;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Scala 3 flags matching the Java toolchain. {@code ScalaCompile} is not on the
 * Gradle plugin API classpath; match the runtime type of each task instead.
 */
final class ScalaFlags {
  private static final String SCALA_COMPILE = "org.gradle.api.tasks.scala.ScalaCompile";

  static final List<String> PARAMETERS = List.of(
    "-release:" + ConventionsPlugin.JVM,
    "-new-syntax",
    "-feature",
    "-language:strictEquality",
    "-source:future"
  );

  private ScalaFlags() {}

  static void configure(Project project) {
    project.getTasks().configureEach(task -> {
      if (isScalaCompile(task)) {
        setAdditionalParameters(task, PARAMETERS);
      }
    });
  }

  private static boolean isScalaCompile(Task task) {
    return isNamed(task.getClass(), SCALA_COMPILE);
  }

  private static boolean isNamed(Class<?> type, String name) {
    return type != null && (name.equals(type.getName()) || isNamed(type.getSuperclass(), name));
  }

  private static void setAdditionalParameters(Task task, List<String> parameters) {
    try {
      Method getOptions = task.getClass().getMethod("getScalaCompileOptions");
      Object options = getOptions.invoke(task);
      options.getClass().getMethod("setAdditionalParameters", List.class)
        .invoke(options, parameters);
    } catch (ReflectiveOperationException e) {
      throw new GradleException("Failed to set Scala compiler flags on " + task.getName(), e);
    }
  }
}
