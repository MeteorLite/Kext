
package kext.internal

import java.util.stream.Collectors
import java.util.concurrent.ConcurrentHashMap
import java.lang.ReflectiveOperationException
import kext.*
import org.slf4j.LoggerFactory
import java.util.*
import java.util.function.Function
import java.util.stream.Stream

class InternalExtensionLoader : ExtensionLoader {
    override fun <T> load(
        type: Class<T>,
        classLoaders: MutableList<ClassLoader>?,
        sessionID: String?
    ): MutableList<T> {
        return classLoaders?.stream()
            ?.flatMap { classLoader: ClassLoader? -> load(type, classLoader) }
            ?.filter { prototype: T? -> filterPrototypesWithoutMetadata(prototype) }
            ?.filter { prototype: T? -> filterExternallyManaged(prototype!!) }
            ?.map { prototype: T? -> instantiate<T?>(prototype, sessionID) }
            ?.filter { obj: Optional<T?>? -> obj!!.isPresent }
            ?.map { obj: Optional<T?>? -> obj!!.get() }
            ?.collect(Collectors.toList())!!
    }

    override fun invalidateSession(sessionID: String) {
        instancesPerSession.remove(sessionID)
    }

    private fun filterPrototypesWithoutMetadata(prototype: Any?): Boolean {
        val prototypeClass: Class<*> = prototype!!.javaClass
        if (withoutMetadata.contains(prototypeClass)) {
            return false
        }
        val metadata = prototypeClass.getAnnotation(Extension::class.java)
        if (metadata == null) {
            LOGGER.debug(
                "Class {} is not annotated with {} so it will be ignored",
                prototypeClass.canonicalName,
                Extension::class.java.canonicalName
            )
            withoutMetadata.add(prototypeClass)
            return false
        }
        return true
    }

    private fun filterExternallyManaged(prototype: Any): Boolean {
        val prototypeClass: Class<*> = prototype.javaClass
        if (externallyManaged.contains(prototypeClass)) {
            return false
        }
        val metadata = prototypeClass.getAnnotation(Extension::class.java)
        if (metadata.externallyManaged) {
            LOGGER.debug(
                "Class {} is externally managed and ignored by the internal extension loader",
                prototypeClass.canonicalName
            )
            externallyManaged.add(prototypeClass)
            return false
        }
        return true
    }

    private fun <T> instantiate(prototype: T?, sessionID: String?): Optional<T> {
        val prototypeClass: Class<*> = prototype!!::class.java.javaClass
        val metadata = prototypeClass.getAnnotation(Extension::class.java)
        val instance: T? = when (metadata.scope) {
            ExtensionScope.LOCAL -> newInstance(prototypeClass)
            ExtensionScope.SESSION -> instancesPerSession
                .computeIfAbsent(
                    sessionID!!
                ) { ConcurrentHashMap() }
                ?.computeIfAbsent(prototypeClass) { newInstance(prototypeClass) } as T?
            ExtensionScope.GLOBAL -> globalInstances
                .computeIfAbsent(prototypeClass) { newInstance(prototypeClass) } as T?
        }
        return Optional.ofNullable(instance)
    }

    private fun <T> newInstance(type: Class<*>?): T? {
        return try {
            type!!.getConstructor().newInstance() as T
        } catch (e: ReflectiveOperationException) {
            LOGGER.error(
                "Class {} cannot be instantiated, a public constructor with " +
                        "zero arguments is required [error was: {}]",
                type!!.canonicalName,
                e.toString()
            )
            null
        }
    }

    private fun <T> load(type: Class<T>, classLoader: ClassLoader?): Stream<T> {
        return try {
            // dynamically declaration of 'use' directive, otherwise it will cause an error
            InternalExtensionLoader::class.java.module.addUses(type)
            ServiceLoader.load(type, classLoader).stream()
                .map { it.get() }
        } catch (e: ServiceConfigurationError) {
            LOGGER.error("Error loading extension of type {}", type, e)
            Stream.empty()
        }
    }

    override fun toString(): String {
        return "Built-in extension loader"
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(InternalExtensionLoader::class.java)
        private val globalInstances: MutableMap<Class<*>?, Any?> = ConcurrentHashMap()
        private val instancesPerSession: MutableMap<String, MutableMap<Class<*>?, Any?>?> = ConcurrentHashMap()
        private val withoutMetadata: MutableSet<Class<*>?> = ConcurrentHashMap.newKeySet()
        private val externallyManaged: MutableSet<Class<*>?> = ConcurrentHashMap.newKeySet()
    }
}