package org.vpilo.babymonitor.model

sealed interface DataSourceError: Error {

    enum class Remote : DataSourceError {
        NoInternetConnection,
        TimeOut,
        TooManyRequests,
        Server,
        Serialization,
        Unknown,
    }

    enum class Local : DataSourceError {
        DiskFull,
        Unknown,
    }
}
