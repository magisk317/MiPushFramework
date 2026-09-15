package com.xiaomi.mipush.sdk

/*
 * No stock 7.4.67-C same-path source was found in the split source tree.
 */
class DecryptException : Exception {
    constructor() : super()
    constructor(message: String?) : super(message)
    constructor(message: String?, cause: Throwable?) : super(message, cause)
    constructor(cause: Throwable?) : super(cause)
}
