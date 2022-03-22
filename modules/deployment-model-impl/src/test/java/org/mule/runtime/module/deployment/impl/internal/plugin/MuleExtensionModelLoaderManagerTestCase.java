/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.runtime.module.deployment.impl.internal.plugin;

import static java.nio.file.Files.newBufferedWriter;
import static java.util.Collections.singleton;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.deployment.model.internal.artifact.extension.MuleExtensionModelLoaderManager;
import org.mule.runtime.extension.api.loader.ExtensionLoadingContext;
import org.mule.runtime.extension.api.loader.ExtensionModelLoader;
import org.mule.runtime.extension.api.loader.ExtensionModelLoaderProvider;
import org.mule.runtime.module.artifact.api.classloader.ArtifactClassLoader;
import org.mule.runtime.module.artifact.api.plugin.LoaderDescriber;
import org.mule.tck.junit4.AbstractMuleTestCase;
import org.mule.tck.size.SmallTest;

import java.io.BufferedWriter;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Set;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@SmallTest
public class MuleExtensionModelLoaderManagerTestCase extends AbstractMuleTestCase {

  private static final String ID = "ID";

  @Rule
  public MockitoRule mockitorule = MockitoJUnit.rule();

  private final LoaderDescriber loaderDescriber = new LoaderDescriber(ID);
  @Mock
  private ArtifactClassLoader containerClassLoader;
  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @After
  public void after() {
    verify(containerClassLoader).getClassLoader();
  }

  @Test
  public void noExtensionModelLoaderRegistered() throws MuleException {
    URLClassLoader emptyClassLoader = new URLClassLoader(new URL[0], getLauncherClassLoader(this.getClass().getClassLoader()));
    when(containerClassLoader.getClassLoader()).thenReturn(emptyClassLoader);

    MuleExtensionModelLoaderManager manager = new MuleExtensionModelLoaderManager(containerClassLoader);
    manager.start();

    Optional<ExtensionModelLoader> loader = manager.getExtensionModelLoader(loaderDescriber);
    assertThat(loader.isPresent(), is(false));
  }

  @Test
  public void getExtensionModelLoaderRegistered() throws Exception {
    File serviceFolder = new File(temporaryFolder.newFolder("META-INF"), "services");
    assertThat(serviceFolder.mkdirs(), is(true));
    File serviceFile = new File(serviceFolder, ExtensionModelLoaderProvider.class.getCanonicalName());
    try (BufferedWriter writer = newBufferedWriter(Paths.get(serviceFile.toURI()))) {
      writer.write(TestExtensionModelLoaderProvider.class.getName());
      writer.newLine();
    }
    when(containerClassLoader.getClassLoader())
        .thenReturn(new URLClassLoader(new URL[] {temporaryFolder.getRoot().toURI().toURL()}, this.getClass().getClassLoader()));

    MuleExtensionModelLoaderManager manager = new MuleExtensionModelLoaderManager(containerClassLoader);
    manager.start();

    Optional<ExtensionModelLoader> loader = manager.getExtensionModelLoader(loaderDescriber);
    assertThat(loader.isPresent(), is(true));
    assertThat(loader.get(), instanceOf(TestExtensionModelLoader.class));
  }

  private ClassLoader getLauncherClassLoader(ClassLoader classLoader) {
    ClassLoader launcherClassLoader = classLoader;
    while (classLoader != null) {
      launcherClassLoader = classLoader;
      classLoader = classLoader.getParent();
    }
    return launcherClassLoader;
  }

  public static class TestExtensionModelLoader extends ExtensionModelLoader {

    @Override
    public String getId() {
      return ID;
    }

    @Override
    protected void declareExtension(ExtensionLoadingContext extensionLoadingContext) {}

  }

  public static class TestExtensionModelLoaderProvider implements ExtensionModelLoaderProvider {

    @Override
    public Set<ExtensionModelLoader> getExtensionModelLoaders() {
      return singleton(new TestExtensionModelLoader());
    }
  }

}
