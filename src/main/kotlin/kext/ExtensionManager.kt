
package kext

import kext.internal.ExtensionLoadContext
import java.util.stream.Collectors
import kext.internal.ExtensionVersion
import java.lang.IllegalArgumentException
import kext.internal.InternalExtensionLoader
import org.slf4j.LoggerFactory
import java.util.*
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Predicate
import java.util.stream.Stream

/**
 * Object that provides operations in order to retrieve instances of
 * classes annotated with [Extension].
 *
 *
 * The intended purpose of this class is to be used as a singleton,
 * but there is no actual constraint about that. Clients can create
 * as many instances as they required, but being responsible of releasing
 * references when they are no longer required (see [.clear]).
 */
class ExtensionManager {
    private val sessionID = UUID.randomUUID().toString()
    private val classLoaders: MutableList<ClassLoader?>
    private val extensionLoaders = extensionLoaders()
    private val invalidExtensions: MutableMap<Class<*>?, MutableSet<Class<*>>> = HashMap()
    private val validExtensions: MutableMap<Class<*>?, MutableSet<Class<*>>> = HashMap()
    private val extensionMetadata: MutableMap<Any, Extension> = HashMap()

    /**
     * Creates a new extension manager using the default class loader of the
     * current thread
     */
    constructor() : this(Thread.currentThread().contextClassLoader) {}

    /**
     * Creates a new extension manager restricted to a specific set of class
     * loaders
     *
     * @param loaders The class loaders used for loading extension classes
     */
    constructor(vararg loaders: ClassLoader?) {
        classLoaders = mutableListOf(*loaders)
    }

    /**
     * Creates a new extension manager restricted to a specific set of class
     * loaders
     *
     * @param loaders The class loaders used for loading extension classes
     */
    constructor(loaders: Collection<ClassLoader?>?) {
        classLoaders = ArrayList(loaders)
    }

    /**
     * Get the extension annotated metadata for a given extension
     *
     * @param extension A extension instance
     * @return The extension metadata, or `null` if passed object is
     * not an extension
     */
    fun <T> getExtensionMetadata(extension: T): Extension {
        return extensionMetadata.computeIfAbsent(
            extension!!
        ) { e: Any ->
            e.javaClass.getAnnotation(
                Extension::class.java
            )
        }
    }

    /**
     * Get all the extension annotated metadata for a given extension point
     *
     * @param extensionPoint A extension point
     * @return The extension metadata, or `null` if passed object is
     * not an extension
     */
    fun <T> getExtensionMetadata(extensionPoint: Class<T?>?): Stream<Extension>? {
        return getExtensions(extensionPoint)?.map { extension: T? -> this.getExtensionMetadata(extension) }
    }

    /**
     * Retrieves an instance for the given extension point, if any exists. In the
     * case of existing multiple alternatives, the one with highest priority will
     * be used.
     *
     * @param extensionPoint The extension point type
     * @return An optional object either empty or wrapping the instance
     */
    fun <T> getExtension(extensionPoint: Class<T?>?): Optional<T?> {
        return loadFirst(ExtensionLoadContext.all(sessionID, extensionPoint))
    }

    /**
     * Retrieves the instance for the given extension point that satisfies the
     * specified condition, if any exists. In the case of existing multiple
     * alternatives, the one with highest priority will be used.
     *
     * @param extensionPoint The extension point type
     * @param condition Only extensions satisfying this condition will be
     * returned
     * @return An optional object either empty or wrapping the instance
     */
    fun <T> getExtensionThatSatisfy(
        extensionPoint: Class<T?>?,
        condition: Predicate<T?>?
    ): Optional<T?> {
        return loadFirst(ExtensionLoadContext.satisfying(sessionID, extensionPoint, condition))
    }

    /**
     * Retrieves the instance for the given extension point that satisfies the
     * specified condition, if any exists. In the case of existing multiple
     * alternatives, the one with highest priority will be used.
     *
     * @param extensionPoint The extension point type
     * @param condition Only extensions which their metadata satisfies this
     * condition will be returned
     * @return An optional object either empty or wrapping the instance
     */
    fun <T> getExtensionThatSatisfyMetadata(
        extensionPoint: Class<T?>?,
        condition: Predicate<Extension?>?
    ): Optional<T?> {
        return loadFirst(ExtensionLoadContext.satisfyingData(sessionID, extensionPoint, condition))
    }

    /**
     * Retrieves the instance for the given extension point that satisfies the
     * given provider, name and version, if any exists. The retrieved extension may
     * be a higher but compatible version if exact version is not found.
     *
     *
     * In the case of existing multiple
     * alternatives, the one with highest priority will be used.
     *
     * @param extensionPoint The extension point type
     * @param provider The extension provider
     * @param name The extension name
     * @param version The minimal version
     * @return An optional object either empty or wrapping the instance
     */
    fun <T> getExtensionThatSatisfyMetadata(
        extensionPoint: Class<T?>?,
        provider: String,
        name: String,
        version: String
    ): Optional<T?> {
        return loadFirst(
            ExtensionLoadContext.satisfyingData(
                sessionID,
                extensionPoint,
                identifier(provider, name, version)
            )
        )
    }

    /**
     * Retrieves a priority-ordered list with all extensions for the given
     * extension point.
     *
     * @param extensionPoint The extension point type
     * @return A list with the extensions, empty if none was found
     */
    fun <T> getExtensions(extensionPoint: Class<T?>?): Stream<T?>? {
        return loadAll(ExtensionLoadContext.all(sessionID, extensionPoint))
    }

    /**
     * Retrieves a priority-ordered list with all then extensions for the given
     * extension point that satisfies the specified condition.
     *
     * @param extensionPoint The extension point type
     * @param condition Only extensions satisfying this condition will be returned
     * @return A list with the extensions, empty if none was found
     */
    fun <T> getExtensionsThatSatisfy(extensionPoint: Class<T?>?, condition: Predicate<T?>?): Stream<T?>? {
        return loadAll(ExtensionLoadContext.satisfying(sessionID, extensionPoint, condition))
    }

    /**
     * Retrieves a priority-ordered list with all then extensions for the given
     * extension point that satisfies the specified condition.
     *
     * @param extensionPoint The extension point type
     * @param condition Only extensions which their metadata satisfies this
     * condition will be returned
     * @return A list with the extensions, empty if none was found
     */
    fun <T> getExtensionsThatSatisfyMetadata(
        extensionPoint: Class<T?>?,
        condition: Predicate<Extension?>?
    ): Stream<T?>? {
        return loadAll(ExtensionLoadContext.satisfyingData(sessionID, extensionPoint, condition))
    }

    /**
     * Creates a new session of the extension manager. Each session
     * will handle extensions marked with the [ExtensionScope.SESSION]
     * scope in isolation, returning a singleton instance per session.
     * Other scopes will be treated normally.
     *
     *
     * Internally, each instance of <tt>ExtensionManager</tt> is considered
     * an independent session, so this method is equivalent to:
     * `
     * new ExtensionManager(extensionManager.classLoaders())
    ` *
     *
     *
     * **IMPORTANT:** Each session created should
     * invoke the method [.clear] after being used. Otherwise,
     * session extension instances might remain permanently in memory.
     * @return A new extension manager that is isolated from the current
     * in the session scope
     */
    fun newSession(): ExtensionManager {
        return ExtensionManager(classLoaders)
    }

    /**
     * Clear any cached or referenced extension instances. This
     * should be the last call prior to discard the manager.
     *
     *
     * If you are using one <tt>ExtensionManager</tt> object as a singleton,
     * usually there is no need to invoke this method. However, it is of major
     * relevance when controlling the lifecycle of several instances.
     * @see .newSession
     */
    fun clear() {
        validExtensions.clear()
        invalidExtensions.clear()
        extensionMetadata.clear()
        builtInExtensionLoader.invalidateSession(sessionID)
        extensionLoaders.forEach(Consumer { loader: ExtensionLoader -> loader.invalidateSession(sessionID) })
    }

    /**
     * @return An unmodifiable list with the class loaders used by this manager
     */
    fun classLoaders(): List<ClassLoader?> {
        return Collections.unmodifiableList(classLoaders)
    }

    private fun <T> loadAll(context: ExtensionLoadContext<T?>?): Stream<T?>? {
        return obtainValidExtensions(context).stream()
            .filter(context!!.condition())
            .sorted(sortByPriority())
    }

    private fun <T> loadFirst(context: ExtensionLoadContext<T?>?): Optional<T?> {
        return obtainValidExtensions(context).stream()
            .filter(context!!.condition())
            .min(sortByPriority())
    }

    private fun <T> obtainValidExtensions(context: ExtensionLoadContext<T?>?): MutableList<T?> {
        validExtensions.putIfAbsent(context!!.extensionPoint(), HashSet())
        invalidExtensions.putIfAbsent(context.extensionPoint(), HashSet())
        val collectedExtensions: MutableList<T?> = ArrayList()
        collectValidExtensions<T?>(
            context.withInternalLoader(classLoaders, builtInExtensionLoader),
            collectedExtensions
        )
        for (extensionLoader in extensionLoaders) {
            collectValidExtensions<T?>(
                context.withExternalLoader(classLoaders, extensionLoader),
                collectedExtensions
            )
        }
        removeOverridenExtensions(collectedExtensions)
        return collectedExtensions
    }

    private fun <T> collectValidExtensions(
        context: ExtensionLoadContext<T?>?,
        collectedExtensions: MutableList<T?>
    ) {
        val extensionPoint = context!!.extensionPoint()
        LOGGER.debug("{} :: Searching...", context)
        for (extension in context.load()!!) {
            if (hasBeenInvalidated(extensionPoint, extension)) {
                LOGGER.debug(
                    "{} :: Found {} but ignored (it is marked as invalid)",
                    context,
                    extension
                )
                continue
            }
            var valid = true
            if (!hasBeenValidated(extensionPoint, extension)) {
                valid = validateExtension(context, extension)
            }
            if (valid) {
                LOGGER.debug("{} :: Found {}", context, extension)
                collectedExtensions.add(extension)
            } else {
                LOGGER.debug(
                    "{} :: Found {} but ignored (marked as invalid)",
                    context,
                    extension
                )
            }
        }
    }

    private fun <T> validateExtension(context: ExtensionLoadContext<T>?, extension: T): Boolean {
        val extensionPoint = context!!.extensionPoint()
        val extensionPointData = context.extensionPointData()
        val extensionData = getExtensionMetadata(extension)

        // this should not happen, but there is no guarantee that external
        // service loaders provides non-externally managed extensions
        if (extensionData.externallyManaged != context.isExternallyManaged()) {
            LOGGER.debug(
                "Class {} is{} externally managed and the extension loader is{}; ignored",
                extension!!::class.java,
                if (extensionData.externallyManaged) "" else " not",
                if (context.isExternallyManaged()) "" else " not"
            )
            invalidExtensions[extensionPoint]!!.add(extension.javaClass)
            return false
        }
        if (!areCompatible(extensionPointData, extensionData)) {
            if (LOGGER.isWarnEnabled) {
                LOGGER.warn(
                    "Extension point version of {} ({}) is not compatible with expected version {}",
                    id(extensionData),
                    extensionData.extensionPointVersion,
                    extensionPointData!!.version
                )
            }
            invalidExtensions[extensionPoint]!!.add(extension!!::class.java)
            return false
        }
        validExtensions[extensionPoint]!!.add(extension!!::class.java)
        return true
    }

    private fun <T> removeOverridenExtensions(extensions: MutableList<T>) {
        val overridableExtensions = extensions.stream()
            .filter { extension: T -> getExtensionMetadata(extension).overridable }
            .collect(Collectors.toList())
        val overridableExtensionClassNames = overridableExtensions.stream()
            .collect(
                Collectors.toMap(
                    { extension: T -> extension!!::class.java.getCanonicalName() },
                    Function.identity()
                )
            )
        for (extension in ArrayList(extensions)) {
            val metadata = getExtensionMetadata(extension)
            val overridable = overridableExtensionClassNames[metadata.overrides]
            if (overridable != null) {
                extensions.remove(overridable)
                if (LOGGER.isInfoEnabled) {
                    LOGGER.info(
                        "Extension {} overrides extension {}",
                        id(getExtensionMetadata(extension)),
                        id(getExtensionMetadata<T>(overridable))
                    )
                }
            }
        }
    }

    private fun areCompatible(extensionPointData: ExtensionPoint?, extensionData: Extension): Boolean {
        val extensionPointVersion = ExtensionVersion.of(extensionPointData!!.version)
        return try {
            val extensionDataPointVersion = ExtensionVersion.of(
                extensionData.extensionPointVersion
            )
            extensionDataPointVersion!!.isCompatibleWith(extensionPointVersion)
        } catch (e: IllegalArgumentException) {
            LOGGER.error("Bad extensionPointVersion in {}", id(extensionData))
            throw e
        }
    }

    private fun getExtensionPriority(extension: Any?): Int {
        return getExtensionMetadata(extension).priority
    }

    private fun <T> hasBeenValidated(extensionPoint: Class<T>?, extension: T): Boolean {
        return validExtensions[extensionPoint]!!.contains(extension!!::class.java)
    }

    private fun <T> hasBeenInvalidated(extensionPoint: Class<T>?, extension: T): Boolean {
        return invalidExtensions[extensionPoint]!!.contains(extension!!::class.java)
    }

    private fun sortByPriority(): Comparator<Any?> {
        return Comparator.comparingInt { extension: Any? -> getExtensionPriority(extension) }
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(ExtensionManager::class.java)
        private val builtInExtensionLoader: ExtensionLoader = InternalExtensionLoader()
        private fun id(extension: Extension): String {
            return extension.provider + ":" + extension.name + ":" + extension.version
        }

        private fun extensionLoaders(): List<ExtensionLoader> {
            val loaders: MutableList<ExtensionLoader> = ArrayList()
            ServiceLoader.load(ExtensionLoader::class.java).forEach(Consumer { e: ExtensionLoader -> loaders.add(e) })
            return loaders
        }

        private fun identifier(provider: String, name: String, version: String): Predicate<Extension?> {
            return Predicate { extension: Extension? ->
                extension!!.provider.equals(provider, ignoreCase = true) &&
                        extension.name.equals(name, ignoreCase = true) &&
                        ExtensionVersion.of(extension.version)!!.isCompatibleWith(ExtensionVersion.of(version))
            }
        }
    }
}