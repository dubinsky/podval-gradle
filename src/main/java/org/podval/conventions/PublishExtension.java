package org.podval.conventions;

import org.gradle.api.provider.Property;

/** Consumer-facing {@code podvalPublish { }} block. POM fields that differ per repo. */
public abstract class PublishExtension {
  /** GitHub {@code owner/repo}, e.g. {@code dubinsky/xml}. Required. */
  public abstract Property<String> getGitHubRepository();

  /** POM {@code <name>}. Default: {@code project.name}. */
  public abstract Property<String> getName();

  /** POM {@code <inceptionYear>}. Optional. */
  public abstract Property<String> getInceptionYear();

  /** POM organization name. Default: {@code Podval Group}. */
  public abstract Property<String> getOrgName();

  /** POM organization URL. Default: {@code https://www.podval.org}. */
  public abstract Property<String> getOrgUrl();

  /** POM developer email. Default: {@code dub@podval.org}. */
  public abstract Property<String> getDeveloperEmail();
}
