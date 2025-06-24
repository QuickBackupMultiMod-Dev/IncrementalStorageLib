package io.github.skydynamic.increment.storage.lib.exception

class IncrementalStorageException private constructor(
    message: String? = null,
    cause: Throwable? = null,
    enableSuppression: Boolean = true,
    writableStackTrace: Boolean = true
) : Exception(message, cause, enableSuppression, writableStackTrace) {

    constructor() : this(null, null, true, true)
    constructor(message: String?) : this(message, null, true, true)
    constructor(message: String?, cause: Throwable?) : this(message, cause, true, true)
    constructor(cause: Throwable?) : this(null, cause, true, true)
}
