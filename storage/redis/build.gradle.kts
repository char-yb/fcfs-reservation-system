dependencies {
    implementation(project(":apps:domain"))
    implementation(libs.spring.boot.starter)
    implementation(libs.redisson)
    annotationProcessor(libs.spring.boot.configuration.processor)
}
