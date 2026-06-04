plugins {
    `kotlin-dsl`
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(libs.junit)
    testRuntimeOnly(libs.junit.platform)
}

tasks
    .withType<Test>()
    .configureEach {
        useJUnitPlatform()
    }
