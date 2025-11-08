pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://api.mapbox.com/downloads/v2/releases/maven") }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        // 👇 Add this line for Mapbox
        maven {
            url = uri("https://api.mapbox.com/downloads/v2/releases/maven")
            credentials {
                username = "mapbox"
                // 👇 Replace YOUR_MAPBOX_DOWNLOAD_TOKEN with your token from your Mapbox account
                password = "YOUR_MAPBOX_DOWNLOAD_TOKEN"
            }
        }
    }
}
rootProject.name = "OnTheWay"
include(":app")
