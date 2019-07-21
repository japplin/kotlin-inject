package me.tatarka.inject.sample

import assertk.assertThat
import assertk.assertions.isNotNull
import assertk.assertions.isSameAs
import assertk.assertions.isTrue
import me.tatarka.inject.annotations.Inject
import me.tatarka.inject.annotations.Module
import org.junit.Test

class ProvidesFoo(val bar: ProvidesBar? = null)
@Inject class ProvidesBar

@Module abstract class ProvidesFunctionModule {
    var providesCalled = false

    abstract val foo: ProvidesFoo

    fun foo() = ProvidesFoo().also { providesCalled = true }
}

@Module abstract class ProvidesFunctionArgModule {
    var providesCalled = false

    abstract val foo: ProvidesFoo

    fun foo(bar: ProvidesBar) = ProvidesFoo(bar).also { providesCalled = true }
}

@Module abstract class ProvidesValModule {
    var providesCalled = false

    abstract val foo: ProvidesFoo

    val provideFoo
        get() = ProvidesFoo().also { providesCalled = true }
}

@Module abstract class ProvidesExtensionFunctionModule {
    var providesCalled = false

    abstract val foo: ProvidesFoo

    fun ProvidesBar.provideFoo() = ProvidesFoo(this).also { providesCalled = true }
}

@Module abstract class ProvidesExtensionValModule {
    var providesCalled = false

    abstract val foo: ProvidesFoo

    val ProvidesBar.provideFoo
        get() = ProvidesFoo(this).also { providesCalled = true }
}

@Module abstract class ProvidesValConstructorModule(val provideFoo: ProvidesFoo) {
    abstract val foo: ProvidesFoo
}

class ProvidesTest {

    @Test fun generates_a_module_that_provides_a_dep_from_a_function() {
        val module = ProvidesFunctionModule::class.create()

        module.foo
        assertThat(module.providesCalled).isTrue()
    }

    @Test fun generates_a_module_that_provides_a_dep_from_a_function_with_arg() {
        val module = ProvidesFunctionArgModule::class.create()

        assertThat(module.foo.bar).isNotNull()
        assertThat(module.providesCalled).isTrue()
    }

    @Test fun generates_a_module_that_provides_a_dep_from_a_val() {
        val module = ProvidesValModule::class.create()

        module.foo
        assertThat(module.providesCalled).isTrue()
    }

    @Test fun generates_a_module_that_provides_a_deb_from_an_extension_function() {
        val module = ProvidesExtensionFunctionModule::class.create()

        assertThat(module.foo.bar).isNotNull()
        assertThat(module.providesCalled).isTrue()
    }

    @Test fun generates_a_module_that_provides_a_deb_from_an_extension_val() {
        val module = ProvidesExtensionValModule::class.create()

        assertThat(module.foo.bar).isNotNull()
        assertThat(module.providesCalled).isTrue()
    }

    @Test fun generates_a_module_that_provides_a_dep_from_a_constructor_val() {
        val foo = ProvidesFoo()
        val module = ProvidesValConstructorModule::class.create(foo)

        assertThat(module.foo).isSameAs(foo)
    }
}

