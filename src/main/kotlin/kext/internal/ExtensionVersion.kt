
package kext.internal

import java.util.NoSuchElementException
import java.lang.IllegalArgumentException
import java.lang.NumberFormatException
import java.util.stream.Stream

class ExtensionVersion private constructor(version: String?) {
    private var major = 0
    private var minor = 0
    private var patch: String? = null

    init {
        val parts = Stream.of(*version!!.split("\\.".toRegex()).toTypedArray()).iterator()
        try {
            major = parts.next().toInt()
            minor = if (parts.hasNext()) parts.next().toInt() else 0
            patch = if (parts.hasNext()) parts.next() else ""
        } catch (e: NoSuchElementException) {
            throw IllegalArgumentException(
                "Not valid version number " + version + " (" + e.message + ")"
            )
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException(
                "Not valid version number " + version + " (" + e.message + ")"
            )
        }
    }

    fun major(): Int {
        return major
    }

    fun minor(): Int {
        return minor
    }

    fun patch(): String? {
        return patch
    }

    fun isCompatibleWith(otherVersion: ExtensionVersion?): Boolean {
        return major == otherVersion!!.major && minor >= otherVersion.minor
    }

    override fun toString(): String {
        return "$major.$minor"
    }

    companion object {
        fun of(version: String?): ExtensionVersion? {
            return ExtensionVersion(version)
        }
    }
}