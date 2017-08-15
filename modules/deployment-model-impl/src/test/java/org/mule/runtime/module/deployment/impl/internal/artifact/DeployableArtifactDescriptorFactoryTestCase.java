/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.runtime.module.deployment.impl.internal.artifact;

import static java.util.Arrays.asList;
import static org.apache.commons.io.FileUtils.toFile;
import static org.hamcrest.Matchers.arrayContainingInAnyOrder;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mule.maven.client.api.MavenClientProvider.discoverProvider;
import static org.mule.runtime.core.api.config.MuleProperties.MULE_HOME_DIRECTORY_PROPERTY;
import static org.mule.runtime.core.api.util.FileUtils.unzip;
import static org.mule.runtime.deployment.model.api.plugin.ArtifactPluginDescriptor.MULE_PLUGIN_CLASSIFIER;
import static org.mule.runtime.module.artifact.api.descriptor.BundleScope.COMPILE;
import org.mule.runtime.api.meta.MuleVersion;
import org.mule.runtime.core.api.registry.SpiServiceRegistry;
import org.mule.runtime.deployment.model.api.DeployableArtifactDescriptor;
import org.mule.runtime.globalconfig.api.GlobalConfigLoader;
import org.mule.runtime.module.artifact.api.descriptor.BundleDependency;
import org.mule.runtime.module.artifact.api.descriptor.ClassLoaderModel;
import org.mule.runtime.module.deployment.impl.internal.application.ApplicationDescriptorFactoryTestCase;
import org.mule.runtime.module.deployment.impl.internal.builder.DeployableFileBuilder;
import org.mule.runtime.module.deployment.impl.internal.builder.JarFileBuilder;
import org.mule.tck.junit4.AbstractMuleTestCase;
import org.mule.tck.junit4.rule.SystemProperty;
import org.mule.tck.junit4.rule.SystemPropertyTemporaryFolder;
import org.mule.tck.util.CompilerUtils;

import java.io.File;
import java.net.URISyntaxException;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public abstract class DeployableArtifactDescriptorFactoryTestCase<D extends DeployableArtifactDescriptor, B extends DeployableFileBuilder>
    extends AbstractMuleTestCase {

  private static File echoTestJarFile;

  private static File getResourceFile(String resource) throws URISyntaxException {
    return new File(ApplicationDescriptorFactoryTestCase.class.getResource(resource).toURI());
  }

  protected static final String ARTIFACT_NAME = "test";

  @BeforeClass
  public static void beforeClass() throws URISyntaxException {
    echoTestJarFile = new CompilerUtils.JarCompiler().compiling(getResourceFile("/org/foo/EchoTest.java"))
        .including(getResourceFile("/test-resource.txt"), "META-INF/MANIFEST.MF")
        .including(getResourceFile("/test-resource.txt"), "README.txt")
        .compile("echo.jar");
  }

  @Rule
  public SystemProperty repositoryLocation = new SystemProperty("muleRuntimeConfig.maven.repositoryLocation",
                                                                discoverProvider(ApplicationDescriptorFactoryTestCase.class
                                                                    .getClassLoader()).getLocalRepositorySuppliers()
                                                                        .environmentMavenRepositorySupplier().get()
                                                                        .getAbsolutePath());

  @Rule
  public TemporaryFolder muleHome = new SystemPropertyTemporaryFolder(MULE_HOME_DIRECTORY_PROPERTY);

  @Before
  public void setUp() throws Exception {
    GlobalConfigLoader.reset();
  }

  @Test
  public void makesConfigFileRelativeToArtifactMuleFolder() throws Exception {
    DeployableFileBuilder artifactFileBuilder = createArtifactFileBuilder()
        .deployedWith("config.resources", "mule/config1.xml,mule/config2.xml");
    unzip(artifactFileBuilder.getArtifactFile(), getArtifactFolder());

    D desc = createArtifactDescriptor();

    String config1Path = new File(getArtifactConfigFolder(), "mule/config1.xml").getAbsolutePath();
    String config2Path = new File(getArtifactConfigFolder(), "mule/config2.xml").getAbsolutePath();
    assertThat(desc.getAbsoluteResourcePaths().length, equalTo(2));
    assertThat(desc.getAbsoluteResourcePaths(), arrayContainingInAnyOrder(config1Path, config2Path));
  }

  @Test
  public void readsSharedLibs() throws Exception {
    DeployableFileBuilder artifactFileBuilder = (DeployableFileBuilder) createArtifactFileBuilder()
        .dependingOnSharedLibrary(new JarFileBuilder("shared", echoTestJarFile));
    unzip(artifactFileBuilder.getArtifactFile(), getArtifactFolder());

    D desc = createArtifactDescriptor();

    assertThat(desc.getClassLoaderModel().getUrls().length, equalTo(2));
    assertThat(toFile(desc.getClassLoaderModel().getUrls()[0]).getPath(),
               equalTo(getArtifactClassesFolder().toString()));
    assertThat(toFile(desc.getClassLoaderModel().getUrls()[1]).getPath(),
               endsWith(getArtifactRootFolder() + "test/repository/org/mule/test/shared/1.0.0/shared-1.0.0.jar"));
    assertThat(desc.getClassLoaderModel().getExportedPackages(), contains("org.foo"));
    assertThat(desc.getClassLoaderModel().getExportedResources(), containsInAnyOrder("META-INF/MANIFEST.MF", "README.txt"));
  }

  @Test
  public void readsRuntimeLibs() throws Exception {
    DeployableFileBuilder artifactFileBuilder = (DeployableFileBuilder) createArtifactFileBuilder()
        .dependingOn(new JarFileBuilder("runtime", echoTestJarFile));
    unzip(artifactFileBuilder.getArtifactFile(), getArtifactFolder());

    D desc = createArtifactDescriptor();

    assertThat(desc.getClassLoaderModel().getUrls().length, equalTo(2));
    assertThat(toFile(desc.getClassLoaderModel().getUrls()[0]).getPath(),
               equalTo(getArtifactClassesFolder().toString()));
    assertThat(desc.getClassLoaderModel().getExportedPackages(), is(empty()));
    assertThat(toFile(desc.getClassLoaderModel().getUrls()[1]).getPath(),
               endsWith(getArtifactRootFolder() + "test/repository/org/mule/test/runtime/1.0.0/runtime-1.0.0.jar"));
  }

  @Test
  public void loadsDescriptorFromJson() throws Exception {
    String artifactPath = getArtifactRootFolder() + "no-dependencies";
    D desc = createArtifactDescriptor(artifactPath);

    assertThat(desc.getMinMuleVersion(), is(new MuleVersion("4.0.0")));
    assertThat(desc.getConfigResources(), hasSize(1));
    assertThat(desc.getConfigResources().get(0), is(getDefaultConfigurationResourceLocation()));

    ClassLoaderModel classLoaderModel = desc.getClassLoaderModel();
    assertThat(classLoaderModel.getDependencies().isEmpty(), is(true));
    assertThat(classLoaderModel.getUrls().length, is(1));
    assertThat(toFile(classLoaderModel.getUrls()[0]).getPath(),
               is(new File(getArtifact(artifactPath), "classes").getAbsolutePath()));

    assertThat(classLoaderModel.getExportedPackages().isEmpty(), is(true));
    assertThat(classLoaderModel.getExportedResources().isEmpty(), is(true));
    assertThat(classLoaderModel.getDependencies().isEmpty(), is(true));
  }

  @Test
  public void loadsDescriptorFromJsonWithCustomConfigFiles() throws Exception {
    String artifactPath = getArtifactRootFolder() + "custom-config-files";
    D desc = createArtifactDescriptor(artifactPath);

    assertThat(desc.getConfigResources(), contains("mule/file1.xml", "mule/file2.xml"));
  }

  @Test
  public void classLoaderModelWithSingleDependency() throws Exception {
    D desc = createArtifactDescriptor(getArtifactRootFolder() + "single-dependency");

    ClassLoaderModel classLoaderModel = desc.getClassLoaderModel();

    assertThat(classLoaderModel.getDependencies(), hasSize(1));
    BundleDependency commonsCollectionDependency = classLoaderModel.getDependencies().iterator().next();
    assertThat(commonsCollectionDependency, commonsCollectionDependencyMatcher());

    assertThat(classLoaderModel.getUrls().length, is(2));
    assertThat(asList(classLoaderModel.getUrls()), hasItem(commonsCollectionDependency.getBundleUri().toURL()));
  }

  @Test
  public void classLoaderModelWithPluginDependency() throws Exception {
    D desc = createArtifactDescriptor(getArtifactRootFolder() + "plugin-dependency");

    ClassLoaderModel classLoaderModel = desc.getClassLoaderModel();

    assertThat(classLoaderModel.getDependencies().size(), is(1));
    assertThat(classLoaderModel.getDependencies(), hasItem(socketsPluginDependencyMatcher()));

    assertThat(classLoaderModel.getUrls().length, is(1));
    assertThat(asList(classLoaderModel.getUrls()), not(hasItem(classLoaderModel.getDependencies().iterator().next())));
  }

  @Test
  public void classLoaderModelWithPluginDependencyWithAnotherPlugin() throws Exception {
    D desc = createArtifactDescriptor(getArtifactRootFolder() + "plugin-dependency-with-another-plugin");

    ClassLoaderModel classLoaderModel = desc.getClassLoaderModel();

    assertThat(classLoaderModel.getDependencies().size(), is(2));
    assertThat(classLoaderModel.getDependencies(), hasItems(httpPluginDependencyMatcher(), httpSocketsDependencyMatcher()));

    assertThat(classLoaderModel.getUrls().length, is(1));
    classLoaderModel.getDependencies().stream()
        .forEach(bundleDependency -> {
          assertThat(asList(classLoaderModel.getUrls()), not(hasItem(bundleDependency.getBundleUri())));
        });
  }

  protected abstract String getArtifactRootFolder();

  protected abstract File getArtifactClassesFolder();

  protected abstract File getArtifactConfigFolder();

  protected abstract File getArtifactFolder();

  protected abstract D createArtifactDescriptor();

  protected abstract B createArtifactFileBuilder();

  protected abstract File getArtifact(String appPath) throws URISyntaxException;

  protected abstract D createArtifactDescriptor(String appPath) throws URISyntaxException;

  protected abstract String getDefaultConfigurationResourceLocation();

  protected ServiceRegistryDescriptorLoaderRepository createDescriptorLoaderRepository() {
    return new ServiceRegistryDescriptorLoaderRepository(new SpiServiceRegistry());
  }

  private Matcher<BundleDependency> commonsCollectionDependencyMatcher() {
    return new BaseMatcher<BundleDependency>() {

      @Override
      public void describeTo(Description description) {
        description.appendText("invalid bundle configuration");
      }

      @Override
      public boolean matches(Object o) {
        if (!(o instanceof BundleDependency)) {
          return false;
        }

        BundleDependency bundleDependency = (BundleDependency) o;
        return bundleDependency.getScope().equals(COMPILE) &&
            !bundleDependency.getDescriptor().getClassifier().isPresent() &&
            bundleDependency.getDescriptor().getArtifactId().equals("commons-collections") &&
            bundleDependency.getDescriptor().getGroupId().equals("commons-collections") &&
            bundleDependency.getDescriptor().getVersion().equals("3.2.2");
      }
    };
  }

  private Matcher<BundleDependency> socketsPluginDependencyMatcher() {
    return new BaseMatcher<BundleDependency>() {

      @Override
      public void describeTo(Description description) {
        description.appendText("invalid bundle configuration");
      }

      @Override
      public boolean matches(Object o) {
        if (!(o instanceof BundleDependency)) {
          return false;
        }

        BundleDependency bundleDependency = (BundleDependency) o;
        return bundleDependency.getScope().equals(COMPILE) &&
            bundleDependency.getDescriptor().getClassifier().isPresent() &&
            bundleDependency.getDescriptor().getClassifier().get().equals(MULE_PLUGIN_CLASSIFIER) &&
            bundleDependency.getDescriptor().getArtifactId().equals("mule-sockets-connector") &&
            bundleDependency.getDescriptor().getGroupId().equals("org.mule.connectors") &&
            bundleDependency.getDescriptor().getVersion().equals("1.0.0-SNAPSHOT");
      }
    };
  }

  private Matcher<BundleDependency> httpPluginDependencyMatcher() {
    return createConnectorMatcher("mule-http-connector");
  }

  private Matcher<BundleDependency> httpSocketsDependencyMatcher() {
    return createConnectorMatcher("mule-sockets-connector");
  }

  private Matcher<BundleDependency> createConnectorMatcher(String artifactId) {
    return new BaseMatcher<BundleDependency>() {

      @Override
      public void describeTo(Description description) {
        description.appendText(" invalid bundle configuration");
      }

      @Override
      public boolean matches(Object o) {
        if (!(o instanceof BundleDependency)) {
          return false;
        }

        BundleDependency bundleDependency = (BundleDependency) o;
        return bundleDependency.getScope().equals(COMPILE) &&
            bundleDependency.getDescriptor().getClassifier().isPresent() &&
            bundleDependency.getDescriptor().getClassifier().get().equals(MULE_PLUGIN_CLASSIFIER) &&
            bundleDependency.getDescriptor().getArtifactId().equals(artifactId) &&
            bundleDependency.getDescriptor().getGroupId().equals("org.mule.connectors") &&
            bundleDependency.getDescriptor().getVersion().equals("1.0.0-SNAPSHOT");
      }
    };
  }
}
