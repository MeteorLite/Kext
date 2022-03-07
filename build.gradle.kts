
plugins {
    kotlin("jvm") version "1.6.10"
    java
    `maven-publish`

}

repositories {
    mavenLocal()
    maven {
        url = uri("https://repo.maven.apache.org/maven2/")
    }
}

dependencies {
    implementation("org.slf4j:slf4j-api:1.7.30")
    implementation("javax.annotation:javax.annotation-api:1.3.2")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.6.10")
    implementation("org.slf4j:slf4j-api:1.7.30")
    testImplementation("junit:junit:4.13.1")
    testImplementation("org.hamcrest:hamcrest:2.2")
    testImplementation("org.apache.logging.log4j:log4j-slf4j-impl:2.13.0")
    testImplementation("org.assertj:assertj-core:3.16.1")
    testImplementation("com.google.testing.compile:compile-testing:0.19")
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.6.10")
    testImplementation("junit:junit:4.13.1")
    testImplementation("org.hamcrest:hamcrest:2.2")
    testImplementation("org.apache.logging.log4j:log4j-slf4j-impl:2.13.0")
    testImplementation("org.assertj:assertj-core:3.16.1")
}

group = "kext"
version = "1.0.0"
description = "kExt"
java.sourceCompatibility = JavaVersion.VERSION_1_8

publishing {
    publications.create<MavenPublication>("maven") {
        from(components["java"])
    }
}

tasks.withType<JavaCompile>() {
    options.encoding = "UTF-8"
}
