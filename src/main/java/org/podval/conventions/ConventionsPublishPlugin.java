package org.podval.conventions;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension;
import org.gradle.plugins.signing.SigningExtension;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Publishing: ScalaDoc {@code javadocJar} before {@code withJavadocJar()}, sources
 * jar, jar duplicates/manifest, Maven publication POM, in-memory signing.
 * Creates a {@code library} publication unless {@code java-gradle-plugin} is applied
 * (that plugin already publishes {@code pluginMaven} plus markers). Does not apply
 * {@code java}, {@code scala}, {@code maven-publish}, or {@code signing}.
 */
public final class ConventionsPublishPlugin implements Plugin<Project> {
  public static final String PLUGIN_ID = "org.podval.conventions.publish";
  public static final String EXTENSION_NAME = "podvalPublish";
  public static final String LIBRARY_PUBLICATION = "library";
  public static final String JAVA_GRADLE_PLUGIN_ID = "java-gradle-plugin";
  public static final String DEFAULT_ORG_NAME = "Podval Group";
  public static final String DEFAULT_ORG_URL = "https://www.podval.org";
  public static final String DEFAULT_DEVELOPER_EMAIL = "dub@podval.org";

  @Override
  public void apply(Project project) {
    PublishExtension extension = project.getExtensions().create(EXTENSION_NAME, PublishExtension.class);
    extension.getOrgName().convention(DEFAULT_ORG_NAME);
    extension.getOrgUrl().convention(DEFAULT_ORG_URL);
    extension.getDeveloperEmail().convention(DEFAULT_DEVELOPER_EMAIL);
    extension.getName().convention(project.provider(project::getName));
    extension.getGitHubRepository().convention(project.getProviders().provider(() -> {
      throw new GradleException(
        "podvalPublish.gitHubRepository is required (e.g. 'dubinsky/xml')"
      );
    }));

    project.getPluginManager().withPlugin("scala", unused -> registerJavadocJarFromScalaDoc(project));
    project.getPluginManager().withPlugin("java", unused -> configureJava(project, extension));
    project.getPluginManager().withPlugin("maven-publish", unused -> configurePublishing(project, extension));
    project.getPluginManager().withPlugin("signing", unused -> configureSigning(project));
    project.getPluginManager().withPlugin(JAVA_GRADLE_PLUGIN_ID, unused ->
      configureGradlePluginMetadata(project, extension)
    );
  }

  private static void registerJavadocJarFromScalaDoc(Project project) {
    if (project.getTasks().getNames().contains("javadocJar")) {
      return;
    }
    project.getTasks().register("javadocJar", Jar.class, jar -> {
      jar.setGroup("build");
      jar.setDescription(
        "Assembles a jar archive containing the ScalaDoc (Maven Central -javadoc classifier)."
      );
      jar.from(project.getTasks().named("scaladoc"));
      jar.getArchiveClassifier().set("javadoc");
    });
  }

  private static void configureJava(Project project, PublishExtension extension) {
    JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);
    java.withSourcesJar();
    java.withJavadocJar();
    project.getTasks().named(JavaPlugin.JAR_TASK_NAME, Jar.class, jar -> {
      jar.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE);
      Provider<String> vendor = extension.getOrgName();
      Provider<String> implVersion = jar.getArchiveVersion();
      String title = project.getName();
      jar.doFirst(task -> {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("Implementation-Title", title);
        attributes.put("Implementation-Version", implVersion.get());
        attributes.put("Implementation-Vendor", vendor.get());
        ((Jar) task).getManifest().attributes(attributes);
      });
    });
    maybeCreateLibraryPublication(project);
  }

  private static void configurePublishing(Project project, PublishExtension extension) {
    PublishingExtension publishing = project.getExtensions().getByType(PublishingExtension.class);
    publishing.getPublications().withType(MavenPublication.class).configureEach(publication ->
      configurePom(publication, extension, project)
    );
    maybeCreateLibraryPublication(project);
  }

  private static void maybeCreateLibraryPublication(Project project) {
    if (project.getPluginManager().hasPlugin(JAVA_GRADLE_PLUGIN_ID)) {
      return;
    }
    if (!project.getPluginManager().hasPlugin("java")) {
      return;
    }
    if (!project.getPluginManager().hasPlugin("maven-publish")) {
      return;
    }
    PublishingExtension publishing = project.getExtensions().getByType(PublishingExtension.class);
    if (publishing.getPublications().findByName(LIBRARY_PUBLICATION) != null) {
      return;
    }
    SoftwareComponent java = project.getComponents().findByName("java");
    if (java == null) {
      return;
    }
    publishing.getPublications().create(LIBRARY_PUBLICATION, MavenPublication.class, publication ->
      publication.from(java)
    );
  }

  private static void configurePom(
    MavenPublication publication,
    PublishExtension extension,
    Project project
  ) {
    Provider<String> gitHubRepository = extension.getGitHubRepository();
    Provider<String> gitHubRepositoryUrl = gitHubRepository.map(repo -> "https://github.com/" + repo);
    publication.getPom().getName().set(extension.getName());
    publication.getPom().getDescription().set(project.provider(project::getDescription));
    publication.getPom().getUrl().set(gitHubRepositoryUrl);
    publication.getPom().getInceptionYear().convention(extension.getInceptionYear());
    publication.getPom().scm(scm -> {
      scm.getUrl().set(gitHubRepositoryUrl);
      scm.getConnection().set(gitHubRepository.map(repo ->
        "scm:git:https://github.com/" + repo + ".git"
      ));
      scm.getDeveloperConnection().set(gitHubRepository.map(repo ->
        "scm:git:ssh://git@github.com/" + repo + ".git"
      ));
    });
    publication.getPom().issueManagement(issues -> {
      issues.getSystem().set("GitHub");
      issues.getUrl().set(gitHubRepositoryUrl.map(url -> url + "/issues"));
    });
    publication.getPom().ciManagement(ci -> {
      ci.getSystem().set("GitHub Actions");
      ci.getUrl().set(gitHubRepositoryUrl.map(url -> url + "/actions"));
    });
    publication.getPom().licenses(licenses -> licenses.license(license -> {
      license.getName().set("Apache-2.0");
      license.getUrl().set("https://www.apache.org/licenses/LICENSE-2.0.txt");
    }));
    publication.getPom().organization(organization -> {
      organization.getName().set(extension.getOrgName());
      organization.getUrl().set(extension.getOrgUrl());
    });
    publication.getPom().developers(developers -> developers.developer(developer -> {
      developer.getId().set("dub");
      developer.getName().set("Leonid Dubinsky");
      developer.getEmail().set(extension.getDeveloperEmail());
      developer.getUrl().set("https://dub.podval.org");
      developer.getOrganization().set(extension.getOrgName());
      developer.getOrganizationUrl().set(extension.getOrgUrl());
      developer.getTimezone().set("-5");
    }));
  }

  private static void configureGradlePluginMetadata(Project project, PublishExtension extension) {
    GradlePluginDevelopmentExtension gradlePlugin =
      project.getExtensions().getByType(GradlePluginDevelopmentExtension.class);
    Provider<String> gitHubRepositoryUrl = extension.getGitHubRepository()
      .map(repo -> "https://github.com/" + repo);
    gradlePlugin.getWebsite().set(gitHubRepositoryUrl);
    gradlePlugin.getVcsUrl().set(gitHubRepositoryUrl.map(url -> url + ".git"));
  }

  private static void configureSigning(Project project) {
    if (!project.getPluginManager().hasPlugin("maven-publish")) {
      project.getPluginManager().withPlugin("maven-publish", unused -> configureSigning(project));
      return;
    }
    SigningExtension signing = project.getExtensions().getByType(SigningExtension.class);
    Provider<String> key = project.getProviders().gradleProperty("signingKey");
    Provider<String> password = project.getProviders().gradleProperty("signingPassword");
    signing.setRequired(key.isPresent());
    signing.useInMemoryPgpKeys(key.getOrNull(), password.getOrNull());
    PublishingExtension publishing = project.getExtensions().getByType(PublishingExtension.class);
    signing.sign(publishing.getPublications());
  }
}
