dependencies {
    implementation(project(":apps:domain"))
    implementation(libs.spring.boot.starter)
    implementation(libs.spring.boot.starter.aspectj)
    implementation(libs.redisson)
    implementation(libs.resilience4j.spring.boot4)
    annotationProcessor(libs.spring.boot.configuration.processor)
}
