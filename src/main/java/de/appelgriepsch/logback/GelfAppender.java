package de.appelgriepsch.logback;

import static de.appelgriepsch.logback.MessageLevelMapping.toGelfNumericValue;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import org.graylog2.gelfclient.GelfConfiguration;
import org.graylog2.gelfclient.GelfMessageBuilder;
import org.graylog2.gelfclient.GelfMessageLevel;
import org.graylog2.gelfclient.GelfTransports;
import org.graylog2.gelfclient.transport.GelfTransport;
import org.slf4j.Marker;

import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.AppenderBase;


/**
 * @author  Sandra Thieme - thieme@synyx.de
 */
public class GelfAppender extends AppenderBase<ILoggingEvent> {

    private String server = "localhost";
    private int port = 12201;
    private String hostName;
    private String protocol = "UDP";
    private boolean includeSource = true;
    private boolean includeMDC = true;
    private boolean includeStackTrace = true;
    private boolean includeLevelName = false;
    private int queueSize = 512;
    private int connectTimeout = 1000;
    private int reconnectDelay = 500;
    private int sendBufferSize = -1;
    private boolean tcpNoDelay = false;
    private boolean tcpKeepAlive = false;
    private Map<String, Object> additionalFields = new HashMap<>();
    ThrowableProxyConverter converter = new ThrowableProxyConverter ();

    private GelfTransport client;

    public GelfAppender() {

        super();
    }

    @Override
    protected void append(ILoggingEvent event) {

        if (event == null) {
            return;
        }

        final GelfMessageBuilder builder = new GelfMessageBuilder(event.getFormattedMessage(), hostName()).timestamp(
                    event.getTimeStamp() / 1000d)
            .level(GelfMessageLevel.fromNumericLevel(toGelfNumericValue(event.getLevel())))
            .additionalField("loggerName", event.getLoggerName())
            .additionalField("threadName", event.getThreadName());

        final Marker marker = event.getMarker();

        if (marker != null) {
            builder.additionalField("marker", marker.getName());
        }

        if (includeMDC) {
            for (Map.Entry<String, String> entry : event.getMDCPropertyMap().entrySet()) {
                builder.additionalField(entry.getKey(), entry.getValue());
            }
        }

        StackTraceElement[] callerData = event.getCallerData();

        if (includeSource && event.hasCallerData()) {
            StackTraceElement source = callerData[0];

            builder.additionalField("sourceFileName", source.getFileName());
            builder.additionalField("sourceMethodName", source.getMethodName());
            builder.additionalField("sourceClassName", source.getClassName());
            builder.additionalField("sourceLineNumber", source.getLineNumber());
        }

        IThrowableProxy thrown = event.getThrowableProxy();

        if (includeStackTrace && thrown != null) {

            builder.additionalField("exceptionClass", thrown.getClassName());
            builder.additionalField("exceptionMessage", thrown.getMessage());
            builder.additionalField("exceptionStackTrace", converter.convert (event));

            builder.fullMessage(event.getFormattedMessage() + "\n\n" + converter.convert (event));
        }

        if (includeLevelName) {
            builder.additionalField("levelName", event.getLevel().levelStr);
        }

        if (!additionalFields.isEmpty()) {
            builder.additionalFields(additionalFields);
        }

        try {
            client.send(builder.build());
        } catch (Exception e) {
            addError("Failed to write log event to the GELF server: " + e.getMessage(), e);
        }
    }


    @Override
    public void start() {

        super.start();
        createGelfClient();
        converter.start ();
    }


    @Override
    public void stop() {

        super.stop();
        client.stop();
        converter.stop ();
    }


    private String hostName() {

        if (hostName == null || hostName.trim().isEmpty()) {
            try {
                return InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException e) {
                return "localhost";
            }
        }

        return hostName;
    }


    private void createGelfClient() {

        client = GelfTransports.create(getGelfConfiguration());
    }


    public GelfConfiguration getGelfConfiguration() {

        final InetSocketAddress serverAddress = new InetSocketAddress(server, port);
        final GelfTransports gelfProtocol = GelfTransports.valueOf(protocol().toUpperCase());

        return new GelfConfiguration(serverAddress).transport(gelfProtocol)
            .queueSize(queueSize)
            .connectTimeout(connectTimeout)
            .reconnectDelay(reconnectDelay)
            .sendBufferSize(sendBufferSize)
            .tcpNoDelay(tcpNoDelay)
            .tcpKeepAlive(tcpKeepAlive);
    }


    private String protocol() {

        if (!"UDP".equalsIgnoreCase(protocol) && !"TCP".equalsIgnoreCase(protocol)) {
            return "UDP";
        }

        return protocol;
    }


    public void setServer(String server) {

        this.server = server;
    }


    public void setPort(int port) {

        this.port = port;
    }


    public void setHostName(String hostName) {

        this.hostName = hostName;
    }


    public void setProtocol(String protocol) {

        this.protocol = protocol;
    }


    public void setIncludeSource(boolean includeSource) {

        this.includeSource = includeSource;
    }


    public void setIncludeMDC(boolean includeMDC) {

        this.includeMDC = includeMDC;
    }


    public void setIncludeStackTrace(boolean includeStackTrace) {

        this.includeStackTrace = includeStackTrace;
    }


    public void setIncludeLevelName(boolean includeLevelName) {

        this.includeLevelName = includeLevelName;
    }


    public void setQueueSize(int queueSize) {

        this.queueSize = queueSize;
    }


    public void setConnectTimeout(int connectTimeout) {

        this.connectTimeout = connectTimeout;
    }


    public void setReconnectDelay(int reconnectDelay) {

        this.reconnectDelay = reconnectDelay;
    }


    public void setSendBufferSize(int sendBufferSize) {

        this.sendBufferSize = sendBufferSize;
    }


    public void setTcpNoDelay(boolean tcpNoDelay) {

        this.tcpNoDelay = tcpNoDelay;
    }


    public void setTcpKeepAlive(boolean tcpKeepAlive) {

        this.tcpKeepAlive = tcpKeepAlive;
    }


    public void setAdditionalFields(String additionalFields) {

        try {
            String[] values = additionalFields.split(",");

            for (String field : values) {
                String[] components = field.split("=");
                this.additionalFields.put(components[0], components[1]);
            }
        } catch (Exception e) {
            addWarn("Failed to read additional fields: " + e.getMessage(), e);
        }
    }
}
