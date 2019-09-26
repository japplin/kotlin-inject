package me.tatarka.inject.gradle

import org.gradle.api.Project
import org.gradle.api.tasks.compile.AbstractCompile
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonOptions
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinGradleSubplugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

class InjectKotlinSubplugin : KotlinGradleSubplugin<AbstractCompile> {
    override fun apply(project: Project, kotlinCompile: AbstractCompile, javaCompile: AbstractCompile?, variantData: Any?, androidProjectHandler: Any?, kotlinCompilation: KotlinCompilation<KotlinCommonOptions>?): List<SubpluginOption> {
        return emptyList()
    }

    override fun getCompilerPluginId(): String = "KotlinInject"

    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
            groupId = "me.tatarka.inject",
            artifactId = "kotlin-inject-compiler",
            version = "0.0.1-SNAPSHOT"
    )

    override fun isApplicable(project: Project, task: AbstractCompile): Boolean = project.plugins.hasPlugin(InjectPlugin::class.java)
}
