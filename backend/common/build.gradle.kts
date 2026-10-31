plugins {
    `java-library`
}

dependencies {
    api(platform(libs.spring.boot.bom))
    api("org.springframework.boot:spring-boot-starter-web")
    api("org.springframework.boot:spring-boot-starter-validation")
    api("org.springframework.boot:spring-boot-starter-data-redis")
    api("org.springframework.boot:spring-boot-starter-actuator")
    api(libs.springdoc)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
