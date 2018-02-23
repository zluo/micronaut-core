/*
 * Copyright 2017 original authors
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
package org.particleframework.function.executor;

import org.particleframework.context.ApplicationContext;
import org.particleframework.core.annotation.Internal;
import org.particleframework.core.cli.CommandLine;
import org.particleframework.core.reflect.ClassUtils;
import org.particleframework.function.LocalFunctionRegistry;
import org.particleframework.http.MediaType;
import org.particleframework.http.codec.MediaTypeCodecRegistry;

import java.io.Closeable;
import java.io.IOException;
import java.util.function.Function;

/**
 * A super class that can be used to initialize a function
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class FunctionInitializer extends AbstractExecutor implements Closeable, AutoCloseable {

    protected final ApplicationContext applicationContext;
    protected final boolean closeContext;
    private FunctionExitHandler functionExitHandler = new DefaultFunctionExitHandler();

    @SuppressWarnings("unchecked")
    public FunctionInitializer() {
        ApplicationContext applicationContext = buildApplicationContext(null);
        this.applicationContext = applicationContext;
        startThis(applicationContext);
        injectThis(applicationContext);
        this.closeContext = true;
    }

    /**
     * Start a function for an existing {@link ApplicationContext}
     * @param applicationContext The application context
     */
    protected FunctionInitializer(ApplicationContext applicationContext) {
        this(applicationContext, true);
    }

    /**
     * Start a function for an existing {@link ApplicationContext}
     * @param applicationContext The application context
     */
    protected FunctionInitializer(ApplicationContext applicationContext, boolean inject) {
        this.applicationContext = applicationContext;
        this.closeContext = false;
        if(inject) {
            injectThis(applicationContext);
        }
    }


    @Override
    @Internal
    public void close() throws IOException {
        if (closeContext && applicationContext != null) {
            applicationContext.close();
        }
    }


    /**
     * This method is designed to be called when using the {@link FunctionInitializer} from a static Application main method
     *
     * @param args     The arguments passed to main
     * @param supplier The function that executes this function
     * @throws IOException If an error occurs
     */
    protected void run(String[] args, Function<ParseContext, ?> supplier) throws IOException {
        ApplicationContext applicationContext = this.applicationContext;
        this.functionExitHandler = applicationContext.findBean(FunctionExitHandler.class).orElse(this.functionExitHandler);
        ParseContext context = new ParseContext(args);
        try {
            Object result = supplier.apply(context);
            if (result != null) {

                LocalFunctionRegistry bean = applicationContext.getBean(LocalFunctionRegistry.class);
                StreamFunctionExecutor.encode(applicationContext.getEnvironment(), bean, result.getClass(), result, System.out);
                functionExitHandler.exitWithSuccess();
            }
        } catch (Exception e) {
            functionExitHandler.exitWithError(e, context.debug);
        }
    }

    /**
     * Start this environment
     *
     * @param applicationContext
     */
    protected void startThis(ApplicationContext applicationContext) {
        startEnvironment(applicationContext);
    }

    /**
     * Injects this instance
     * @param applicationContext The {@link ApplicationContext}
     * @return This injected instance
     */
    protected void injectThis(ApplicationContext applicationContext) {
        if(applicationContext != null) {
            applicationContext.inject(this);
        }
    }


    /**
     * The parse context supplied from the {@link #run(String[], Function)} method. Consumers can use the {@link #get(Class)} method to obtain the data is the desired type
     */
    public class ParseContext {
        private final String data;
        private final boolean debug;

        public ParseContext(String[] args) {
            CommandLine commandLine = FunctionApplication.parseCommandLine(args);
            debug = commandLine.hasOption(FunctionApplication.DEBUG_OPTIONS);
            data = commandLine.hasOption(FunctionApplication.DATA_OPTION) ? commandLine.optionValue(FunctionApplication.DATA_OPTION).toString() : null;
        }

        public <T> T get(Class<T> type) {
            if (data == null) {
                functionExitHandler.exitWithNoData();
                return null;
            } else {
                if (ClassUtils.isJavaLangType(type)) {
                    return applicationContext
                            .getConversionService()
                            .convert(data, type).orElseThrow(() -> newIllegalArgument(type, data));
                } else {
                    MediaTypeCodecRegistry codecRegistry = applicationContext.getBean(MediaTypeCodecRegistry.class);
                    return codecRegistry.findCodec(MediaType.APPLICATION_JSON_TYPE)
                            .map(codec -> codec.decode(type, data))
                            .orElseThrow(() -> newIllegalArgument(type, data));
                }
            }
        }

        private <T> IllegalArgumentException newIllegalArgument(Class<T> dataType, String data) {
            return new IllegalArgumentException("Passed data [" + data + "] cannot be converted to type: " + dataType);
        }

    }
}
