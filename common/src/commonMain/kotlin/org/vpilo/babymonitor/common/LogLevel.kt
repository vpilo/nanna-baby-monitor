package org.vpilo.babymonitor.common

/**
 * Represent the different levels of logging.
 * Enum values are ordered by decreasing verbosity.
 */
public enum class LogLevel {
    /**
     * Debug logging level. Used for debugging and development purposes.
     */
    DEBUG,

    /**
     * Informative logging level. Used for bug investigation purposes.
     */
    INFO,

    /**
     * Warning logging level. Indicates potential issues that may need attention.
     */
    WARN,

    /**
     * Error logging level. Indicates errors in the application.
     */
    ERROR,
}
