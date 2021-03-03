/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.extension.internal.runtime.operation;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mule.runtime.api.event.Event;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.util.Reference;
import org.mule.runtime.api.util.concurrent.Latch;
import org.mule.runtime.core.api.event.CoreEvent;
import org.mule.runtime.core.api.processor.Processor;
import org.mule.runtime.core.internal.exception.MessagingException;
import org.mule.runtime.core.internal.message.InternalEvent;
import org.mule.runtime.core.privileged.processor.chain.MessageProcessorChain;
import org.mule.runtime.extension.api.runtime.operation.Result;
import org.mule.runtime.module.extension.api.runtime.privileged.EventedResult;
import org.mule.runtime.module.extension.internal.runtime.execution.SdkInternalContext;
import org.mule.tck.junit4.AbstractMuleContextTestCase;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;
import static org.mule.runtime.api.message.Message.of;
import static reactor.core.publisher.Mono.error;

@RunWith(MockitoJUnitRunner.class)
public class ProcessorChildContextChainExecutorTestCase extends AbstractMuleContextTestCase {

  private static final String TEST_CORRELATION_ID = "messirve";

  @Mock(lenient = true)
  private MessageProcessorChain chain;

  @Mock
  private Processor processor;

  private CoreEvent coreEvent;

  private Latch latch;

  @Before
  public void setUp() throws Exception {
    this.coreEvent = testEvent();
    ((InternalEvent) this.coreEvent).setSdkInternalContext(new SdkInternalContext());
    when(chain.getLocation()).thenReturn(null);
    when(chain.apply(any())).thenAnswer(inv -> Mono.<CoreEvent>from(inv.getArgument(0))
        .map(event -> CoreEvent.builder(event)
            .message(coreEvent.getMessage())
            .variables(coreEvent.getVariables())
            .build()));
    when(chain.getMessageProcessors()).thenReturn(singletonList(processor));
  }

  @Test
  public void testDoProcessSuccessOnce() throws InterruptedException {
    ImmutableProcessorChildContextChainExecutor chainExecutor = new ImmutableProcessorChildContextChainExecutor(coreEvent, chain);

    AtomicInteger successCalls = new AtomicInteger(0);
    AtomicInteger errorCalls = new AtomicInteger(0);
    Reference<String> correlationID = new Reference<>();

    doProcessAndWait(chainExecutor, TEST_CORRELATION_ID, r -> {
      successCalls.incrementAndGet();
      correlationID.set(((EventedResult) r).getEvent().getCorrelationId());
    }, (t, r) -> errorCalls.incrementAndGet());

    assertThat(successCalls.get(), is(1));
    assertThat(errorCalls.get(), is(0));
    assertThat(correlationID.get(), is(TEST_CORRELATION_ID));
  }

  @Test
  public void testDoProcessOnErrorMessagingException() throws InterruptedException, MuleException {
    final String ERROR_PAYLOAD = "ERROR_PAYLOAD";
    doReturn(error(new MessagingException(createStaticMessage(""),
                                          getEventBuilder().message(of(ERROR_PAYLOAD)).build()))).when(chain).apply(any());
    ImmutableProcessorChildContextChainExecutor chainExecutor = new ImmutableProcessorChildContextChainExecutor(coreEvent, chain);

    AtomicInteger successCalls = new AtomicInteger(0);
    AtomicInteger errorCalls = new AtomicInteger(0);
    Reference<Event> errorEvent = new Reference<>();

    doProcessAndWait(chainExecutor, TEST_CORRELATION_ID, r -> successCalls.incrementAndGet(), (t, r) -> {
      errorCalls.incrementAndGet();
      errorEvent.set(((EventedResult) r).getEvent());
    });

    assertThat(successCalls.get(), is(0));
    assertThat(errorCalls.get(), is(1));
    assertThat(errorEvent.get().getMessage().getPayload().getValue(), is(ERROR_PAYLOAD));
    // CHECK
    assertThat(errorEvent.get().getCorrelationId(), is(not(TEST_CORRELATION_ID)));
  }

  @Test
  public void testDoProcessOnErrorGenericException() throws InterruptedException {
    doReturn(error(new RuntimeException())).when(chain).apply(any());
    ImmutableProcessorChildContextChainExecutor chainExecutor = new ImmutableProcessorChildContextChainExecutor(coreEvent, chain);

    AtomicInteger successCalls = new AtomicInteger(0);
    AtomicInteger errorCalls = new AtomicInteger(0);
    Reference<Event> errorEvent = new Reference<>();

    doProcessAndWait(chainExecutor, TEST_CORRELATION_ID, r -> successCalls.incrementAndGet(), (t, r) -> {
      errorCalls.incrementAndGet();
      errorEvent.set(((EventedResult) r).getEvent());
    });

    assertThat(successCalls.get(), is(0));
    assertThat(errorCalls.get(), is(1));
    assertThat(errorEvent.get().getMessage().getPayload().getValue(), is(TEST_PAYLOAD));
    // CHECK
    assertThat(errorEvent.get().getCorrelationId(), is(TEST_CORRELATION_ID));
  }


  private void doProcessAndWait(ImmutableProcessorChildContextChainExecutor chainExecutor, String expectedCorrelationId,
                                Consumer<Result> onSuccess, BiConsumer<Throwable, Result> onError)
      throws InterruptedException {
    latch = new Latch();
    chainExecutor.process(expectedCorrelationId, onSuccess, onError);
    latch.await(300, MILLISECONDS);
  }

}
