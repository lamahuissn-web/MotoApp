package com.lamahuissn.motoapp.data

import java.util.UUID

/**
 * The key that makes a retry a retry.
 *
 * The server charges once per key. The phone's job is to keep ONE key for ONE confirmed attempt and
 * hang on to it until the answer is known: a request that times out in a dead spot is not a new
 * attempt, and generating a fresh key for it is exactly how a household pays twice for one month.
 *
 * The shape is the server's own rule (`[A-Za-z0-9._:-]{16,128}`), and it is deliberately opaque —
 * nothing about the amount or the household is encoded in it.
 */
object AttemptKey {

    fun next(): String = "moto-" + UUID.randomUUID().toString().replace("-", "")

    /** The server's rule, kept here so a malformed key can never leave the phone. */
    fun isValid(key: String): Boolean = key.length in 16..128 && key.all { it.isLetterOrDigit() || it in "._:-" }
}
