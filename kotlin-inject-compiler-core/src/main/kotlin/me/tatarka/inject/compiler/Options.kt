package me.tatarka.inject.compiler

private const val OPTION_GENERATE_COMPANION_EXTENSIONS = "me.tatarka.inject.generateCompanionExtensions"
private const val OPTION_ENABLE_JAVAX_ANNOTATIONS = "me.tatarka.inject.enableJavaxAnnotations"

data class Options(
        val generateCompanionExtensions: Boolean,
        val enableJavaxAnnotations: Boolean
) {
    companion object {
        fun from(map: Map<String, String>) = Options(
                generateCompanionExtensions = map[OPTION_GENERATE_COMPANION_EXTENSIONS]?.toBoolean() ?: false,
                enableJavaxAnnotations = map[OPTION_ENABLE_JAVAX_ANNOTATIONS]?.toBoolean() ?: false
        )
    }
}