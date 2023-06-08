/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.container.internal;

import static org.mule.runtime.api.util.Preconditions.checkArgument;
import static org.mule.runtime.container.internal.ContainerClassLoaderCreatorUtils.getLookupPolicy;

import static java.util.Collections.emptyList;

import org.mule.runtime.container.api.ModuleRepository;
import org.mule.runtime.container.api.MuleModule;
import org.mule.runtime.core.internal.util.EnumerationAdapter;
import org.mule.runtime.module.artifact.api.classloader.ArtifactClassLoader;
import org.mule.runtime.module.artifact.api.classloader.ClassLoaderLookupPolicy;
import org.mule.runtime.module.artifact.api.classloader.MuleArtifactClassLoader;
import org.mule.runtime.module.artifact.api.descriptor.ArtifactDescriptor;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;

/**
 * Default implementation of {@link PreFilteredContainerClassLoaderCreator}.
 *
 * @since 4.5
 */
public class DefaultPreFilteredContainerClassLoaderCreator implements PreFilteredContainerClassLoaderCreator {

  private final ModuleRepository moduleRepository;

  public DefaultPreFilteredContainerClassLoaderCreator(ModuleRepository moduleRepository) {
    checkArgument(moduleRepository != null, "moduleRepository cannot be null");

    this.moduleRepository = moduleRepository;
  }

  @Override
  public List<MuleModule> getMuleModules() {
    return moduleRepository.getModules();
  }

  @Override
  public Set<String> getBootPackages() {
    return BOOT_PACKAGES;
  }

  @Override
  public ArtifactClassLoader getPreFilteredContainerClassLoader(ArtifactDescriptor artifactDescriptor,
                                                                ClassLoader parentClassLoader) {
    return new MuleContainerClassLoader(artifactDescriptor, new URL[0], parentClassLoader,
                                        getLookupPolicy(parentClassLoader, getMuleModules(), getBootPackages()));
  }

  @Override
  public void close() throws Exception {
    // Nothing to do
  }

  private static final class MuleContainerClassLoader extends MuleArtifactClassLoader {

    static {
      registerAsParallelCapable();
    }

    private MuleContainerClassLoader(ArtifactDescriptor artifactDescriptor, URL[] urls, ClassLoader parent,
                                     ClassLoaderLookupPolicy lookupPolicy) {
      super("container", artifactDescriptor, urls, parent, lookupPolicy);
    }

    @Override
    public URL findResource(String name) {
      // Container classLoader is just an adapter, it does not owns any resource
      return null;
    }

    @Override
    public Enumeration<URL> findResources(String name) throws IOException {
      // Container classLoader is just an adapter, it does not owns any resource
      return new EnumerationAdapter<>(emptyList());
    }
  }
}
