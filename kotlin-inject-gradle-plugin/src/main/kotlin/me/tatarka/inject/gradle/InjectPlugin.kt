package me.tatarka.inject.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

class InjectPlugin : Plugin<Project> {
    override fun apply(p0: Project) {
        // Real magic happens in InjectKotlinSubplugin
    }
}