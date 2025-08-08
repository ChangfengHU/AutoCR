plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.intellij") version "1.13.3"
}

group = "com.vyibc"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Logging
    implementation("org.slf4j:slf4j-api:2.0.13")
    
    // Neo4j Driver for database connection testing
    implementation("org.neo4j.driver:neo4j-java-driver:5.14.0")
    
    // Apache TinkerPop for embedded graph database
    implementation("org.apache.tinkerpop:tinkergraph-gremlin:3.7.2")
    
    // Caffeine for high-performance caching
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
    
    // OkHttp for API calls
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // JSON processing
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")
    
    // Kotlin coroutines for async processing
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
    
    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.3")
}

// Configure Gradle IntelliJ Plugin
intellij {
    version.set("2024.2.5")
    type.set("IC") // IntelliJ IDEA Community Edition
    
    plugins.set(listOf("java", "Git4Idea"))
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }
    
    patchPluginXml {
        sinceBuild.set("242")
        untilBuild.set("243.*")
        
        changeNotes.set("""
            Initial version of AutoCR - AI-powered code review plugin
        """.trimIndent())
    }
    
    buildSearchableOptions {
        enabled = false
    }
}

tasks.test {
    useJUnitPlatform()
}