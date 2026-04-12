package dev.phillipslabs.tuskt

public object TusHeaders {
    /**
     * The Upload-Offset request and response header indicates a byte offset within a resource.
     * The value MUST be a non-negative integer.
     */
    public const val UPLOAD_OFFSET: String = "Upload-Offset"

    /**
     * The Upload-Length request and response header indicates the size of the entire upload in bytes.
     * The value MUST be a non-negative integer.
     */
    public const val UPLOAD_LENGTH: String = "Upload-Length"

    /**
     * The Tus-Version response header MUST be a comma-separated list of protocol versions supported by the Server.
     * The list MUST be sorted by Server’s preference where the first one is the most preferred one.
     */
    public const val TUS_VERSION: String = "Tus-Version"

    /**
     * The Tus-Resumable header MUST be included in every request and response except for OPTIONS requests.
     * The value MUST be the version of the protocol used by the Client or the Server.
     */
    public const val TUS_RESUMABLE: String = "Tus-Resumable"

    /**
     * The Tus-Extension response header MUST be a comma-separated list of the extensions supported by the Server.
     * If no extensions are supported, the Tus-Extension header MUST be omitted.
     */
    public const val TUS_EXTENSION: String = "Tus-Extension"

    /**
     * The Tus-Max-Size response header MUST be a non-negative integer indicating the maximum allowed size of an entire
     * upload in bytes.
     * The Server SHOULD set this header if there is a known hard limit.
     */
    public const val TUS_MAX_SIZE: String = "Tus-Max-Size"

    // NOTE this header is handled by a ktor server plugin!
//    /**
//     * The X-HTTP-Method-Override request header MUST be a string which MUST be interpreted as the request’s method by
//     * the Server, if the header is presented.
//     * The actual method of the request MUST be ignored.
//     * The Client SHOULD use this header if its environment does not support the PATCH or DELETE methods.
//     */
//    const val X_HTTP_METHOD_OVERRIDE = "X-HTTP-Method-Override"

    /**
     * The Upload-Defer-Length request and response header indicates that the size of the upload is not known currently
     * and will be transferred later.
     * Its value MUST be 1. If the length of an upload is not deferred, this header MUST be omitted.
     */
    public const val UPLOAD_DEFER_LENGTH: String = "Upload-Defer-Length"

    /**
     * The Upload-Metadata request and response header MUST consist of one or more comma-separated key-value pairs.
     * The key and value MUST be separated by a space. The key MUST NOT contain spaces and commas and MUST NOT be empty.
     * The key SHOULD be ASCII encoded and the value MUST be Base64 encoded. All keys MUST be unique.
     * The value MAY be empty. In these cases, the space, which would normally separate the key and the value,
     * MAY be left out.
     */
    public const val UPLOAD_METADATA: String = "Upload-Metadata"

    /**
     * The Upload-Expires response header indicates the time after which the unfinished upload expires.
     * The Client SHOULD use this header to determine if an upload is still valid before attempting to resume the
     * upload.
     */
    public const val UPLOAD_EXPIRES: String = "Upload-Expires"

    /**
     * The Upload-Checksum request header contains information about the checksum of the current body payload.
     * The header MUST consist of the name of the used checksum algorithm and the Base64 encoded checksum separated by
     * a space.
     */
    public const val UPLOAD_CHECKSUM: String = "Upload-Checksum"

    /**
     * The Tus-Checksum-Algorithm response header MUST be a comma-separated list of the checksum algorithms supported
     * by the server.
     */
    public const val TUS_CHECKSUM_ALGORITHM: String = "Tus-Checksum-Algorithm"

    /**
     * The Upload-Concat request and response header MUST be set in both partial and final upload creation requests.
     * It indicates whether the upload is either a partial or final upload.
     * If the upload is a partial one, the header value MUST be partial.
     * In the case of a final upload, its value MUST be final followed by a semicolon and a space-separated list of
     * partial upload URLs that will be concatenated.
     */
    public const val UPLOAD_CONCAT: String = "Upload-Concat"
}
