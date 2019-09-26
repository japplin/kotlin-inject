package me.tatarka.inject.compiler

import org.jetbrains.kotlin.com.intellij.mock.MockProject
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.container.StorageComponentContainer
import org.jetbrains.kotlin.container.useInstance
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.extensions.StorageComponentContainerContributor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.resolve.TargetPlatform
import org.jetbrains.kotlin.resolve.checkers.DeclarationChecker
import org.jetbrains.kotlin.resolve.checkers.DeclarationCheckerContext

class InjectComponentRegistrar: ComponentRegistrar {
    override fun registerProjectComponents(project: MockProject, configuration: CompilerConfiguration) {
        StorageComponentContainerContributor.registerExtension(project, object : StorageComponentContainerContributor {
            override fun registerModuleComponents(container: StorageComponentContainer, platform: TargetPlatform, moduleDescriptor: ModuleDescriptor) {
                container.useInstance(InjectDeclarationChecker())
            }
        })
    }

    class InjectDeclarationChecker : DeclarationChecker {
        override fun check(declaration: KtDeclaration, descriptor: DeclarationDescriptor, context: DeclarationCheckerContext) {
            when (descriptor) {
                is ClassDescriptor -> {
                    if (descriptor.annotations.hasAnnotation(FqName(Module::class.java.name))) {
                    }
                }
            }
        }
    }
}