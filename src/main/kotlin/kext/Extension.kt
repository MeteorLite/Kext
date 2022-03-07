/**
 * @author Luis Iñesta Gelabert - luiinge@gmail.com
 */
package kext

import java.util.*

/**
 * This annotation allows to mark a class as an extension managed by the
 * [ExtensionManager].
 *
 *
 * Notice that any class not annotated with [Extension] will not be
 * managed in spite of implementing or extending the [ExtensionPoint]
 * class.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS)
annotation class Extension(
    /** The provider (organization, package, group, etc.) of the extension  */
    val provider: String,
    /** The name of the extension  */
    val name: String,
    /**
     * The version of the extension in form of
     * `<majorVersion>.<minorVersion>.<patch>`
     */
    val version: String,
    /**
     * The class name of the extension point that is extended.
     *
     *
     * If this field is not provided and the extension class implements directly
     * the extension point class, it will automatically infer the value as the
     * qualified name of the extension point class. Notice that, if the extension
     * point class uses generic parameters, the inference mechanism will not
     * work, so clients must provide the name of the class directly in those
     * cases.
     *
     */
    val extensionPoint: String = "",
    /**
     * The minimum version of the extension point that is extended in form of
     * `<majorVersion>.<minorVersion>` .
     *
     *
     * If an incompatible version is used (that is, the major part of the version
     * is different), the extension manager will emit a warning and prevent the
     * extension from loading.
     *
     */
    val extensionPointVersion: String = "1.0",
    /**
     * Extensions marked as externally managed will not resolved using the
     * [ServiceLoader] mechanism. Instead, custom [ExtensionLoader]
     * will be used to retrieve the instance of the extension.
     */
    val externallyManaged: Boolean = false,
    /**
     * Scope where this extension should be instantiated
     */
    val scope: ExtensionScope = ExtensionScope.GLOBAL,
    /**
     * Priority used when extensions collide, the highest value have priority
     * over others.
     */
    val priority: Int = NORMAL_PRORITY,
    /**
     * Defines whether or not this extension can be overridden by other extension that
     * extends the implementation.
     */
    val overridable: Boolean = true,
    /**
     * The qualified class name of another extension that should be replaced by
     * this extension in case both of them are valid alternatives. It only has
     * effect if the [.overridable] property of the extension to override
     * is set to `true`.
     */
    val overrides: String = ""
) {
    companion object {
       const val NORMAL_PRORITY = 5
    }
}