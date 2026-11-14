pluginManagement {
    repositories {
        google()
        // Maven Central'ın Google tarafından barındırılan aynası (hız sınırına karşı önce bu denenir)
        maven("https://maven-central.storage-download.googleapis.com/maven2/") { name = "MavenCentralGcsMirror" }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // Maven Central'ın Google tarafından barındırılan aynası (hız sınırına karşı önce bu denenir)
        maven("https://maven-central.storage-download.googleapis.com/maven2/") { name = "MavenCentralGcsMirror" }
        mavenCentral()
    }
}
rootProject.name = "Karala"
include(":app")
