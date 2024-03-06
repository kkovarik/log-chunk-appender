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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import ch.qos.logback.core.spi.AppenderAttachable;
import ch.qos.logback.core.spi.AppenderAttachableImpl;
import cz.kkovarik.logback.util.LoggingEventUtils;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.event.Level;

/**
 * Appender, will split messages and stack traces that are longer than specified length.
 * The length is configurable via maxLength property.
 * It will append sequenceKey to MDC with sequence number of the split message.
 *
 * @author Karel Kovarik
 */
@Getter
@Setter
public class ChunkAppender extends UnsynchronizedAppenderBase<ILoggingEvent>
        implements AppenderAttachable<ILoggingEvent> {
    private AppenderAttachableImpl<ILoggingEvent> aai = new AppenderAttachableImpl<>();
    // configuration
    private boolean enabled = true;
    private int maxLength = 8192;
    private String sequenceKey = "seq";
    private boolean debugEnabled = false;

    @Override
    protected void append(ILoggingEvent iLoggingEvent) {
        if (!isEnabled()) {
            // do nothing, pass it on
            aai.appendLoopOnAppenders(iLoggingEvent);
            return;
        }
        // note it won't work on both message and stack trace, if message is to be splitted, then stack trace will be lost effectively
        // this is edge case that will not be addressed at the moment
        if (shouldSplitMessage(iLoggingEvent)) {
            splitMessage(iLoggingEvent).forEach(aai::appendLoopOnAppenders);
        } else if (shouldSplitStackTrace(iLoggingEvent)) {
            splitStackTrace(iLoggingEvent).forEach(aai::appendLoopOnAppenders);
        } else {
            aai.appendLoopOnAppenders(iLoggingEvent);
        }
    }

    public boolean shouldSplitMessage(ILoggingEvent event) {
        return event.getFormattedMessage().length() > maxLength;
    }

    protected boolean shouldSplitStackTrace(ILoggingEvent event) {
        // stacktrace
        long stackTraceLength = 0;
        if (event.getThrowableProxy() != null) {
            for (StackTraceElementProxy proxy : event.getThrowableProxy().getStackTraceElementProxyArray()) {
                stackTraceLength += proxy.getSTEAsString().length();
            }
        }

        return stackTraceLength > maxLength;
    }

    protected List<ILoggingEvent> splitMessage(ILoggingEvent event) {
        List<String> logMessages = LoggingEventUtils.splitString(event.getFormattedMessage(), getMaxLength());

        List<ILoggingEvent> splitLogEvents = new ArrayList<>(logMessages.size());
        for (int i = 0; i < logMessages.size(); i++) {

            LoggingEvent partition = LoggingEventUtils.clone(event, logMessages.get(i));
            int j = i + 1;
            partition.getMDCPropertyMap().put(getSequenceKey(), "" + j);
            splitLogEvents.add(partition);
        }

        return splitLogEvents;
    }

    protected List<ILoggingEvent> splitStackTrace(ILoggingEvent event) {
        final List<ILoggingEvent> ret = new ArrayList<>();
        final Queue<StackTraceElementProxy> stackTraces = new LinkedList<>();
        Arrays.stream(event.getThrowableProxy().getStackTraceElementProxyArray()).forEach(stackTraces::add);

        if (stackTraces.isEmpty()) {
            log(Level.TRACE, "No stack trace to split");
            return Collections.singletonList(event);
        }
        int i = 0;
        // events cloning
        do {
            i++;
            log(Level.TRACE,"i: " + i + ", stackTraces stack size: " + stackTraces.size());
            LoggingEvent clone = LoggingEventUtils.clone(event);
            long length = 0;
            List<StackTraceElementProxy> stackTraceList = new ArrayList<>();
            // stack trace splitting
            do {
                StackTraceElementProxy proxy = stackTraces.peek();
                length += proxy.getSTEAsString().length();
                if (length < maxLength && stackTraces.size() > 1) {
                    stackTraceList.add(stackTraces.poll());
                } else {
                    stackTraceList.add(stackTraces.poll());
                    if (event.getThrowableProxy() instanceof ThrowableProxy) {
                        // will clone it, but with different stack trace elements (subset)
                        final ThrowableProxy throwableProxyClone = LoggingEventUtils.cloneThrowableProxy((ThrowableProxy) event.getThrowableProxy(), stackTraceList);
                        log(Level.TRACE,"Adding stack trace of size: %s, with length: %d".formatted(stackTraceList.size(), length));
                        clone.setThrowableProxy(throwableProxyClone);
                        clone.getMDCPropertyMap().put(getSequenceKey(), "" + i);
                    } else {
                        log(Level.WARN, "Cannot clone throwable proxy, it is not instance of ThrowableProxy");
                        return Collections.singletonList(event); // cannot clone, but we can still return original event
                    }
                    break;
                }
            } while (true);
            ret.add(clone);
        } while (!stackTraces.isEmpty());

        log(Level.TRACE, "Returning %s split stack trace events".formatted(ret.size()));
        return ret;
    }

    @Override
    public void addAppender(Appender<ILoggingEvent> appender) {
        addInfo("Attaching appender named [" + appender.getName() + "] to SplittingAppender.");
        aai.addAppender(appender);
    }

    @Override
    public Iterator<Appender<ILoggingEvent>> iteratorForAppenders() {
        return aai.iteratorForAppenders();
    }

    @Override
    public Appender<ILoggingEvent> getAppender(String s) {
        return aai.getAppender(s);
    }

    @Override
    public boolean isAttached(Appender<ILoggingEvent> appender) {
        return aai.isAttached(appender);
    }

    @Override
    public void detachAndStopAllAppenders() {
        aai.detachAndStopAllAppenders();
    }

    @Override
    public boolean detachAppender(Appender<ILoggingEvent> appender) {
        return aai.detachAppender(appender);
    }

    @Override
    public boolean detachAppender(String s) {
        return aai.detachAppender(s);
    }

    private void log(Level level, String message) {
        if (debugEnabled) {
            System.out.println(level + ": " + message);
        }
    }
}
