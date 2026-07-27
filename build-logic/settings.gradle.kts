rootProject.name = "build-logic"

apply(from = "../gradle/repositories.gradle.kts")

plugins {
    id("dev.panuszewski.typesafe-conventions") version "0.11.1"
}
