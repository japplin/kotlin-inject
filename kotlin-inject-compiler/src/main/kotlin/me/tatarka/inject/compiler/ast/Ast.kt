package me.tatarka.inject.compiler.ast

import com.squareup.kotlinpoet.ClassName
import kotlin.reflect.KClass

interface AstProvider {

    fun KClass<*>.toAstClass(): AstClass

    fun AstType.toAstClass(): AstClass

    fun declaredTypeOf(astClass: AstClass, vararg astTypes: AstType): AstType

    fun declaredTypeOf(klass: KClass<*>, vararg astTypes: AstType): AstType

    fun error(message: String, element: AstElement?)

    fun warn(message: String, element: AstElement?)
}

interface AstElement

interface AstAnnotated {
    val annotations: List<AstAnnotation>

    fun <T : Annotation> annotationOf(klass: KClass<T>): T?
}

interface AstClass : AstElement, AstAnnotated {

    val packageName: String

    val name: String

    val modifiers: Set<AstModifier>

    val companion: AstClass?

    val superclass: AstClass?

    val interfaces: List<AstClass>

    val constructors: List<AstConstructor>

    val methods: List<AstMethod>

    val type: AstType

    override val annotations: List<AstAnnotation>
        get() = type.annotations

    fun visitInheritanceChain(f: (AstClass) -> Unit) {
        f(this)
        superclass?.visitInheritanceChain(f)
        interfaces.forEach { it.visitInheritanceChain(f) }
    }

    fun asClassName(): ClassName
}

interface AstConstructor : AstElement {
    val type: AstType

    val parameters: List<AstParam>
}

sealed class AstMethod : AstElement, AstAnnotated {

    abstract val name: String

    abstract val modifiers: Set<AstModifier>

    abstract val receiverParameterType: AstType?

    abstract val returnType: AstType

    abstract fun returnTypeFor(enclosingClass: AstClass): AstType
}

abstract class AstFunction : AstMethod() {
    abstract val parameters: List<AstParam>
}

abstract class AstProperty: AstMethod()

interface AstType : AstElement, AstAnnotated {

    val packageName: String

    val name: String

    val simpleName: String

    val abbreviatedTypeName: String?

    val arguments: List<AstType>

    fun isUnit(): Boolean

    fun isNotUnit() = !isUnit()
}

interface AstAnnotation : AstElement, AstAnnotated {
    val type: AstType
}

interface AstParam : AstElement {

    val name: String

    val type: AstType

    val modifiers: Set<AstModifier>

    fun <T : Annotation> annotationOf(klass: KClass<T>): T?
}

enum class AstModifier {
    PRIVATE, ABSTRACT
}
