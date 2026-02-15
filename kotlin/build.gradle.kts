plugins {
    kotlin("multiplatform") version "2.1.0"
}

repositories {
    mavenCentral()
}

kotlin {
    linuxX64 {
        binaries {
            executable {
                baseName = "apple1"
                entryPoint = "a1em.main"
            }
        }
    }

    sourceSets {
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

tasks.register("test") {
    dependsOn("linuxX64Test")
}
