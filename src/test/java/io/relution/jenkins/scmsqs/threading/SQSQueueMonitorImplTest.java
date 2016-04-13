/*
 * Copyright 2016 M-Way Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.relution.jenkins.scmsqs.threading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.amazonaws.services.sqs.model.Message;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;

import io.relution.jenkins.scmsqs.interfaces.SQSQueueListener;
import io.relution.jenkins.scmsqs.interfaces.SQSQueueMonitor;
import io.relution.jenkins.scmsqs.net.SQSChannel;


public class SQSQueueMonitorImplTest {

    @Mock
    private ExecutorService     executor;

    @Mock
    private SQSChannel          channel;

    @Mock
    private SQSQueueListener    listener;

    private SQSQueueMonitor     monitor;

    private final List<Message> messages = new ArrayList<>();

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);

        final Message message = new Message();
        this.messages.add(message);

        Mockito.when(this.channel.getMessages()).thenReturn(this.messages);

        Mockito.when(this.listener.getQueueUuid()).thenReturn("a");
        Mockito.when(this.channel.getQueueUuid()).thenReturn("a");

        this.monitor = new SQSQueueMonitorImpl(this.executor, this.channel);
    }

    @Test
    public void shouldThrowIfRegisterNullListener() {
        assertThatThrownBy(new ThrowingCallable() {

            @Override
            public void call() throws Throwable {
                SQSQueueMonitorImplTest.this.monitor.add(null);
            }

        }).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void shouldNotThrowIfUnregisterNullListener() {
        assertThat(this.monitor.remove(null)).isFalse();
        assertThat(this.monitor.isShutDown()).isFalse();
    }

    @Test
    public void shouldNotThrowIfUnregisterUnknown() {
        assertThat(this.monitor.remove(this.listener)).isFalse();
        assertThat(this.monitor.isShutDown()).isFalse();
    }

    @Test
    public void shouldThrowIfWrongQueue() {
        Mockito.when(this.listener.getQueueUuid()).thenReturn("a");
        Mockito.when(this.channel.getQueueUuid()).thenReturn("b");

        final SQSQueueMonitor monitor = new SQSQueueMonitorImpl(this.executor, this.channel);
        Throwable thrown = null;

        try {
            monitor.add(null);
        } catch (final Throwable t) {
            thrown = t;
        }

        assertThat(thrown).isNotNull();
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void shouldStartAndStop() {
        assertThat(this.monitor.add(this.listener)).isTrue();
        Mockito.verify(this.executor).execute(this.monitor);
        assertThat(this.monitor.isShutDown()).isFalse();

        assertThat(this.monitor.add(this.listener)).isFalse();
        Mockito.verifyNoMoreInteractions(this.executor);
        assertThat(this.monitor.isShutDown()).isFalse();

        assertThat(this.monitor.remove(this.listener)).isFalse();
        Mockito.verifyNoMoreInteractions(this.executor);
        assertThat(this.monitor.isShutDown()).isFalse();

        assertThat(this.monitor.remove(this.listener)).isTrue();
        Mockito.verifyNoMoreInteractions(this.executor);
        assertThat(this.monitor.isShutDown()).isTrue();
    }

    @Test
    public void shouldQueryQueue() {
        assertThat(this.monitor.add(this.listener)).isTrue();
        Mockito.verify(this.channel).getQueueUuid();
        Mockito.verify(this.listener).getQueueUuid();
        Mockito.verify(this.executor).execute(this.monitor);

        this.monitor.run();

        Mockito.verify(this.channel).getMessages();

        Mockito.verify(this.listener).handleMessages(this.messages);
        Mockito.verifyNoMoreInteractions(this.listener);

        Mockito.verify(this.channel).deleteMessages(this.messages);
        Mockito.verifyNoMoreInteractions(this.channel);

        Mockito.verify(this.executor, Mockito.times(2)).execute(this.monitor);
    }

    @Test
    public void shouldNotSendDeleteRequestIfResultIsEmpty() {
        final List<Message> messages = Collections.emptyList();
        Mockito.when(this.channel.getMessages()).thenReturn(messages);

        assertThat(this.monitor.add(this.listener)).isTrue();
        Mockito.verify(this.channel).getQueueUuid();
        Mockito.verify(this.listener).getQueueUuid();
        Mockito.verify(this.executor).execute(this.monitor);

        this.monitor.run();

        Mockito.verify(this.channel).getMessages();

        Mockito.verifyNoMoreInteractions(this.listener);
        Mockito.verifyNoMoreInteractions(this.channel);

        Mockito.verify(this.executor, Mockito.times(2)).execute(this.monitor);
    }

    @Test
    public void shouldNotRunIfAlreadyShutDown() {
        assertThat(this.monitor.add(this.listener)).isTrue();
        Mockito.verify(this.channel).getQueueUuid();
        Mockito.verify(this.listener).getQueueUuid();
        Mockito.verify(this.executor).execute(this.monitor);

        this.monitor.shutDown();
        this.monitor.run();

        assertThat(this.monitor.isShutDown()).isTrue();

        Mockito.verifyNoMoreInteractions(this.channel);
        Mockito.verifyNoMoreInteractions(this.listener);

        Mockito.verifyNoMoreInteractions(this.executor);
    }
}
