
package kext

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.nio.file.NoSuchFileException
import javax.annotation.processing.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ModuleElement
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror
import javax.tools.Diagnostic
import javax.tools.FileObject
import javax.tools.StandardLocation

/**
 * An extension processor that validate and publish the provided extensions
 */
@SupportedAnnotationTypes("kext.Extension")
@SupportedSourceVersion(SourceVersion.RELEASE_11)
class ExtensionProcessor : AbstractProcessor() {
    override fun process(annotations: MutableSet<out TypeElement>, roundEnv: RoundEnvironment): Boolean {
        val serviceImplementations: MutableMap<String?, MutableList<String?>?> = LinkedHashMap()
        for (extensionElement in roundEnv.getElementsAnnotatedWith(Extension::class.java)) {
            if (validateElementKindIsClass(extensionElement)) {
                validateAndRegisterExtension(
                    extensionElement as TypeElement, serviceImplementations
                )
            }
        }
        for (extensionPointElement in roundEnv
            .getElementsAnnotatedWith(ExtensionPoint::class.java)) {
            validateExtensionPoint(extensionPointElement)
        }
        writeMetaInfServiceDeclarations(serviceImplementations)
        return false
    }

    private fun validateExtensionPoint(extensionPointElement: Element) {
        if (extensionPointElement.kind != ElementKind.CLASS
            && extensionPointElement.kind != ElementKind.INTERFACE
        ) {
            log(
                Diagnostic.Kind.ERROR,
                extensionPointElement,
                "@ExtensionPoint not valid for {} (only processed for classes or interfaces)",
                extensionPointElement.simpleName
            )
        } else {
            val extensionPointAnnotation = extensionPointElement
                .getAnnotation(ExtensionPoint::class.java)
            validateVersionFormat(
                extensionPointAnnotation.version, extensionPointElement, "version"
            )
        }
    }

    private fun validateAndRegisterExtension(
        extensionElement: TypeElement,
        serviceImplementations: MutableMap<String?, MutableList<String?>?>
    ) {
        var ignore: Boolean
        val extensionAnnotation = extensionElement.getAnnotation(Extension::class.java)

        // not handling externally managed extensions
        ignore = extensionAnnotation.externallyManaged
        ignore = ignore || !validateVersionFormat(
            extensionAnnotation.version,
            extensionElement,
            "version"
        )
        ignore = ignore || !validateVersionFormat(
            extensionAnnotation.extensionPointVersion,
            extensionElement,
            "extensionPointVersion"
        )
        if (ignore) {
            return
        }
        val extensionPointName = computeExtensionPointName(
            extensionElement, extensionAnnotation
        )
        val extensionName = extensionElement.qualifiedName.toString()
        val extensionPointElement = processingEnv.elementUtils
            .getTypeElement(extensionPointName)
        val extensionInfo = ExtensionInfo(
            extensionElement,
            extensionName,
            extensionPointElement,
            extensionPointName
        )
        ignore = !validateExtensionPointClassExists(extensionInfo)
        ignore = ignore || !validateExtensionPointAnnotation(extensionInfo)
        ignore = ignore || !validateExtensionPointAssignableFromExtension(extensionInfo)
        notifyExtensionDeclaredInModuleInfo(extensionInfo)
        if (!ignore) {
            serviceImplementations
                .computeIfAbsent(
                    extensionPointName
                ) { ArrayList() }
                ?.add(extensionName)
        }
    }

    private fun notifyExtensionDeclaredInModuleInfo(extensionInfo: ExtensionInfo) {
        val module = processingEnv.elementUtils
            .getModuleOf(extensionInfo.extensionElement)
        if (module.isUnnamed) {
            return
        }
        val declaredInModule = module.directives
            .stream()
            .filter { directive: ModuleElement.Directive -> directive.getKind() == ModuleElement.DirectiveKind.PROVIDES }
            .map { obj: Any? -> ModuleElement.ProvidesDirective::class.java.cast(obj) }
            .filter { provides: ModuleElement.ProvidesDirective -> provides.getService() == extensionInfo.extensionPointElement }
            .flatMap { provides: ModuleElement.ProvidesDirective -> provides.getImplementations().stream() }
            .anyMatch { obj: Any -> extensionInfo.extensionElement!!.equals(obj) }
        if (!declaredInModule) {
            log(
                Diagnostic.Kind.MANDATORY_WARNING,
                extensionInfo.extensionElement!!,
                "{} must be declared with the directive 'provides' in module-info.java in order to be used properly",
                extensionInfo.extensionName,
                extensionInfo.extensionPointName
            )
        }
    }

    private fun validateExtensionPointAssignableFromExtension(extensionInfo: ExtensionInfo): Boolean {
        if (!isAssignable(
                extensionInfo.extensionElement!!.asType(), extensionInfo.extensionPointElement!!.asType()
            )
        ) {
            log(
                Diagnostic.Kind.ERROR,
                extensionInfo.extensionElement,
                "{} must implement or extend the extension point type {}",
                extensionInfo.extensionName,
                extensionInfo.extensionPointName
            )
            return false
        }
        return true
    }

    private fun validateExtensionPointAnnotation(extensionInfo: ExtensionInfo): Boolean {
        if (extensionInfo.extensionPointElement?.getAnnotation(ExtensionPoint::class.java) == null) {
            log(
                Diagnostic.Kind.ERROR,
                extensionInfo.extensionElement!!,
                "Expected extension point type '{}' is not annotated with @ExtensionPoint",
                extensionInfo.extensionPointName
            )
            return false
        }
        return true
    }

    private fun validateExtensionPointClassExists(extensionInfo: ExtensionInfo): Boolean {
        if (extensionInfo.extensionPointElement == null) {
            log(
                Diagnostic.Kind.ERROR,
                extensionInfo.extensionElement!!,
                "Cannot find extension point class '{}'",
                extensionInfo.extensionPointName
            )
            return false
        }
        return true
    }

    private fun computeExtensionPointName(
        extensionClassElement: TypeElement,
        extensionAnnotation: Extension
    ): String? {
        var extensionPoint: String = extensionAnnotation.extensionPoint
        if (extensionPoint.isEmpty()) {
            for (implementedInterface in extensionClassElement.interfaces) {
                extensionPoint = implementedInterface.toString()
                // remove the <..> part in case it is a generic class
                extensionPoint = extensionPoint.replace("\\<[^\\>]*\\>".toRegex(), "")
            }
        }
        return extensionPoint
    }

    fun validateElementKindIsClass(element: Element): Boolean {
        if (element.kind != ElementKind.CLASS) {
            log(
                Diagnostic.Kind.WARNING,
                element,
                "@Extension ignored for {} (only processed for classes)",
                element.simpleName
            )
            return false
        }
        return true
    }

    private fun validateVersionFormat(version: String, element: Element, fieldName: String?): Boolean {
        val valid = version.matches("\\d+.d+(\\..*)?".toRegex())
        if (!valid) {
            log(
                Diagnostic.Kind.ERROR,
                element,
                "Content of field {} ('{}') must be in form '<major>.<minor>(.<patch>)'",
                fieldName,
                version
            )
        }
        return valid
    }

    private fun isAssignable(type: TypeMirror?, typeTo: TypeMirror?): Boolean {
        if (nameWithoutGeneric(type) == nameWithoutGeneric(typeTo)) {
            return true
        }
        for (superType in processingEnv.typeUtils.directSupertypes(type)) {
            if (isAssignable(superType, typeTo)) {
                return true
            }
        }
        return false
    }

    private fun nameWithoutGeneric(type: TypeMirror?): String? {
        val genericPosition = type.toString().indexOf('<')
        return if (genericPosition < 0) type.toString() else type.toString().substring(0, genericPosition)
    }

    private fun writeMetaInfServiceDeclarations(serviceImplementations: MutableMap<String?, MutableList<String?>?>) {
        val filer = processingEnv.filer
        for (mapEntry in serviceImplementations.entries) {
            val extension = mapEntry.key
            val resourcePath = "META-INF/services/$extension"
            try {
                writeFile(filer, resourcePath, mapEntry)
            } catch (e: IOException) {
                log(Diagnostic.Kind.ERROR, "UNEXPECTED ERROR: {}", e.toString())
            }
        }
    }

    @Throws(IOException::class)
    private fun writeFile(
        filer: Filer,
        resourcePath: String?,
        entry: MutableMap.MutableEntry<String?, MutableList<String?>?>
    ) {
        var resourceFile = filer
            .getResource(StandardLocation.CLASS_OUTPUT, "", resourcePath)
        val oldExtensions = if (resourceFile.lastModified == 0L) listOf() else readLines(resourceFile)
        val allExtensions: MutableSet<String?> = LinkedHashSet()
        allExtensions.addAll(oldExtensions)
        allExtensions.addAll(entry.value!!)
        resourceFile = filer.createResource(StandardLocation.CLASS_OUTPUT, "", resourcePath)
        writeLines(allExtensions, resourceFile)
        log(Diagnostic.Kind.NOTE, "[kext] :: Generated service declaration file {}", resourceFile.name)
    }

    private fun readLines(resourceFile: FileObject): MutableList<String?> {
        val lines: MutableList<String?> = ArrayList()
        try {
            BufferedReader(resourceFile.openReader(true)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    lines.add(line)
                }
            }
        } catch (e: NoSuchFileException) {
            log(Diagnostic.Kind.ERROR, "File does not exist : {} [error was {}]", resourceFile.toUri(), e.toString())
        } catch (e: IOException) {
            log(Diagnostic.Kind.ERROR, "Cannot read file {} : {}", resourceFile.toUri(), e.toString())
        }
        return lines
    }

    private fun writeLines(lines: MutableSet<String?>, resourceFile: FileObject) {
        try {
            BufferedWriter(resourceFile.openWriter()).use { writer ->
                for (line in lines) {
                    writer.append(line)
                    writer.newLine()
                }
            }
        } catch (e: IOException) {
            log(Diagnostic.Kind.ERROR, "error writing {} : {}", resourceFile.toUri(), e.toString())
        }
    }

    private fun log(kind: Diagnostic.Kind?, message: String, vararg messageArgs: Any?) {
        processingEnv.messager
            .printMessage(
                kind,
                "[kext] :: " + String.format(message.replace("{}", "%s"), *messageArgs)
            )
    }

    private fun log(kind: Diagnostic.Kind?, element: Element, message: String, vararg messageArgs: Any?) {
        processingEnv.messager
            .printMessage(
                kind,
                "[kext] at " + element.asType().toString() + " :: " + String.format(
                    message.replace("{}", "%s"),
                    *messageArgs
                )
            )
    }

    private class ExtensionInfo(
        val extensionElement: TypeElement?,
        val extensionName: String?,
        val extensionPointElement: TypeElement?,
        val extensionPointName: String?
    )
}