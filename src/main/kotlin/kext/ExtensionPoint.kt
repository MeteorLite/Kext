
package kext

/**
 * This annotation allows to mark an interface or abstract class as an
 * extension point managed by the [ExtensionManager].
 *
 *
 * In order to ensure compatibility between the extension point and its
 * extensions, it is important to maintain correctly the [.version]
 * property. If you are intended to break backwards compatibility keeping the
 * same package and type name, increment the major part of the version in
 * order to avoid runtime errors. Otherwise, increment the minor part of the
 * version in order to state the previous methods are still valid.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS)
annotation class ExtensionPoint(
    /**
     * The version of the extension point in form of
     * `<majorVersion>.<minorVersion>`
     */
    val version: String = "1.0"
)