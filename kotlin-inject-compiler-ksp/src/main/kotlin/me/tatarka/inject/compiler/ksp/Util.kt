package me.tatarka.inject.compiler.ksp

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeVariableName
import org.jetbrains.kotlin.ksp.isAbstract
import org.jetbrains.kotlin.ksp.symbol.*
import org.jetbrains.kotlin.ksp.visitor.KSDefaultVisitor

fun KSAnnotated.typeAnnotatedWith(className: String): KSType? {
    for (annotation in annotations) {
        val t = annotation.annotationType.resolve()
        if (t?.declaration?.hasAnnotation(className) == true) {
            return t
        }
    }
    return null
}

fun KSAnnotated.hasAnnotation(className: String): Boolean {
    return annotations.any { it.annotationType.resolve()?.declaration?.qualifiedName?.asString() == className }
}

fun KSDeclaration.asClassName(): ClassName {
    val name = qualifiedName!!
    val packageName = packageName.asString()
    val shortName = name.asString().removePrefix("$packageName.")
    return ClassName(if (packageName == "<root>") "" else packageName, shortName.split('.'))
}

fun KSDeclaration.isAbstract() = when (this) {
    is KSFunctionDeclaration -> isAbstract
    is KSPropertyDeclaration -> {
        isAbstractWorkaround()
    }
    is KSClassDeclaration -> isAbstract()
    else -> false
}

// https://github.com/android/kotlin/issues/107
fun KSPropertyDeclaration.isAbstractWorkaround() = this.modifiers.contains(Modifier.ABSTRACT) ||
        ((this.parentDeclaration as? KSClassDeclaration)?.classKind == ClassKind.INTERFACE && getter?.origin == Origin.SYNTHETIC)

fun KSTypeReference.memberOf(enclosingClass: KSClassDeclaration): KSTypeReference {
    val declaration = resolve()!!.declaration
    return if (declaration is KSTypeParameter) {
        val parent = declaration.parentDeclaration!!
        val resolvedParent =
            enclosingClass.superTypes.first { it.resolve()!!.declaration.qualifiedName == parent.qualifiedName }
                .resolve()!!
        val typePosition = parent.typeParameters.indexOfFirst { it.name == declaration.name }
        resolvedParent.arguments[typePosition].type!!
    } else {
        this
    }
}

fun KSType.asTypeName(): TypeName {

    return declaration.accept(object : KSDefaultVisitor<Unit, TypeName>() {

        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit): TypeName {
            return fromDeclaration(classDeclaration)
        }

        override fun visitTypeAlias(typeAlias: KSTypeAlias, data: Unit): TypeName {
            return fromDeclaration(typeAlias)
        }

        override fun visitTypeParameter(typeParameter: KSTypeParameter, data: Unit): TypeName {
            return TypeVariableName(
                name = typeParameter.name.asString(),
                bounds = typeParameter.bounds.map { it.resolve()!!.asTypeName() },
                variance = when (typeParameter.variance) {
                    Variance.COVARIANT -> KModifier.IN
                    Variance.CONTRAVARIANT -> KModifier.OUT
                    else -> null
                }
            )
        }

        private fun fromDeclaration(declaration: KSDeclaration): TypeName {
            val rawType = declaration.asClassName().copy(nullable = nullability == Nullability.NULLABLE) as ClassName
            if (declaration.typeParameters.isEmpty()) {
                return rawType
            }
            val typeArgumentNames = mutableListOf<TypeName>()
            for (typeArgument in arguments) {
                typeArgumentNames += typeArgument.type!!.resolve()!!.asTypeName()
            }
            return rawType.parameterizedBy(typeArgumentNames)
        }

        override fun defaultHandler(node: KSNode, data: Unit): TypeName {
            throw IllegalArgumentException("Unexpected node: $node")
        }
    }, Unit)
}

