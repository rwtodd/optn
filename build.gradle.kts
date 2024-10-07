plugins {
    java
    application
}

version = "1.0.0"
group = "org.rwtodd"

sourceSets {
    main {
        java { setSrcDirs(listOf("src")) }
    }
}

tasks.withType<JavaCompile>().configureEach {
   options.release = 21
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.rwtodd:org.rwtodd.args:2.0.1")
}

application {
    // Define the main class for the application.
    mainModule = "optn"
    mainClass = "optn.App"
}
