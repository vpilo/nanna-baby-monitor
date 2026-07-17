plugins {
    `kotlin-dsl`
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks
    .withType<Test>()
    .configureEach {
        useJUnitPlatform()
    }
