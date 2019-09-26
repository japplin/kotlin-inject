package me.tatarka.inject.compiler

import me.tatarka.inject.annotations.Inject
import me.tatarka.inject.annotations.Module
import me.tatarka.inject.compiler.ast.AstClass
import me.tatarka.inject.compiler.ast.KaptAstProvider
import java.io.File
import javax.annotation.processing.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement
import javax.lang.model.util.Elements
import javax.lang.model.util.Types

private const val OPTION_KAPT_KOTLIN_GENERATED = "kapt.kotlin.generated"

private const val OPTION_GENERATE_COMPANION_EXTENSIONS = "me.tatarka.inject.generateCompanionExtensions"

class InjectCompiler : AbstractProcessor(), KaptAstProvider {

    private lateinit var generatedSourcesRoot: String
    private var generateCompanionExtensions: Boolean = false

    private lateinit var filer: Filer
    override lateinit var types: Types
    override lateinit var elements: Elements
    override lateinit var messager: Messager
    private lateinit var processor: InjectProcessor

    override fun init(processingEnv: ProcessingEnvironment) {
        super.init(processingEnv)
        this.generatedSourcesRoot = processingEnv.options[OPTION_KAPT_KOTLIN_GENERATED]!!
        this.generateCompanionExtensions = processingEnv.options[OPTION_GENERATE_COMPANION_EXTENSIONS]?.toBoolean() ?: false
        this.filer = processingEnv.filer
        this.types = processingEnv.typeUtils
        this.elements = processingEnv.elementUtils
        this.messager = processingEnv.messager

        this.processor = InjectProcessor(this, generateCompanionExtensions)
    }

    override fun process(elements: Set<TypeElement>, env: RoundEnvironment): Boolean {
        for (element in env.getElementsAnnotatedWith(Module::class.java)) {
            if (element !is TypeElement) continue
            val astClass = element.toAstClass()

            val scope = element.scopeType()
            val scopedInjects: List<AstClass> =
                if (scope != null) env.getElementsAnnotatedWith(scope).mapNotNull {
                    // skip module itself, we only want @Inject's annotated with the scope
                    if (it.getAnnotation(Module::class.java) != null) {
                        null
                    } else {
                        (it as? TypeElement)?.toAstClass()
                    }
                } else emptyList()
            try {
                val file = processor.process(astClass, scopedInjects)
                val out = File(generatedSourcesRoot).also { it.mkdir() }
                file.writeTo(out)
            } catch (e: FailedToGenerateException) {
                // Continue so we can see all errors
                continue
            }
        }
        return false
    }

    override fun getSupportedAnnotationTypes(): Set<String> = setOf(
        Module::class.java.canonicalName,
        Inject::class.java.canonicalName
    )

    override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latestSupported()

    override fun getSupportedOptions(): Set<String> = setOf(OPTION_GENERATE_COMPANION_EXTENSIONS)
}
