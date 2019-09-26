package me.tatarka.inject.compiler.ast

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import kotlinx.metadata.*
import kotlinx.metadata.jvm.annotations
import kotlinx.metadata.jvm.getterSignature
import kotlinx.metadata.jvm.signature
import kotlinx.metadata.jvm.syntheticMethodForAnnotations
import me.tatarka.inject.compiler.metadata
import org.jetbrains.kotlin.psi.propertyVisitor
import javax.annotation.processing.Messager
import javax.lang.model.element.Element
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.ExecutableType
import javax.lang.model.type.NoType
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.ElementFilter
import javax.lang.model.util.Elements
import javax.lang.model.util.Types
import javax.tools.Diagnostic
import kotlin.reflect.KClass


interface KaptAstProvider : AstProvider {
    val types: Types
    val elements: Elements
    val messager: Messager

    fun TypeElement.toAstClass(): AstClass = KaptAstClass(this@KaptAstProvider, this, metadata)

    override fun KClass<*>.toAstClass(): AstClass = elements.getTypeElement(java.canonicalName).toAstClass()

    override fun AstType.toAstClass(): AstClass = (types.asElement((this as KaptAstType).type) as TypeElement).toAstClass()

    override fun declaredTypeOf(astClass: AstClass, vararg astTypes: AstType): AstType {
        return KaptAstType(
                this, types.getDeclaredType((astClass as KaptAstClass).element, *astTypes.map { (it as KaptAstType).type }.toTypedArray()),
                astClass.kmClass?.type
        )
    }

    override fun declaredTypeOf(klass: KClass<*>, vararg astTypes: AstType): AstType {
        val type = elements.getTypeElement(klass.java.canonicalName)
        val declaredType = types.getDeclaredType(type, *astTypes.map { (it as KaptAstType).type }.toTypedArray())
        return KaptAstType( this, declaredType, klass.toKmType())
    }

    override fun error(message: String, element: AstElement?) {
        messager.printMessage(Diagnostic.Kind.ERROR, message, (element as? KaptAstElement)?.element)
    }

    override fun warn(message: String, element: AstElement?) {
        messager.printMessage(Diagnostic.Kind.WARNING, message, (element as? KaptAstElement)?.element)
    }
}

abstract class KaptAstElement(provider: KaptAstProvider) : AstElement, KaptAstProvider by provider {
    internal abstract val element: Element
}

class KaptAstClass(provider: KaptAstProvider, val element: TypeElement, internal val kmClass: KmClass?) :
        AstClass, KaptAstProvider by provider {

    override val packageName: String get() = elements.getPackageOf(element).qualifiedName.toString()

    override val name: String get() = element.simpleName.toString()

    override val modifiers: Set<AstModifier> by lazy { collectModifiers(kmClass?.flags) }

    override val companion: AstClass? by lazy {
        val companionName = kmClass?.companionObject ?: return@lazy null
        val companionType = ElementFilter.typesIn(element.enclosedElements).firstOrNull { type ->
            type.simpleName.contentEquals(companionName)
        }
        companionType?.toAstClass()
    }

    override val superclass: AstClass? by lazy {
        val superclassType = element.superclass
        if (superclassType is NoType) return@lazy null
        val superclass = provider.types.asElement(superclassType) as TypeElement
        superclass.toAstClass()
    }

    override val interfaces: List<AstClass> by lazy {
        element.interfaces.mapNotNull { ifaceType ->
            val iface = provider.types.asElement(ifaceType) as TypeElement
            iface.toAstClass()
        }
    }

    override val constructors: List<AstConstructor> by lazy {
        ElementFilter.constructorsIn(element.enclosedElements).mapNotNull { constructor ->
            //TODO: not sure how to match constructors
            KaptAstConstructor(this, this, constructor, kmClass?.constructors?.first())
        }
    }

    override val methods: List<AstMethod> by lazy {
        ElementFilter.methodsIn(element.enclosedElements).mapNotNull { method ->
            if (kmClass != null) {
                for (property in kmClass.properties) {
                    val javaName = property.getterSignature?.name ?: continue
                    if (method.simpleName.contentEquals(javaName)) {
                        return@mapNotNull KaptAstProperty(this, method, property)
                    }
                }
                for (function in kmClass.functions) {
                    val javaName = function.signature?.name
                    if (method.simpleName.contentEquals(javaName)) {
                        return@mapNotNull KaptAstFunction(this, method, function)
                    }
                }
            }
            null
        }
    }

    override val type: AstType by lazy {
        KaptAstType(this, element.asType(), kmClass?.type)
    }

    override fun <T : Annotation> annotationOf(klass: KClass<T>): T? = element.getAnnotation(klass.java)

    override fun asClassName(): ClassName = element.asClassName()

    override fun toString(): String {
        return if (packageName.isEmpty() || packageName == "kotlin") name else "$packageName.$name"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as KaptAstClass
        if (element != other.element) return false
        return true
    }

    override fun hashCode(): Int {
        return element.hashCode()
    }
}

class KaptAstConstructor(
        provider: KaptAstProvider,
        private val parent: AstClass,
        val element: ExecutableElement,
        private val kmConstructor: KmConstructor?
) : AstConstructor {
    override val type: AstType get() = parent.type

    override val parameters: List<AstParam> by lazy {
        element.parameters.mapNotNull { element ->
            if (kmConstructor != null) {
                for (parameter in kmConstructor.valueParameters) {
                    if (element.simpleName.contentEquals(parameter.name)) {
                        return@mapNotNull KaptAstParam(provider, element, parameter)
                    }
                }
            }
            null
        }
    }
}

class KaptAstFunction(val provider: KaptAstProvider, val element: ExecutableElement, private val kmFunction: KmFunction) : AstFunction() {

    override val name: String get() = kmFunction.name

    override val modifiers: Set<AstModifier> by lazy { collectModifiers(kmFunction.flags) }

    override val returnType: AstType
        get() = KaptAstType(provider, element.returnType, kmFunction.returnType)

    override fun returnTypeFor(enclosingClass: AstClass): AstType {
        val declaredType = (enclosingClass as KaptAstClass).element.asType() as DeclaredType
        val methodType = provider.types.asMemberOf(declaredType, element) as ExecutableType
        return KaptAstType(provider, methodType.returnType, kmFunction.returnType)
    }

    override val receiverParameterType: AstType?
        get() = kmFunction.receiverParameterType?.let {
            KaptAstType(provider, element.parameters[0].asType(), it)
        }

    override val parameters: List<AstParam> by lazy {
        element.parameters.mapNotNull { element ->
            for (parameter in kmFunction.valueParameters) {
                if (element.simpleName.contentEquals(parameter.name)) {
                    return@mapNotNull KaptAstParam(provider, element, parameter)
                }
            }
            null
        }
    }

    override val annotations: List<AstAnnotation> by lazy {
        element.annotationMirrors.map { KaptAstAnnotation(provider, it.annotationType, null) }
    }

    override fun <T : Annotation> annotationOf(klass: KClass<T>): T? {
        return element.getAnnotation(klass.java)
    }
}

class KaptAstProperty(val provider: KaptAstProvider, val element: ExecutableElement, private val kmProperty: KmProperty) :
        AstProperty() {

    override val name: String get() = kmProperty.name

    override val modifiers: Set<AstModifier> by lazy {
        val result = mutableSetOf<AstModifier>()
        val flags = kmProperty.flags
        if (Flag.Common.IS_PRIVATE(flags)) {
            result.add(AstModifier.PRIVATE)
        }
        if (Flag.Common.IS_ABSTRACT(flags)) {
            result.add(AstModifier.ABSTRACT)
        }
        result
    }

    override val returnType: AstType
        get() = KaptAstType(provider, element.returnType, kmProperty.returnType)

    override fun returnTypeFor(enclosingClass: AstClass): AstType {
        val declaredType = (enclosingClass as KaptAstClass).element.asType() as DeclaredType
        val methodType = provider.types.asMemberOf(declaredType, element) as ExecutableType
        return KaptAstType(provider, methodType.returnType, kmProperty.returnType)
    }

    override val receiverParameterType: AstType?
        get() = kmProperty.receiverParameterType?.let {
            KaptAstType(provider, element.parameters[0].asType(), it)
        }

    private val annotatedElement: Element? by lazy {
        val javaName = kmProperty.syntheticMethodForAnnotations?.name ?: return@lazy null
        for (method in ElementFilter.methodsIn(element.enclosingElement.enclosedElements)) {
            if (method.simpleName.contentEquals(javaName)) {
                return@lazy method
            }
        }
        null
    }

    override val annotations: List<AstAnnotation>
        get() = annotatedElement?.annotationMirrors?.map { KaptAstAnnotation(provider, it.annotationType, null) }
                ?: emptyList()

    override fun <T : Annotation> annotationOf(klass: KClass<T>): T? {
        return annotatedElement?.getAnnotation(klass.java)
    }
}

class KaptAstType(val provider: KaptAstProvider, val type: TypeMirror, private val kmType: KmType?) : AstType {

    override val packageName: String by lazy {
        provider.elements.getPackageOf(provider.types.asElement(type)).qualifiedName.toString()
    }

    override val name: String by lazy {
        if (kmType != null) {
            when (val c = kmType.classifier) {
                is KmClassifier.TypeAlias -> c.name.replace('/', '.')
                else -> asTypeName().toString()
            }
        } else {
            asTypeName().toString()
        }
    }

    override val simpleName: String by lazy {
        provider.types.asElement(type).simpleName.toString()
    }

    override val annotations: List<AstAnnotation> by lazy {
        val typeAnnotations = provider.types.asElement(type).annotationMirrors
        if (kmType != null && kmType.annotations.size == typeAnnotations.size) {
            kmType.annotations.map { annotation ->
                val mirror = provider.elements.getTypeElement(annotation.className.replace('/', '.'))
                KaptAstAnnotation(provider, mirror.asType() as DeclaredType, annotation)
            }
        } else {
            typeAnnotations.map { KaptAstAnnotation(provider, it.annotationType, null) }
        }
    }

    override fun <T : Annotation> annotationOf(klass: KClass<T>): T? {
        return provider.types.asElement(type).getAnnotation(klass.java)
    }

    override val abbreviatedTypeName: String?
        get() {
            return (kmType?.abbreviatedType?.classifier as? KmClassifier.TypeAlias)?.name?.replace('/', '.')
        }

    override val arguments: List<AstType> by lazy {
        val typeArgs = (type as DeclaredType).typeArguments
        if (kmType != null && typeArgs.size == kmType.arguments.size) {
            typeArgs.zip(kmType.arguments).map { (a1, a2) ->
                KaptAstType(provider, a1, a2.type)
            }
        } else {
            typeArgs.map { KaptAstType(provider, it, null) }
        }
    }

    override fun isUnit(): Boolean = type is NoType

    override fun equals(other: Any?): Boolean {
        if (other !is AstType) return false
        return asTypeName() == other.asTypeName()
    }

    override fun hashCode(): Int {
        return asTypeName().hashCode()
    }

    override fun toString(): String {
        val n = name
        return if (n.substringBefore("<").substringBeforeLast('.') == "kotlin") n.removePrefix("kotlin.") else n
    }
}

class KaptAstAnnotation(val provider: KaptAstProvider, val annotationType: DeclaredType, private val kmAnnotation: KmAnnotation?) :
        AstAnnotation {

    override val type: AstType
        get() = KaptAstType(provider, annotationType, kmAnnotation?.type)

    override val annotations: List<AstAnnotation>
        get() = annotationType.asElement().annotationMirrors.map { KaptAstAnnotation(provider, it.annotationType, null) }

    override fun <T : Annotation> annotationOf(klass: KClass<T>): T? {
        return annotationType.asElement().getAnnotation(klass.java)
    }

    override fun equals(other: Any?): Boolean {
        if (other !is KaptAstAnnotation) return false
        return kmAnnotation == other.kmAnnotation
    }

    override fun hashCode(): Int {
        return kmAnnotation.hashCode()
    }

    override fun toString(): String {
        return "@$annotationType(${kmAnnotation?.arguments?.toList()?.joinToString(separator = ", ") { (name, value) -> "$name=${value.value}" }})"
    }
}

class KaptAstParam(val provider: KaptAstProvider, val element: VariableElement, val kmValueParameter: KmValueParameter) :
        AstParam {

    override val name: String get() = kmValueParameter.name

    override val type: AstType by lazy {
        KaptAstType(provider, element.asType(), kmValueParameter.type!!)
    }

    override val modifiers: Set<AstModifier>
        get() = collectModifiers(kmValueParameter.flags)

    override fun <T : Annotation> annotationOf(klass: KClass<T>): T? = element.getAnnotation(klass.java)
}

private val KmClass.type: KmType
    get() = KmType(flags = flags).apply {
        classifier = KmClassifier.Class(name)
    }

private val KmAnnotation.type: KmType
    get() = KmType(0).apply {
        classifier = KmClassifier.Class(className)
    }

private fun KClass<*>.toKmType(): KmType = KmType(0).apply {
    classifier = KmClassifier.Class(java.canonicalName)
}

private fun collectModifiers(flags: Flags?): Set<AstModifier> {
    val result = mutableSetOf<AstModifier>()
    if (flags == null) return result
    if (Flag.Common.IS_PRIVATE(flags)) {
        result.add(AstModifier.PRIVATE)
    }
    if (Flag.Common.IS_ABSTRACT(flags)) {
        result.add(AstModifier.ABSTRACT)
    }
    return result
}

