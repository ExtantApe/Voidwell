plugins { java }

java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }

dependencies { compileOnly(rootProject.extra["paperApi"] as String) }

tasks.withType<JavaCompile>().configureEach { options.release.set(25) }

tasks.jar { archiveBaseName.set("WaystationStandin") }
