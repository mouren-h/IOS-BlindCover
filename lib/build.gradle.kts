plugins {
    id("com.android.library")
    id("maven-publish") 
}

android {
    namespace = "mr.x.iosx"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}


afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                groupId = "com.github.youhuaixu37.boops" 
                artifactId = "IOS-BlindCover"                 
                version = "1.1.0"
                from(components["release"])
            }
        }
    }
}