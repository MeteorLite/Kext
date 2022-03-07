
package kext

/**
 * The different strategies that can be used each time an extension is
 * requested using the [ExtensionManager].
 */
enum class ExtensionScope {
    /** Keep a single instance  */
    GLOBAL,

    /** Create a new instance each time  */
    LOCAL,

    /** Keep a single instance per session  */
    SESSION
}