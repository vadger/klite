val mainClassName = "LauncherKt"

dependencies {
  implementation(project(":server"))
  implementation(project(":json"))
  implementation(project(":jdbc"))
  implementation(project(":jdbc-multitenant"))
  implementation(project(":slf4j"))
  implementation(libs.postgresql)
  testImplementation(project(":jdbc-test"))
}

sourceSets {
  main {
    resources.srcDirs("db")
  }
}

tasks.register<Copy>("deps") {
  into("$buildDir/libs/deps")
  from(configurations.runtimeClasspath)
}

tasks.jar {
  dependsOn("deps")
  doFirst {
    manifest {
      attributes(
        "Main-Class" to mainClassName,
        "Class-Path" to File("$buildDir/libs/deps").listFiles()!!.joinToString(" ") { "deps/${it.name}" }
      )
    }
  }
}

tasks.register<JavaExec>("run") {
  mainClass.set(mainClassName)
  classpath = sourceSets.main.get().runtimeClasspath
}
