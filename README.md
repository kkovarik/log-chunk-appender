# log-chunk-appender
Logback Chunk Appender, to split long messages into multiple smaller ones. Useful when working with log aggregators that have message size limits.
For example, Azure Log Analytics Workspace has a default limit of 16k, which is not so hard to cross with long stack traces or large JSON objects.

Appender will split messages and stack traces that are longer than specified length.
The length is configurable via maxLength property.
It will append sequenceKey to MDC with sequence number of the split message.
Then it fill forward the log event to another appender.

Basic logback.xml configuration:
```xml
<appender name="CHUNK" class="cz.kkovarik.logback.ChunkAppender">
    <maxLength>10000</maxLength>
    <sequenceKey>seq</sequenceKey>
    <appender-ref ref="STDOUT" />
</appender>
```

Use with JSON encoder:
```xml
<configuration>

    <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <includeMdcKeyName>trace_id</includeMdcKeyName>
            <includeMdcKeyName>span_id</includeMdcKeyName>
            <includeMdcKeyName>trace_flags</includeMdcKeyName>
            <includeMdcKeyName>seq</includeMdcKeyName>
        </encoder>
    </appender>

    <appender name="CHUNK" class="cz.kkovarik.logback.ChunkAppender">
        <appender-ref ref="CONSOLE"/>
        <maxLength>10000</maxLength>
        <sequenceKey>seq</sequenceKey>
    </appender>
    <root level="trace">
        <appender-ref ref="CHUNK" />
    </root>
</configuration>
```

Can be used with logback ShortenedThrowableConverter as well:
```xml  
        <encoder>
            <throwableConverter class="net.logstash.logback.stacktrace.ShortenedThrowableConverter">
                <omitCommonFrames>true</omitCommonFrames>
            </throwableConverter>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg [%X{seq}]%n</pattern>
        </encoder>
```
