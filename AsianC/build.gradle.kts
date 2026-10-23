version = 2

cloudstream {
    authors = listOf("AsianC")
    language = "en"
    description =
        "Asian dramas, movies and K-shows from AsianC (Dramacool) with English subtitles."
    status = 1
    tvTypes = listOf("AsianDrama", "Movie", "TvSeries")
    iconUrl = "https://asianc.id/template/images/icon_192x192.png"
}

android {
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("org.jsoup:jsoup:1.22.2")
    testImplementation("com.github.Blatzar:NiceHttp:0.4.18")
    testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.13.1")
    testImplementation("com.google.code.gson:gson:2.14.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0")
    testImplementation("dev.whyoleg.cryptography:cryptography-core:0.6.0")
    testImplementation("dev.whyoleg.cryptography:cryptography-provider-optimal:0.6.0")
    testImplementation(files("${gradle.gradleUserHomeDir}/caches/cloudstream/cloudstream/cloudstream.jar"))
}

tasks.withType<Test>().configureEach {
    testLogging {
        showStandardStreams = true
        events("passed", "failed")
    }
}
