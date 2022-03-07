
package kext.internal

import java.lang.IllegalArgumentException
import java.lang.StringBuilder
import kext.*
import java.util.function.Predicate

class ExtensionLoadContext<T> private constructor(
    private val sessionID: String?,
    private val extensionPoint: Class<T?>?,
    private val extensionPointData: ExtensionPoint?,
    private val condition: Predicate<T?>?
) {
    private var classLoaders: MutableList<ClassLoader?>? = null
    private var extensionLoader: ExtensionLoader? = null
    private var externallyManaged = false
    fun withInternalLoader(
        classLoaders: MutableList<ClassLoader?>?,
        extensionLoader: ExtensionLoader?
    ): ExtensionLoadContext<T> {
        val context = ExtensionLoadContext<T>(
            sessionID,
            extensionPoint!!,
            extensionPointData,
            condition
        )
        context.classLoaders = classLoaders
        context.extensionLoader = extensionLoader
        context.externallyManaged = false
        return context
    }

    fun withExternalLoader(
        classLoaders: MutableList<ClassLoader?>,
        extensionLoader: ExtensionLoader?
    ): ExtensionLoadContext<T> {
        val context = ExtensionLoadContext<T>(
            sessionID,
            extensionPoint,
            extensionPointData,
            condition
        )
        context.classLoaders = classLoaders
        context.extensionLoader = extensionLoader
        context.externallyManaged = true
        return context
    }

    fun load(): MutableList<T?>? {
        return extensionLoader?.load(extensionPoint, classLoaders, sessionID)
    }

    fun condition(): Predicate<T?>? {
        return condition
    }

    fun extensionPointData(): ExtensionPoint? {
        return extensionPointData
    }

    fun extensionPoint(): Class<T?>? {
        return extensionPoint
    }

    fun isExternallyManaged(): Boolean {
        return externallyManaged
    }

    override fun toString(): String {
        val string = StringBuilder("[Extensions of type ").append(extensionPoint)
        if (externallyManaged) {
            string.append(" (externally managed) ")
        }
        if (extensionLoader != null) {
            string.append(" loaded by ").append(extensionLoader)
        }
        if (classLoaders != null) {
            string.append(" using class loaders ").append(classLoaders)
        }
        return string.append("]").toString()
    }

    companion object {
        fun <T> all(sessionID: String?, extensionPoint: Class<T?>?): ExtensionLoadContext<T> {
            return ExtensionLoadContext(
                sessionID,
                extensionPoint,
                dataOf(extensionPoint),
                selectAll<T?>()
            )
        }

        fun <T> satisfying(
            sessionID: String?,
            extensionPoint: Class<T?>?,
            condition: Predicate<T?>?
        ): ExtensionLoadContext<T?> {
            return ExtensionLoadContext(
                sessionID,
                extensionPoint,
                dataOf(extensionPoint),
                condition
            )
        }

        fun <T> satisfyingData(
            sessionID: String?,
            extensionPoint: Class<T?>?,
            condition: Predicate<Extension?>?
        ): ExtensionLoadContext<T?> {
            return ExtensionLoadContext(
                sessionID,
                extensionPoint,
                dataOf(extensionPoint),
                conditionFromAnnotation<T?>(condition)
            )
        }

        private fun <T> selectAll(): Predicate<T?> {
            return Predicate { true }
        }

        private fun <T> conditionFromAnnotation(condition: Predicate<Extension?>?): Predicate<T?> {
            return Predicate { extension: T? ->
                condition!!.test(
                    extension!!::class.java.javaClass.getAnnotation<Extension?>(
                        Extension::class.java
                    )
                )
            }
        }

        private fun <T> dataOf(extensionPoint: Class<T>?): ExtensionPoint {
            return extensionPoint!!.getAnnotation(ExtensionPoint::class.java)
                ?: throw IllegalArgumentException("$extensionPoint must be annotated with @ExtensionPoint")
        }
    }
}