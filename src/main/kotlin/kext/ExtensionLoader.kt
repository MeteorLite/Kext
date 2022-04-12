
package kext

/**
 * This interface allows third-party contributors to implement custom
 * mechanisms to retrieve extension instances, instead of using the Java
 * [java.util.ServiceLoader] approach.
 *
 *
 * This is specially suited for IoC injection frameworks that may manage
 * object instances in a wide range of different ways.
 */
interface ExtensionLoader {
    /**
     * Given a expected type and a class loader, retrieves a collection
     * of instances of the type.
     * @param <T> The type of the extension point
     * @param type The type of the extension point
     * @param classLoaders The class loaders to be used
     * @param sessionID The string identifier of the extension manager session
     * @return An list with the retrieved instances. It cannot be null but can be empty.
    </T> */
    fun <T> load(type: Class<T>, classLoaders: MutableList<ClassLoader>?, sessionID: String?): MutableList<T>

    /**
     * Invalidate the given session, removing possible stored data from cache.
     *
     *
     * Clients can provide a void implementation if no session-related cache
     * is used.
     * @param sessionID The string identifier of the extension manager session
     */
    fun invalidateSession(sessionID: String)
}