package dev.phillipslabs.tuskt.client

public class TusktException(
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause) {
    public constructor(message: String?) : this(message, null)
    public constructor(cause: Throwable?) : this(null, cause)
}
