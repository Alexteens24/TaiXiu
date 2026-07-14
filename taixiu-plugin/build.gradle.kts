import org.apache.tools.ant.filters.ReplaceTokens

plugins {
    alias(libs.plugins.shadow)
    jacoco
}

dependencies {
    implementation(project(":taixiu-api"))
    implementation(project(":taixiu-implement"))

    compileOnly(libs.paper.api)
    compileOnly(libs.vault.unlocked.api)
    compileOnly(libs.placeholder.api)
    compileOnly(libs.floodgate.api)
    compileOnly(libs.player.points)

    implementation(libs.config.updater)
    implementation(libs.json)
    implementation(libs.sqlite.jdbc)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.paper.api)
    testRuntimeOnly(libs.junit.platform.launcher)
}

val pluginVersion = version.toString()

tasks.processResources {
    inputs.property("version", pluginVersion)
    filter<ReplaceTokens>("tokens" to mapOf("version" to pluginVersion))
}

tasks.shadowJar {
    archiveBaseName = "TaiXiu"
    archiveClassifier = ""
    duplicatesStrategy = DuplicatesStrategy.INCLUDE

    mergeServiceFiles()
    relocate(
        "com.tchristofferson.configupdater",
        "com.cortezromeo.taixiu.lib.configupdater"
    )
    relocate("org.json", "com.cortezromeo.taixiu.lib.json")
}

tasks.jar {
    enabled = false
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    classDirectories.setFrom(sourceSets.main.get().output.asFileTree.matching {
        include("com/cortezromeo/taixiu/storage/**")
    })
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    classDirectories.setFrom(tasks.jacocoTestReport.get().classDirectories)
    violationRules { rule { limit { minimum = "0.20".toBigDecimal() } } }
}

tasks.check { dependsOn(tasks.jacocoTestCoverageVerification) }
