package me.tatarka.inject.test

import assertk.Assert
import assertk.assertions.isNotNull
import assertk.assertions.message
import com.tschuchort.compiletesting.*
import me.tatarka.inject.compiler.kapt.InjectCompiler
import me.tatarka.inject.compiler.kapt.ScopedInjectCompiler
import me.tatarka.inject.compiler.ksp.InjectProcessor
import org.jetbrains.kotlin.ksp.processing.SymbolProcessor
import java.io.File
import javax.annotation.processing.Processor

class ProjectCompiler(private val root: File, private val target: Target) {

    private val sourceFiles = mutableListOf<SourceFile>()

    fun setup(): ProjectCompiler {
        return this
    }

    fun source(fileName: String, source: String): ProjectCompiler {
        sourceFiles.add(
            SourceFile.kotlin(
                fileName,
                source
            )
        )
        return this
    }

    fun clear() {
        sourceFiles.clear()
    }

    fun compile() {
        val result = KotlinCompilation().apply {
            sources = sourceFiles
            inheritClassPath = true
            workingDir = root
            when (target) {
                Target.kapt -> {
                    annotationProcessors = listOf<Processor>(InjectCompiler(), ScopedInjectCompiler())
                }
                Target.ksp -> {
                    symbolProcessors = listOf<SymbolProcessor>(InjectProcessor())
                }
            }
        }.compile()

        if (result.exitCode != KotlinCompilation.ExitCode.OK) {
            throw Exception(result.messages)
        }
    }
}

fun Assert<Throwable>.output(): Assert<String> = message().isNotNull()

enum class Target {
    kapt, ksp
}
