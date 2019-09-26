package me.tatarka.inject.compiler

import kotlinx.metadata.KmClass
import kotlinx.metadata.jvm.KotlinClassHeader
import kotlinx.metadata.jvm.KotlinClassMetadata
import me.tatarka.inject.annotations.Scope
import me.tatarka.inject.compiler.ast.AstAnnotated
import me.tatarka.inject.compiler.ast.AstType
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement

fun Element.scope(): AnnotationMirror? = annotationMirrors.find {
    it.annotationType.asElement().getAnnotation(Scope::class.java) != null
}

fun Element.scopeType(): TypeElement? = scope()?.let { it.annotationType.asElement() as TypeElement }

fun AstAnnotated.scopeType(): AstType? = annotations.find { it.annotationOf(Scope::class) != null }?.type

fun String.asScopedProp(): String = "_" + decapitalize()

val TypeElement.metadata: KmClass? get() {
    val meta = getAnnotation(Metadata::class.java) ?: return null
    val header = KotlinClassHeader(
        kind =  meta.kind,
        bytecodeVersion = meta.bytecodeVersion,
        data1 = meta.data1,
        data2 = meta.data2,
        extraInt = meta.extraInt,
        extraString = meta.extraString,
        metadataVersion = meta.metadataVersion,
        packageName = meta.packageName
    )
    val metadata = KotlinClassMetadata.read(header) ?: return null
    if (metadata !is KotlinClassMetadata.Class) return null
    return metadata.toKmClass()
}
