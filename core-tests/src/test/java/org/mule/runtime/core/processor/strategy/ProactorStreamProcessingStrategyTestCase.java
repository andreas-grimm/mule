/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.processor.strategy;

import static java.lang.Integer.MAX_VALUE;
import static org.mule.runtime.core.processor.strategy.ReactorStreamProcessingStrategyFactory.DEFAULT_BUFFER_SIZE;
import static org.mule.runtime.core.processor.strategy.ReactorStreamProcessingStrategyFactory.DEFAULT_SUBSCRIBER_COUNT;
import static org.mule.runtime.core.processor.strategy.ReactorStreamProcessingStrategyFactory.DEFAULT_WAIT_STRATEGY;
import static org.mule.test.allure.AllureConstants.ProcessingStrategiesFeature.PROCESSING_STRATEGIES;
import static org.mule.test.allure.AllureConstants.ProcessingStrategiesFeature.ProcessingStrategiesStory.PROACTOR;

import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.api.processor.strategy.ProcessingStrategy;
import org.mule.runtime.core.processor.strategy.ProactorStreamProcessingStrategyFactory.ProactorStreamProcessingStrategy;

import ru.yandex.qatools.allure.annotations.Features;
import ru.yandex.qatools.allure.annotations.Stories;

@Features(PROCESSING_STRATEGIES)
@Stories(PROACTOR)
public class ProactorStreamProcessingStrategyTestCase extends ProactorProcessingStrategyTestCase {

  public ProactorStreamProcessingStrategyTestCase(Mode mode) {
    super(mode);
  }

  @Override
  protected ProcessingStrategy createProcessingStrategy(MuleContext muleContext, String schedulersNamePrefix) {
    return new ProactorStreamProcessingStrategy(() -> blocking,
                                                DEFAULT_BUFFER_SIZE,
                                                DEFAULT_SUBSCRIBER_COUNT,
                                                DEFAULT_WAIT_STRATEGY,
                                                () -> cpuLight,
                                                () -> blocking,
                                                () -> cpuIntensive,
                                                MAX_VALUE);
  }

}
