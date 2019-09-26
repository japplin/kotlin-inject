package me.tatarka.inject.compiler.ast

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy

fun AstType.asTypeName(): TypeName {
    abbreviatedTypeName?.let {
        return ClassName.bestGuess(it)
    }
    return ClassName(packageName, simpleName).run {
        if (arguments.isNotEmpty()) {
            parameterizedBy(*(arguments.map { it.asTypeName() }.toTypedArray()))
        } else {
            this
        }
    }.javaToKotlinType()
}

fun ParameterSpec.Companion.parametersOf(constructor: AstConstructor): List<ParameterSpec> =
        constructor.parameters.map { it.asParameterSpec() }

fun AstParam.asParameterSpec(): ParameterSpec = ParameterSpec.builder(name, type.asTypeName())
        .addModifiers(modifiers.map { it.asKModifier() })
        .build()

fun AstModifier.asKModifier(): KModifier {
    return when (this) {
        AstModifier.PRIVATE -> KModifier.PRIVATE
        AstModifier.ABSTRACT -> KModifier.ABSTRACT
    }
}

private fun TypeName.javaToKotlinType(): TypeName = if (this is ParameterizedTypeName) {
    (rawType.javaToKotlinType() as ClassName).parameterizedBy(
            *typeArguments.map { it.javaToKotlinType() }.toTypedArray()
    )
} else {
    when (val s = toString()) {
        "java.util.Map" -> ClassName.bestGuess("kotlin.collections.Map")
        "java.util.Set" -> ClassName.bestGuess("kotlin.collections.Set")
        "java.lang.String" -> ClassName.bestGuess("kotlin.String")
        else -> {
            val regex = Regex("kotlin\\.jvm\\.functions\\.(Function[0-9]+)")
            val result = regex.matchEntire(s)
            if (result != null) {
                ClassName.bestGuess("kotlin.${result.groupValues[1]}")
            } else {
                this
            }
        }
    }
}

