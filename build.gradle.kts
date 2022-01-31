plugins {
  id("com.github.johnrengelman.shadow") version "7.1.2"
  id("net.kyori.indra") version "2.0.6"
}

indra {
  javaVersions().target(17)
}

repositories {
  mavenCentral()
  maven("https://papermc.io/repo/repository/maven-public/")
}

dependencies {
  implementation("org.ow2.asm:asm:9.2")
  implementation("org.ow2.asm:asm-tree:9.2")

  implementation("org.cadixdev:atlas:0.2.2")

  implementation("org.cadixdev:mercury:0.1.1-paperweight-SNAPSHOT")

  implementation("org.checkerframework:checker-qual:3.21.1")

  implementation(platform("org.apache.logging.log4j:log4j-bom:2.17.1"))
  implementation("org.apache.logging.log4j:log4j-api")
  implementation("org.apache.logging.log4j:log4j-core")
}

tasks {
  jar {
    manifest.attributes(
      "Main-Class" to "xyz.jpenilla.deprecator.Deprecator",
      "Multi-Release" to true,
    )
  }
  assemble {
    dependsOn(shadowJar)
  }
}
