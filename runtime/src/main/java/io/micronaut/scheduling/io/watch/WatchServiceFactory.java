/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.scheduling.io.watch;

import io.methvin.watchservice.MacOSXListeningWatchService;
import io.micronaut.context.annotation.*;
import io.micronaut.core.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.WatchService;

/**
 * A factory that creates the default watch service.
 *
 * @author graemerocher
 * @since 1.1.0
 */
@Requires(property = FileWatchConfiguration.ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
@Requires(property = FileWatchConfiguration.PATHS)
@Requires(missing = MacOSXListeningWatchService.class)
@Factory
public class WatchServiceFactory {
    protected static final Logger LOG = LoggerFactory.getLogger(WatchServiceFactory.class);

    /**
     * The default {@link WatchService}.
     *
     * @return The watch service to use.
     * @throws IOException if an error occurs creating the watch service
     */
    @Bean(preDestroy = "close")
    @Prototype
    @Requires(missing = MacOSXListeningWatchService.class)
    @Requires(property = FileWatchConfiguration.ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
    @Requires(property = FileWatchConfiguration.PATHS)
    @Primary
    protected WatchService watchService() throws IOException {
        String name = System.getProperty("os.name").toLowerCase();
        boolean isMacOS = "Mac OS X".equalsIgnoreCase(name) || "Darwin".equalsIgnoreCase(name);
        if (isMacOS) {
            LOG.warn("Using default File WatchService on OS X is slow. Consider adding 'io.methvin:directory-watcher' and 'net.java.dev.jna:jna' dependencies to use native file watch");
        }
        return FileSystems.getDefault().newWatchService();
    }

}
