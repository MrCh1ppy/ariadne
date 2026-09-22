plugins {
    java
    id("org.springframework.boot") version "4.0.2"
}

group = "com.ariadne"
version = "0.1.0"

repositories {
    maven(url = "https://maven.aliyun.com/repository/public")
    mavenCentral()
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.0.2"))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.1")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
    options.compilerArgs.add("-parameters")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
