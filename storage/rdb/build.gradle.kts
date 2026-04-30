allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

dependencies {
    implementation(project(":apps:domain"))

    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.flyway.core)
    implementation(libs.flyway.mysql)

    runtimeOnly(libs.h2)
    runtimeOnly(libs.mysql.connector.j)

    testImplementation(libs.spring.boot.starter.data.jpa.test)
    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.h2)
}
