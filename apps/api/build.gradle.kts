apply(plugin = "org.springframework.boot")

dependencies {
    implementation(project(":apps:domain"))
    implementation(project(":apps:application"))
    implementation(project(":storage:rdb"))
    implementation(project(":storage:redis"))
    implementation(project(":external:pg"))

    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.jackson.module.kotlin)

    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.starter.actuator.test)
    testImplementation(libs.spring.boot.starter.data.jpa.test)
    testImplementation(libs.testcontainers)
}

tasks.withType<Test>().configureEach {
    if (System.getenv("DOCKER_API_VERSION").isNullOrBlank()) {
        environment("DOCKER_API_VERSION", "1.40")
    }
    if (System.getProperty("api.version").isNullOrBlank()) {
        systemProperty("api.version", "1.40")
    }

    val orbstackSocket = file("${System.getProperty("user.home")}/.orbstack/run/docker.sock")
    if (System.getenv("DOCKER_HOST").isNullOrBlank() && orbstackSocket.exists()) {
        environment("DOCKER_HOST", "unix://$orbstackSocket")
        environment(
            "TESTCONTAINERS_DOCKER_CLIENT_STRATEGY",
            "org.testcontainers.dockerclient.EnvironmentAndSystemPropertyClientProviderStrategy",
        )
        systemProperty(
            "docker.client.strategy",
            "org.testcontainers.dockerclient.EnvironmentAndSystemPropertyClientProviderStrategy",
        )
        systemProperty("docker.host", "unix://$orbstackSocket")
    }
}

tasks.getByName("bootJar") {
    enabled = true
}

tasks.getByName("jar") {
    enabled = false
}
