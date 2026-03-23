package org.vpilo.babymonitor.common

import kotlin.reflect.KClass

public expect val platformLogger: PlatformLogger

/**
 * Logging abstraction.
 *
 * This singleton allows to log from anywhere, on all supported platforms.
 */
public object Logger {
    private const val UNKNOWN_CLASS_NAME = "<BabyMonitor>"

    /**
     * Log a message at debug level from [caller].
     * This can be used in the context of a class or object: the given [caller]'s class name will be used as the tag.
     * The [message] is a lazily evaluated lambda: it will be invoked only when the log is called.
     * Optionally, a [throwable] can be provided to log an exception along with the [message].
     */
    public inline fun d(
        caller: KClass<out Any>,
        throwable: Throwable? = null,
        message: () -> String,
    ) {
        platformLogger.log(caller.asTag(), LogLevel.DEBUG, message(), throwable)
    }

    /**
     * Log a message at debug level using the given [tag].
     * The [message] is a lazily evaluated lambda: it will be invoked only when the log is called.
     * Optionally, a [throwable] can be provided to log an exception along with the [message].
     */
    public fun d(
        tag: String,
        throwable: Throwable? = null,
        message: () -> String,
    ) {
        platformLogger.log(tag, LogLevel.DEBUG, message(), throwable)
    }

    /**
     * Log a message at info level from [caller].
     * This can be used in the context of a class or object: the given [caller]'s class name will be used as the tag.
     * The [message] is a lazily evaluated lambda: it will be invoked only when the log is called.
     * Optionally, a [throwable] can be provided to log an exception along with the [message].
     */
    public inline fun i(
        caller: KClass<out Any>,
        throwable: Throwable? = null,
        message: () -> String,
    ) {
        platformLogger.log(caller.asTag(), LogLevel.INFO, message(), throwable)
    }

    /**
     * Log a message at info level using the given [tag].
     * The [message] is a lazily evaluated lambda: it will be invoked only when the log is called.
     * Optionally, a [throwable] can be provided to log an exception along with the [message].
     */
    public fun i(
        tag: String,
        throwable: Throwable? = null,
        message: () -> String,
    ) {
        platformLogger.log(tag, LogLevel.INFO, message(), throwable)
    }

    /**
     * Log a message at warning level from [caller].
     * This can be used in the context of a class or object: the given [caller]'s class name will be used as the tag.
     * The [message] is a lazily evaluated lambda: it will be invoked only when the log is called.
     * Optionally, a [throwable] can be provided to log an exception along with the [message].
     */
    public inline fun w(
        caller: KClass<out Any>,
        throwable: Throwable? = null,
        message: () -> String,
    ) {
        platformLogger.log(caller.asTag(), LogLevel.WARN, message(), throwable)
    }

    /**
     * Log a message at warning level using the given [tag].
     * The [message] is a lazily evaluated lambda: it will be invoked only when the log is called.
     * Optionally, a [throwable] can be provided to log an exception along with the [message].
     */
    public fun w(
        tag: String,
        throwable: Throwable? = null,
        message: () -> String,
    ) {
        platformLogger.log(tag, LogLevel.WARN, message(), throwable)
    }

    /**
     * Log a message at error level from [caller].
     * This can be used in the context of a class or object: the given [caller]'s class name will be used as the tag.
     * The [message] is a lazily evaluated lambda: it will be invoked only when the log is called.
     * Optionally, a [throwable] can be provided to log an exception along with the [message].
     */
    public inline fun e(
        caller: KClass<out Any>,
        throwable: Throwable? = null,
        message: () -> String,
    ) {
        platformLogger.log(caller.asTag(), LogLevel.ERROR, message(), throwable)
    }

    /**
     * Log a message at error level using the given [tag].
     * The [message] is a lazily evaluated lambda: it will be invoked only when the log is called.
     * Optionally, a [throwable] can be provided to log an exception along with the [message].
     */
    public fun e(
        tag: String,
        throwable: Throwable? = null,
        message: () -> String,
    ) {
        platformLogger.log(tag, LogLevel.ERROR, message(), throwable)
    }

    /**
     * Converts this [KClass] to a tag string.
     */
    public fun KClass<out Any>.asTag(): String = simpleName ?: UNKNOWN_CLASS_NAME
}
