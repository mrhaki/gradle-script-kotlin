package org.gradle.script.lang.kotlin

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.anyMap
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever

import org.gradle.api.Project
import org.gradle.api.artifacts.ClientModule
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.dsl.DependencyHandler

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.sameInstance
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test

class DependencyHandlerExtensionsTest {

    @Test
    fun `given group, name, version, configuration, classifier and ext, 'create' extension will build corresponding map`() {

        val expectedModuleMap = mapOf(
            "group" to "g",
            "name" to "n",
            "version" to "v",
            "configuration" to "cfg",
            "classifier" to "cls",
            "ext" to "x")

        val dependencies: DependencyHandler = mock()
        val dependency: ExternalModuleDependency = mock()
        whenever(dependencies.create(expectedModuleMap)).thenReturn(dependency)

        assertThat(
            dependencies.create(
                group = "g",
                name = "n",
                version = "v",
                configuration = "cfg",
                classifier = "cls",
                ext = "x"),
            sameInstance(dependency))
    }

    @Test
    fun `given group and module, 'exclude' extension will build corresponding map`() {

        val dependencies = KotlinDependencyHandler(mock())
        val dependency: ExternalModuleDependency = mock()
        val events = mutableListOf<String>()
        whenever(dependencies.create("dependency")).then {
            events.add("created")
            dependency
        }
        whenever(dependency.exclude(mapOf("group" to "g", "module" to "m"))).then {
            events.add("configured")
            dependency
        }
        whenever(dependencies.add("configuration", dependency)).then {
            events.add("added")
            dependency
        }

        dependencies {

            "configuration"("dependency") {
                val configuredDependency =
                    exclude(group = "g", module = "m")
                assertThat(
                    configuredDependency,
                    sameInstance(dependency))
            }
        }

        assertThat(
            events,
            equalTo(listOf("created", "configured", "added")))
    }

    @Test
    fun `given path and configuration, 'project' extension will build corresponding map`() {

        val dependencies = KotlinDependencyHandler(mock())
        val dependency: ProjectDependency = mock()
        val events = mutableListOf<String>()
        val expectedProjectMap = mapOf("path" to ":project", "configuration" to "default")
        whenever(dependencies.project(expectedProjectMap)).then {
            events.add("created")
            dependency
        }
        whenever(dependencies.add("configuration", dependency)).then {
            events.add("added")
            dependency
        }
        val project: Project = mock()
        whenever(dependency.dependencyProject).thenReturn(project)

        dependencies {

            "configuration"(project(path = ":project", configuration = "default")) {
                events.add("configured")
                assertThat(
                    dependencyProject,
                    sameInstance(project))
            }
        }

        assertThat(
            events,
            equalTo(listOf("created", "configured", "added")))
    }

    @Test
    fun `given extensions for common configurations, they will delegate to the appropriate methods`() {

        val externalModuleDependency = mock<ExternalModuleDependency>() {
            on { exclude(anyMap()) }.then { it.mock }
        }
        val projectDependency = mock<ProjectDependency>() {
            on { exclude(anyMap()) }.then { it.mock }
        }
        val dependencyHandler = mock<DependencyHandler>() {
            on { create(anyMap<String, String>()) } doReturn externalModuleDependency
            on { create("org.gradle:baz:1.0-SNAPSHOT") } doReturn externalModuleDependency
            on { project(any()) } doReturn projectDependency
            on { add(any(), any()) }.then { it.getArgument(1) }
        }

        val dependencies = KotlinDependencyHandler(dependencyHandler)
        dependencies {

            default(group = "org.gradle", name = "foo", version = "1.0") {
                isForce = true
            }

            compile(group = "org.gradle", name = "bar") {
                exclude(module = "foo")
            }

            runtime("org.gradle:baz:1.0-SNAPSHOT") {
                isChanging = true
                isTransitive = false
            }

            testCompile(group = "junit", name = "junit")

            testRuntime(project(":core")) {
                exclude(group = "org.gradle")
            }
        }
    }

    @Test
    fun `given configuration name and dependency notation, it will add the dependency`() {

        val dependencies = KotlinDependencyHandler(mock())
        whenever(dependencies.add(any(), any())).thenReturn(mock())

        dependencies {
            "configuration"("notation")
        }

        verify(dependencies.dependencies).add("configuration", "notation")
    }

    @Test
    fun `given configuration and dependency notation, it will add the dependency to the named configuration`() {

        val dependencies = KotlinDependencyHandler(mock())
        whenever(dependencies.add(any(), any())).thenReturn(mock())

        val configuration: Configuration = mock()
        whenever(configuration.name).thenReturn("c")

        dependencies {
            configuration("notation")
        }

        verify(dependencies.dependencies).add("c", "notation")
    }

    @Test
    fun `client module configuration`() {

        val clientModule = mock<ClientModule>()

        val commonsCliDependency = mock<ExternalModuleDependency>("commonsCliDependency")

        val antModule = mock<ClientModule>("antModule")
        val antLauncherDependency = mock<ExternalModuleDependency>("antLauncherDependency")
        val antJUnitDependency = mock<ExternalModuleDependency>("antJUnitDependency")

        val dependencies = mock<DependencyHandler>() {
            on { module("org.codehaus.groovy:groovy:2.4.7") } doReturn clientModule

            on { create("commons-cli:commons-cli:1.0") } doReturn commonsCliDependency

            val antModuleNotation = mapOf("group" to "org.apache.ant", "name" to "ant", "version" to "1.9.6")
            on { module(antModuleNotation) } doReturn antModule
            on { create("org.apache.ant:ant-launcher:1.9.6@jar") } doReturn antLauncherDependency
            on { create("org.apache.ant:ant-junit:1.9.6") } doReturn antJUnitDependency

            on { add("runtime", clientModule) } doReturn clientModule
        }

        dependencies.apply {
            val groovy = module("org.codehaus.groovy:groovy:2.4.7") {

                // Configures the module itself
                isTransitive = false

                dependency("commons-cli:commons-cli:1.0") {
                    // Configures the external module dependency
                    isTransitive = false
                }

                module(group = "org.apache.ant", name = "ant", version = "1.9.6") {
                    // Configures the inner module dependencies
                    dependencies(
                        "org.apache.ant:ant-launcher:1.9.6@jar",
                        "org.apache.ant:ant-junit:1.9.6")
                }
            }
            runtime(groovy)
        }

        verify(clientModule).isTransitive = false
        verify(clientModule).addDependency(commonsCliDependency)
        verify(clientModule).addDependency(antModule)

        verify(commonsCliDependency).isTransitive = false

        verify(antModule).addDependency(antLauncherDependency)
        verify(antModule).addDependency(antJUnitDependency)
    }
}
