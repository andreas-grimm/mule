/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.extension.mule.internal.loader.parser;

import static org.mule.runtime.api.component.TypedComponentIdentifier.ComponentType.OPERATION_DEF;
import static org.mule.runtime.extension.api.dsl.syntax.DslSyntaxUtils.getSanitizedElementName;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Optional.empty;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import org.mule.metadata.api.TypeLoader;
import org.mule.metadata.api.model.MetadataType;
import org.mule.runtime.api.meta.model.ExternalLibraryModel;
import org.mule.runtime.api.meta.model.ModelProperty;
import org.mule.runtime.api.meta.model.deprecated.DeprecationModel;
import org.mule.runtime.api.meta.model.notification.NotificationModel;
import org.mule.runtime.ast.api.ArtifactAst;
import org.mule.runtime.ast.api.ComponentAst;
import org.mule.runtime.ast.internal.model.ExtensionModelHelper;
import org.mule.runtime.extension.api.property.SinceMuleVersionModelProperty;
import org.mule.runtime.module.extension.internal.loader.java.property.ExceptionHandlerModelProperty;
import org.mule.runtime.module.extension.internal.loader.parser.ConfigurationModelParser;
import org.mule.runtime.module.extension.internal.loader.parser.ConnectionProviderModelParser;
import org.mule.runtime.module.extension.internal.loader.parser.ErrorModelParser;
import org.mule.runtime.module.extension.internal.loader.parser.ExtensionModelParser;
import org.mule.runtime.module.extension.internal.loader.parser.FunctionModelParser;
import org.mule.runtime.module.extension.internal.loader.parser.OperationModelParser;
import org.mule.runtime.module.extension.internal.loader.parser.SourceModelParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * {@link ExtensionModelParser} implementation for Mule SDK extensions
 *
 * @since 4.5.0
 */
public abstract class MuleSdkExtensionModelParser extends BaseMuleSdkExtensionModelParser implements ExtensionModelParser {

  private final ArtifactAst ast;
  private final TypeLoader typeLoader;
  private final List<OperationModelParser> operationModelParsers;
  private final ExtensionModelHelper extensionModelHelper;

  public MuleSdkExtensionModelParser(ArtifactAst ast,
                                     TypeLoader typeLoader,
                                     ExtensionModelHelper extensionModelHelper) {
    this.ast = ast;
    this.typeLoader = typeLoader;
    this.extensionModelHelper = extensionModelHelper;
    operationModelParsers = computeOperationModelParsers();
  }

  @Override
  public List<ModelProperty> getAdditionalModelProperties() {
    return emptyList();
  }

  @Override
  public List<ConfigurationModelParser> getConfigurationParsers() {
    return emptyList();
  }

  @Override
  public List<OperationModelParser> getOperationModelParsers() {
    return operationModelParsers;
  }

  @Override
  public List<SourceModelParser> getSourceModelParsers() {
    return emptyList();
  }

  @Override
  public List<ConnectionProviderModelParser> getConnectionProviderModelParsers() {
    return emptyList();
  }

  @Override
  public List<FunctionModelParser> getFunctionModelParsers() {
    return emptyList();
  }

  @Override
  public List<ErrorModelParser> getErrorModelParsers() {
    return emptyList();
  }

  @Override
  public List<ExternalLibraryModel> getExternalLibraryModels() {
    return emptyList();
  }

  @Override
  public Optional<ExceptionHandlerModelProperty> getExtensionHandlerModelProperty() {
    return empty();
  }

  @Override
  public Optional<DeprecationModel> getDeprecationModel() {
    return empty();
  }

  @Override
  public List<MetadataType> getExportedTypes() {
    return emptyList();
  }

  @Override
  public List<String> getExportedResources() {
    return emptyList();
  }

  @Override
  public List<MetadataType> getImportedTypes() {
    return emptyList();
  }

  @Override
  public List<String> getPrivilegedExportedArtifacts() {
    return emptyList();
  }

  @Override
  public List<String> getPrivilegedExportedPackages() {
    return emptyList();
  }

  @Override
  public Map<MetadataType, List<MetadataType>> getSubTypes() {
    return emptyMap();
  }

  @Override
  public List<NotificationModel> getNotificationModels() {
    return emptyList();
  }

  @Override
  public Optional<SinceMuleVersionModelProperty> getSinceMuleVersionModelProperty() {
    return empty();
  }

  /**
   * @param ast the {@link ArtifactAst} representing the extension.
   * @return a {@link Stream} with the top level elements {@link ComponentAst} extracted from the {@code ast}.
   */
  protected abstract Stream<ComponentAst> getTopLevelElements(ArtifactAst ast);

  private List<OperationModelParser> computeOperationModelParsers() {
    final Map<String, MuleSdkOperationModelParserSdk> operationParsersByName =
        getTopLevelElements(ast)
            .filter(c -> c.getComponentType() == OPERATION_DEF)
            .map(c -> new MuleSdkOperationModelParserSdk(c, getNamespace(), typeLoader, extensionModelHelper))
            .collect(toMap(c -> getSanitizedElementName(c::getName), identity()));

    // Some characteristics of the operation model parsers require knowledge about the other operation model parsers
    operationParsersByName.values()
        .forEach(operationModelParser -> operationModelParser.computeCharacteristics(operationParsersByName));

    return new ArrayList<>(operationParsersByName.values());
  }
}
