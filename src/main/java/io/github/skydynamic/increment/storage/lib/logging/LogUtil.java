package io.github.skydynamic.increment.storage.lib.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("unused")
public class LogUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger("IncrementalStorageLib");

    public static Logger getLogger() {
        return LOGGER;
    }
}
