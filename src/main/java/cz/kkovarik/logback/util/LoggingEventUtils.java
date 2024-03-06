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

package cz.kkovarik.logback.util;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.classic.spi.ThrowableProxy;
import lombok.experimental.UtilityClass;
import org.slf4j.Marker;

/**
 * @author Karel Kovarik
 */
@UtilityClass
public class LoggingEventUtils {
    public static LoggingEvent clone(ILoggingEvent event) {
        return clone(event, event.getFormattedMessage());
    }

    public static LoggingEvent clone(ILoggingEvent event, String message) {
        LoggingEvent clone = new LoggingEvent();

        clone.setLevel(event.getLevel());
        clone.setLoggerName(event.getLoggerName());
        clone.setTimeStamp(event.getTimeStamp());
        clone.setLoggerContextRemoteView(event.getLoggerContextVO());
        clone.setThreadName(event.getThreadName());

        final List<Marker> eventMarkerList = event.getMarkerList();
        if (null != eventMarkerList && !eventMarkerList.isEmpty()) {
            eventMarkerList.forEach(clone::addMarker);
        }

        if (event.hasCallerData()) {
            clone.setCallerData(event.getCallerData());
        }

        clone.setMDCPropertyMap(new HashMap<>()); // always populate MDC, so its not null
        if (null != event.getMDCPropertyMap() && !event.getMDCPropertyMap().isEmpty()) {
            clone.getMDCPropertyMap().putAll(event.getMDCPropertyMap());
        }

        // will NOT clone throwableProxy, as it can be set just once

        clone.setMessage(message);
        return clone;
    }

    public static ThrowableProxy cloneThrowableProxy(ThrowableProxy proxy) {
        return new ThrowableProxy(proxy.getThrowable());
    }

    public static ThrowableProxy cloneThrowableProxy(ThrowableProxy proxy, Collection<StackTraceElementProxy> stackTraceElementProxies) {
        final ThrowableProxy throwableProxyClone = cloneThrowableProxy(proxy);
        // sadly, we need to use reflection to set stackTraceElementProxyArray, it is package local, but we are not in the same package
        try {
            Field field = ThrowableProxy.class.getDeclaredField("stackTraceElementProxyArray");
            field.setAccessible(true);
            StackTraceElementProxy[] value = stackTraceElementProxies.toArray(new StackTraceElementProxy[0]);
            field.set(throwableProxyClone, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Unable to set stackTraceElementProxyArray", e);
        }
        return throwableProxyClone;
    }

    public static List<String> splitString(String str, int chunkSize) {
        int len = str.length();
        List<String> result = new ArrayList<>();
        for (int i = 0; i < len; i += chunkSize) {
            result.add(str.substring(i, Math.min(len, i + chunkSize)));
        }
        return result;
    }
}
