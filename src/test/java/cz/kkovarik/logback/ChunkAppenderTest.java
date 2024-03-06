/**
 * MIT License
 * <p>
 * Copyright (c) 2024 Karel Kovarik
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package cz.kkovarik.logback;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.HashMap;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.spi.AppenderAttachableImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;


class ChunkAppenderTest {

    private ChunkAppender tested = new ChunkAppender();

    @Mock
    private AppenderAttachableImpl<ILoggingEvent> appenderAttachableMock = Mockito.mock(AppenderAttachableImpl.class);
    @BeforeEach
    void setUpBeforeClass() throws Exception {
        // configure appender
        tested.setMaxLength(100);
        tested.setSequenceKey("seq");
        tested.setEnabled(true);
        // attach mock
        Mockito.reset(appenderAttachableMock);
        tested.setAai(appenderAttachableMock);
    }

    @Test
    void testSplitMessage_ok() {
        // config
        tested.setMaxLength(50);
        // prepare
        String message = "This is exactly one hundred characters long message that should be split into multiple log entries..";
        // execute
        tested.append(createLoggingEvent(message));
        // verify
        verify(appenderAttachableMock, times(2)).appendLoopOnAppenders(any(ILoggingEvent.class));
    }

    @Test
    void testSplitMessage_long_enough() {
        // config
        tested.setMaxLength(101);
        // prepare
        String message = "This is exactly one hundred characters long message that should be split into multiple log entries..";
        // execute
        tested.append(createLoggingEvent(message));
        // verify
        verify(appenderAttachableMock, times(1)).appendLoopOnAppenders(any(ILoggingEvent.class));
    }

    @Test
    void testSplitStacktrace() {
        // config
        tested.setMaxLength(100);
        // prepare
        String message = "This is exactly one hundred characters long message that should be split into multiple log entries..";
        Exception e = new RuntimeException("Long stack trace");
        // execute
        var event = createLoggingEvent(message);
        event.setThrowableProxy(new ThrowableProxy(e)); // will create something long
        tested.append(event);
        // verify
        verify(appenderAttachableMock, atLeast(2)).appendLoopOnAppenders(any(ILoggingEvent.class));
    }

    @Test
    void testSplitStacktrace_should_not() {
        // config
        tested.setMaxLength(Integer.MAX_VALUE);
        // prepare
        String message = "This is exactly one hundred characters long message that should be split into multiple log entries..";
        Exception e = new RuntimeException("Long stack trace");
        // execute
        var event = createLoggingEvent(message);
        event.setThrowableProxy(new ThrowableProxy(e)); // will create something long
        tested.append(event);
        // verify
        verify(appenderAttachableMock, times(1)).appendLoopOnAppenders(any(ILoggingEvent.class));
    }

    private LoggingEvent createLoggingEvent(String message) {
        LoggingEvent event = new LoggingEvent();
        event.setMessage(message);
        event.setMDCPropertyMap(new HashMap<>());
        event.setLoggerContext(new LoggerContext());
        return event;
    }
}