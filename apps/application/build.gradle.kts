plugins {
    kotlin("plugin.spring")
}

dependencies {
    implementation(project(":apps:domain"))
    implementation("org.springframework.boot:spring-boot-starter")
}
