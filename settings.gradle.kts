pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google(); mavenCentral()
        // Bibliothèque officielle AILinkSecretAndroid (elinkthings) : poignée de main + chiffrement A7 des balances AiLink
        maven("https://jitpack.io") { content { includeGroup("com.github.elinkthings") } }
    }
}
rootProject.name = "Calories"
include(":app")
