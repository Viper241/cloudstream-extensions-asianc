import com.android.build.api.dsl.LibraryExtension
import com.lagradost.cloudstream3.gradle.CloudstreamExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

buildscript {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }

    dependencies {
        classpath("com.android.tools.build:gradle:9.1.1")
        // CloudStream's Gradle plugin that turns modules into installable .cs files.
        classpath("com.github.recloudstream:gradle:81b1d424d2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

fun Project.cloudstream(configuration: CloudstreamExtension.() -> Unit) =
    extensions.getByName<CloudstreamExtension>("cloudstream").configuration()

fun Project.android(configuration: LibraryExtension.() -> Unit) {
    extensions.getByName<LibraryExtension>("android").configuration()
}

subprojects {
    apply(plugin = "com.android.library")
    apply(plugin = "com.lagradost.cloudstream3.gradle")

    cloudstream {
        setRepo(
            System.getenv("GITHUB_REPOSITORY")
                ?: "https://github.com/Viper241/cloudstream-extensions-asianc"
        )
        authors = listOf("AsianC")
    }

    android {
        namespace = "com.asianc"
        compileSdk = 36

        defaultConfig {
            minSdk = 21
        }

        lint {
            targetSdk = 36
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }

        //noinspection WrongGradleMethod
        tasks.withType<KotlinJvmCompile> {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_11)
                freeCompilerArgs.addAll(
                    "-Xno-call-assertions",
                    "-Xno-param-assertions",
                    "-Xno-receiver-assertions",
                )
            }
        }
    }

    dependencies {
        val compileOnly by configurations
        val cloudstream by configurations

        // CloudStream API stubs, used at compile time.
        cloudstream("com.lagradost:cloudstream3:pre-release")

        // Libraries already shipped by the CloudStream app. compileOnly keeps
        // them out of the plugin and avoids duplicate-class problems.
        compileOnly(kotlin("stdlib"))
        compileOnly("com.github.Blatzar:NiceHttp:0.4.18")
        compileOnly("org.jsoup:jsoup:1.22.2")
        compileOnly("androidx.annotation:annotation:1.10.0")
        compileOnly("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")
        compileOnly("com.fasterxml.jackson.core:jackson-databind:2.13.1")
        compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
        compileOnly("com.google.code.gson:gson:2.14.0")
        compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
