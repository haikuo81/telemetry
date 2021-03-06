package com.yammer.telemetry.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.yammer.telemetry.agent.handlers.ApacheHttpClientClassHandler;
import com.yammer.telemetry.agent.handlers.EnvironmentExecutorClassHandler;
import com.yammer.telemetry.agent.handlers.HttpServletClassHandler;
import com.yammer.telemetry.agent.handlers.MetricsRegistryHandler;
import com.yammer.telemetry.agent.jdbc.JdbcDriverClassHandler;
import com.yammer.telemetry.instrumentation.TelemetryTransformer;
import com.yammer.telemetry.tracing.Annotations;
import com.yammer.telemetry.tracing.SpanHelper;
import com.yammer.telemetry.tracing.SpanSinkRegistry;
import com.yammer.telemetry.tracing.logging.LoggingSpanSink;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.instrument.Instrumentation;

public class TelemetryAgent {
    public static void agentmain(String agentArgs, Instrumentation inst) {
        premain(agentArgs, inst);
    }

    public static void premain(String agentArgs, Instrumentation inst) {
        if (!agentArgs.isEmpty()) {
            try {
                TelemetryConfiguration config = loadConfiguration(agentArgs);

                SpanHelper.setSampler(config.getSampler());
                Annotations.setServiceAnnotations(config.getAnnotations());

                if (config.isEnabled()) {
                    LogConfiguration log = config.getSinks().getLog();
                    if (log.isEnabled()) {
                        SpanSinkRegistry.register(new LoggingSpanSink(log.getFile()));
                    }

                    final TelemetryTransformer transformer = new TelemetryTransformer();
                    if (config.getInstruments().contains("inbound-http")) {
                        transformer.addHandler(new HttpServletClassHandler());
                    }
                    if (config.getInstruments().contains("outbound-http")) {
                        transformer.addHandler(new ApacheHttpClientClassHandler());
                    }
                    if (config.getInstruments().contains("database")) {
                        transformer.addHandler(new JdbcDriverClassHandler());
                    }
                    if (config.getInstruments().contains("metrics")) {
                        transformer.addHandler(new MetricsRegistryHandler());
                    }
                    if (config.getInstruments().contains("executors")) {
                        transformer.addHandler(new EnvironmentExecutorClassHandler());
                    }

                    inst.addTransformer(transformer);
                }
            } catch (IOException e) {
                System.err.println("Failed to load telemetry agent configuration: " + e.toString());
                System.err.println("Application will continue uninstrumented.");
                e.printStackTrace(System.err);
            }
        } else {
            System.err.println("No agent configuration path was specified. Application will not be traced by telemetry.");
            System.err.println("To trace an application, specify the path to telemetry.yml as an argument to the agent.");
        }
    }

    private static TelemetryConfiguration loadConfiguration(String filePath) throws IOException {
        File configPath = new File(filePath);
        if (!configPath.isFile()) {
            throw new FileNotFoundException("Config file " + filePath + " does not exist");
        }

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        return mapper.readValue(configPath, TelemetryConfiguration.class);
    }
}
