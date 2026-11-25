// By default dependencies and plugins come from the standard public repositories (Google, Maven Central, Gradle Plugin Portal).
//
// OPTIONAL: if your network requires an artifact mirror (Artifactory, Nexus, ...), set any of these Gradle properties
// (for example in ~/.gradle/gradle.properties, which is NOT part of the project) or the matching environment variables.
// Each one replaces the public repository it names; the ones you leave out keep using the public one.
//
//   mavenMirrorGoogle   / MAVEN_MIRROR_GOOGLE    full URL of a repository that mirrors https://maven.google.com
//   mavenMirrorCentral  / MAVEN_MIRROR_CENTRAL   full URL of a repository that mirrors Maven Central
//   mavenMirrorPlugins  / MAVEN_MIRROR_PLUGINS   full URL of a repository that mirrors https://plugins.gradle.org/m2
//   mavenMirrorToken    / MAVEN_MIRROR_TOKEN     optional access token, sent as "Authorization: Bearer <token>"
//
// Never put the token in a file that is committed.
pluginManagement {
    fun setting(property: String, env: String): String? =
        providers.gradleProperty(property).orElse(providers.environmentVariable(env)).orNull

    val token = setting("mavenMirrorToken", "MAVEN_MIRROR_TOKEN")

    repositories {
        fun mirrorOr(url: String?, public: () -> Unit) {
            if (url == null) public() else maven {
                name = url
                this.url = uri(url)
                if (token != null) {
                    credentials(HttpHeaderCredentials::class) { name = "Authorization"; value = "Bearer $token" }
                    authentication { create<HttpHeaderAuthentication>("header") }
                }
            }
        }
        mirrorOr(setting("mavenMirrorGoogle", "MAVEN_MIRROR_GOOGLE")) { google() }
        mirrorOr(setting("mavenMirrorPlugins", "MAVEN_MIRROR_PLUGINS")) { gradlePluginPortal() }
        mirrorOr(setting("mavenMirrorCentral", "MAVEN_MIRROR_CENTRAL")) { mavenCentral() }
    }
}

dependencyResolutionManagement {
    fun setting(property: String, env: String): String? =
        providers.gradleProperty(property).orElse(providers.environmentVariable(env)).orNull

    val token = setting("mavenMirrorToken", "MAVEN_MIRROR_TOKEN")

    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        fun mirrorOr(url: String?, public: () -> Unit) {
            if (url == null) public() else maven {
                name = url
                this.url = uri(url)
                if (token != null) {
                    credentials(HttpHeaderCredentials::class) { name = "Authorization"; value = "Bearer $token" }
                    authentication { create<HttpHeaderAuthentication>("header") }
                }
            }
        }
        mirrorOr(setting("mavenMirrorGoogle", "MAVEN_MIRROR_GOOGLE")) { google() }
        mirrorOr(setting("mavenMirrorCentral", "MAVEN_MIRROR_CENTRAL")) { mavenCentral() }
    }
}

rootProject.name = "StorageTrackerAndroid"
include(":app")
