dependencies {
  api(project(":jdbc"))
  compileOnly(project(":server"))
  testImplementation(project(":server"))
  testImplementation(libs.mockk)
}
