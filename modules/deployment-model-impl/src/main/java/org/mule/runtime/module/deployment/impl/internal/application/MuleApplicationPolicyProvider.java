/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.runtime.module.deployment.impl.internal.application;

import static java.lang.Integer.compare;
import static java.lang.String.format;
import static java.util.Optional.of;
import static org.mule.runtime.api.util.Preconditions.checkArgument;
import static org.mule.runtime.http.policy.api.SourcePolicyAwareAttributes.noAttributes;

import org.mule.runtime.api.lifecycle.Disposable;
import org.mule.runtime.api.lifecycle.InitialisationException;
import org.mule.runtime.core.api.policy.Policy;
import org.mule.runtime.core.api.policy.PolicyParametrization;
import org.mule.runtime.core.api.policy.PolicyProvider;
import org.mule.runtime.deployment.model.api.application.Application;
import org.mule.runtime.deployment.model.api.policy.PolicyRegistrationException;
import org.mule.runtime.deployment.model.api.policy.PolicyTemplate;
import org.mule.runtime.deployment.model.api.policy.PolicyTemplateDescriptor;
import org.mule.runtime.module.deployment.impl.internal.policy.ApplicationPolicyInstance;
import org.mule.runtime.module.deployment.impl.internal.policy.PolicyInstanceProviderFactory;
import org.mule.runtime.module.deployment.impl.internal.policy.PolicyTemplateFactory;
import org.mule.runtime.policy.api.AttributeAwarePointcut;
import org.mule.runtime.policy.api.PolicyAwareAttributes;
import org.mule.runtime.policy.api.PolicyPointcutParameters;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Provides policy management and provision for Mule applications
 */
public class MuleApplicationPolicyProvider implements ApplicationPolicyProvider, PolicyProvider, Disposable {

  private final PolicyTemplateFactory policyTemplateFactory;
  private final PolicyInstanceProviderFactory policyInstanceProviderFactory;
  private final List<RegisteredPolicyTemplate> registeredPolicyTemplates = new LinkedList<>();
  private List<RegisteredPolicyInstanceProvider> registeredPolicyInstanceProviders = new LinkedList<>(); // TODO this has stopped
                                                                                                         // being final., WAT
  private PolicyAwareAttributes sourcePolicyAwareAttributes = noAttributes();
  private Application application;

  private Runnable policiesChangedCallback = () -> {
  };

  /**
   * Creates a new provider
   *
   * @param policyTemplateFactory         used to create the policy templates for the application. Non null.
   * @param policyInstanceProviderFactory used to create the policy instances for the application. Non null.
   */
  public MuleApplicationPolicyProvider(PolicyTemplateFactory policyTemplateFactory,
                                       PolicyInstanceProviderFactory policyInstanceProviderFactory) {
    this.policyTemplateFactory = policyTemplateFactory;
    this.policyInstanceProviderFactory = policyInstanceProviderFactory;
  }

  @Override
  public synchronized void addPolicy(PolicyTemplateDescriptor policyTemplateDescriptor, PolicyParametrization parametrization)
      throws PolicyRegistrationException {
    try {
      checkArgument(application != null, "application was not configured on the policy provider");

      if (registeredPolicyInstanceProviders.stream().anyMatch(isPolicy(parametrization))) {
        throw new IllegalArgumentException(createPolicyAlreadyRegisteredError(parametrization.getId()));
      }

      Optional<RegisteredPolicyTemplate> registeredPolicyTemplate = xxxNoSeQueHacexxx(policyTemplateDescriptor);

      ApplicationPolicyInstance applicationPolicyInstance =
          createAndInitPolicyInstance(parametrization, registeredPolicyTemplate);

      RegisteredPolicyInstanceProvider registeredPolicyInstanceProvider =
          new RegisteredPolicyInstanceProvider(applicationPolicyInstance, parametrization.getId());

      registeredPolicyInstanceProviders
          .add(registeredPolicyInstanceProvider);
      registeredPolicyInstanceProviders.sort(null);
      registeredPolicyTemplate.get().count++;

      policiesChangedCallback.run();

    } catch (Exception e) {
      throw new PolicyRegistrationException(createPolicyRegistrationError(parametrization.getId()), e);
    }
  }

  private Predicate<RegisteredPolicyInstanceProvider> isPolicy(PolicyParametrization parametrization) {
    return p -> p.getPolicyId().equals(parametrization.getId());
  }

  private Optional<RegisteredPolicyTemplate> xxxNoSeQueHacexxx(PolicyTemplateDescriptor policyTemplateDescriptor) {
    Optional<RegisteredPolicyTemplate> registeredPolicyTemplate = findRegistered(policyTemplateDescriptor);

    if (!registeredPolicyTemplate.isPresent()) {
      PolicyTemplate policyTemplate = policyTemplateFactory.createArtifact(application, policyTemplateDescriptor);
      registeredPolicyTemplate = of(new RegisteredPolicyTemplate(policyTemplate));
      registeredPolicyTemplates.add(registeredPolicyTemplate.get());
    }
    return registeredPolicyTemplate;
  }

  private ApplicationPolicyInstance createAndInitPolicyInstance(PolicyParametrization parametrization,
                                                                Optional<RegisteredPolicyTemplate> registeredPolicyTemplate)
      throws InitialisationException {
    ApplicationPolicyInstance applicationPolicyInstance = policyInstanceProviderFactory
        .create(application, registeredPolicyTemplate.get().policyTemplate, parametrization);

    applicationPolicyInstance.initialise();

    return applicationPolicyInstance;
  }

  @Override
  public void updatePolicyOrder(PolicyTemplateDescriptor policyTemplateDescriptor, PolicyParametrization parametrization)
      throws PolicyRegistrationException {

    Optional<RegisteredPolicyInstanceProvider> instanceProvider = registeredPolicyInstanceProviders.stream()
        .filter(ip -> compareParametrizations(ip.getApplicationPolicyInstance().getParametrization(), parametrization))
        .findFirst();

    if (instanceProvider.isPresent()) {
      instanceProvider.get().applicationPolicyInstance.updateOrder(parametrization.getOrder());
      registeredPolicyInstanceProviders.sort(null); // todo from L118 to here it's only to fix the ordering stuff.
      policiesChangedCallback.run();
    }
  }

  @Override
  public void updatePolicy(PolicyTemplateDescriptor policyTemplateDescriptor, PolicyParametrization parametrization)
      throws PolicyRegistrationException {

    // todo this should be a precondition check.
    // if (registeredPolicyInstanceProviders.stream().anyMatch(isPolicy(parametrization))) {
    // throw new IllegalArgumentException(createPolicyAlreadyRegisteredError(parametrization.getId()));
    // }

    Optional<RegisteredPolicyTemplate> registeredPolicyTemplate = xxxNoSeQueHacexxx(policyTemplateDescriptor);

    try {
      ApplicationPolicyInstance applicationPolicyInstance = null;
      applicationPolicyInstance = createAndInitPolicyInstance(parametrization, registeredPolicyTemplate);
      RegisteredPolicyInstanceProvider newPolicy =
          new RegisteredPolicyInstanceProvider(applicationPolicyInstance, parametrization.getId());

      RegisteredPolicyInstanceProvider outdatedPolicy =
          registeredPolicyInstanceProviders.stream().filter(isPolicy(parametrization)).findFirst().get();
      List<RegisteredPolicyInstanceProvider> newRegisteredPolicyInstanceProviders =
          new ArrayList<>(registeredPolicyInstanceProviders);
      newRegisteredPolicyInstanceProviders.remove(outdatedPolicy);
      newRegisteredPolicyInstanceProviders.add(newPolicy);
      newRegisteredPolicyInstanceProviders.sort(null);
      registeredPolicyInstanceProviders = newRegisteredPolicyInstanceProviders;

      policiesChangedCallback.run();

      outdatedPolicy.getApplicationPolicyInstance().dispose(); // todo dispose outside the chain. perhaps async?

    } catch (InitialisationException e) {
      throw new RuntimeException("idk, smth failed", e);
    }

  }

  // TODO check template version / pointcuts / etc
  private boolean compareParametrizations(PolicyParametrization parametrization, PolicyParametrization newParametrization) {
    return parametrization.getId().equals(newParametrization.getId()) &&
        parametrization.getParameters().equals(newParametrization.getParameters()) &&
        parametrization.getOrder() != newParametrization.getOrder();
  }

  private Optional<RegisteredPolicyTemplate> findRegistered(PolicyTemplateDescriptor policyTemplateDescriptor) {
    return registeredPolicyTemplates.stream()
        .filter(p -> p.policyTemplate.getDescriptor().getBundleDescriptor().getGroupId()
            .equals(policyTemplateDescriptor.getBundleDescriptor().getGroupId()) &&
            p.policyTemplate.getDescriptor().getBundleDescriptor().getArtifactId()
                .equals(policyTemplateDescriptor.getBundleDescriptor().getArtifactId())
            &&
            p.policyTemplate.getDescriptor().getBundleDescriptor().getVersion()
                .equals(policyTemplateDescriptor.getBundleDescriptor().getVersion()))
        .findAny();
  }

  @Override
  public synchronized boolean removePolicy(String parametrizedPolicyId) {
    Optional<RegisteredPolicyInstanceProvider> registeredPolicyInstanceProvider = registeredPolicyInstanceProviders.stream()
        .filter(p -> p.getPolicyId().equals(parametrizedPolicyId)).findFirst();

    registeredPolicyInstanceProvider.ifPresent(provider -> {

      registeredPolicyInstanceProviders.remove(provider);

      // Run callback before disposing the policy to be able to dispose Composite Policies before policy schedulers are shutdown
      policiesChangedCallback.run();

      provider.getApplicationPolicyInstance().dispose();

      Optional<RegisteredPolicyTemplate> registeredPolicyTemplate = registeredPolicyTemplates.stream()
          .filter(p -> p.policyTemplate.equals(provider.getApplicationPolicyInstance().getPolicyTemplate()))
          .findFirst();

      if (!registeredPolicyTemplate.isPresent()) {
        throw new IllegalStateException("Cannot find registered policy template");
      }

      registeredPolicyTemplate.get().count--;
      if (registeredPolicyTemplate.get().count == 0) {
        application.getRegionClassLoader()
            .removeClassLoader(registeredPolicyTemplate.get().policyTemplate.getArtifactClassLoader());
        registeredPolicyTemplate.get().policyTemplate.dispose();
        registeredPolicyTemplates.remove(registeredPolicyTemplate.get());
      }
    });

    return registeredPolicyInstanceProvider.isPresent();
  }

  @Override
  public synchronized boolean isPoliciesAvailable() {
    return !registeredPolicyInstanceProviders.isEmpty();
  }

  @Override
  public boolean isSourcePoliciesAvailable() {
    return registeredPolicyInstanceProviders
        .stream()
        .anyMatch(pip -> pip.getApplicationPolicyInstance().getSourcePolicy().isPresent());
  }

  @Override
  public boolean isOperationPoliciesAvailable() {
    return registeredPolicyInstanceProviders
        .stream()
        .anyMatch(pip -> pip.getApplicationPolicyInstance().getOperationPolicy().isPresent());
  }

  @Override
  public void onPoliciesChanged(Runnable policiesChangedCallback) {
    this.policiesChangedCallback = () -> {
      policiesChangedCallback.run();
      updatePolicyAwareAttributes();
    };
  }

  private synchronized void updatePolicyAwareAttributes() {
    sourcePolicyAwareAttributes = registeredPolicyInstanceProviders.stream()
        .filter(pip -> pip.getApplicationPolicyInstance().getPointcut() instanceof AttributeAwarePointcut)
        .map(pip -> ((AttributeAwarePointcut) pip.getApplicationPolicyInstance().getPointcut()).sourcePolicyAwareAttributes())
        .reduce(noAttributes(), PolicyAwareAttributes::merge);
  }

  @Override // TODO shouldn't this have a RW lock with the other editing this list?
  public List<Policy> findSourceParameterizedPolicies(PolicyPointcutParameters policyPointcutParameters) {
    List<Policy> policies = new ArrayList<>();

    if (!registeredPolicyInstanceProviders.isEmpty()) {
      for (RegisteredPolicyInstanceProvider registeredPolicyInstanceProvider : registeredPolicyInstanceProviders) {
        if (registeredPolicyInstanceProvider.getApplicationPolicyInstance().getPointcut().matches(policyPointcutParameters)) {
          if (registeredPolicyInstanceProvider.getApplicationPolicyInstance().getSourcePolicy().isPresent()) {
            policies.add(registeredPolicyInstanceProvider.getApplicationPolicyInstance().getSourcePolicy().get());
          }
        }
      }
    }

    return policies;
  }

  @Override
  public synchronized PolicyAwareAttributes sourcePolicyAwareAttributes() {
    return sourcePolicyAwareAttributes;
  }

  @Override
  public List<Policy> findOperationParameterizedPolicies(PolicyPointcutParameters policyPointcutParameters) {
    List<Policy> policies = new ArrayList<>();

    if (!registeredPolicyInstanceProviders.isEmpty()) {
      for (RegisteredPolicyInstanceProvider registeredPolicyInstanceProvider : registeredPolicyInstanceProviders) {
        if (registeredPolicyInstanceProvider.getApplicationPolicyInstance().getPointcut().matches(policyPointcutParameters)) {
          if (registeredPolicyInstanceProvider.getApplicationPolicyInstance().getOperationPolicy().isPresent()) {
            policies.add(registeredPolicyInstanceProvider.getApplicationPolicyInstance().getOperationPolicy().get());
          }
        }
      }
    }

    return policies;
  }

  @Override
  public void dispose() {

    for (RegisteredPolicyInstanceProvider registeredPolicyInstanceProvider : registeredPolicyInstanceProviders) {
      registeredPolicyInstanceProvider.getApplicationPolicyInstance().dispose();
    }
    registeredPolicyInstanceProviders.clear();

    for (RegisteredPolicyTemplate registeredPolicyTemplate : registeredPolicyTemplates) {
      try {
        registeredPolicyTemplate.policyTemplate.dispose();
      } catch (RuntimeException e) {
        // Ignore and continue
      }


      registeredPolicyTemplates.clear();
    }
  }

  public void setApplication(Application application) {
    this.application = application;
  }

  static String createPolicyAlreadyRegisteredError(String policyId) {
    return format("Policy already registered: '%s'", policyId);
  }

  static String createPolicyRegistrationError(String policyId) {
    return format("Error occured registering policy '%s'", policyId);
  }

  private static class RegisteredPolicyTemplate {

    private volatile int count;
    private final PolicyTemplate policyTemplate;

    private RegisteredPolicyTemplate(PolicyTemplate policyTemplate) {
      this.policyTemplate = policyTemplate;
    }
  }

  private static class RegisteredPolicyInstanceProvider implements Comparable<RegisteredPolicyInstanceProvider> {

    private final ApplicationPolicyInstance applicationPolicyInstance;
    private final String policyId;

    public RegisteredPolicyInstanceProvider(ApplicationPolicyInstance applicationPolicyInstance, String policyId) {
      this.applicationPolicyInstance = applicationPolicyInstance;
      this.policyId = policyId;
    }

    @Override
    public int compareTo(RegisteredPolicyInstanceProvider registeredPolicyInstanceProvider) {
      return compare(applicationPolicyInstance.getOrder(), registeredPolicyInstanceProvider.applicationPolicyInstance.getOrder());
    }

    public ApplicationPolicyInstance getApplicationPolicyInstance() {
      return applicationPolicyInstance;
    }

    public String getPolicyId() {
      return policyId;
    }
  }
}
