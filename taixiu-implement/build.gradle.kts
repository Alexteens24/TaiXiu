plugins {
    jacoco
}

dependencies {
    api(project(":taixiu-api"))
    compileOnly(libs.paper.api)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    classDirectories.setFrom(sourceSets.main.get().output.asFileTree.matching {
        include("com/cortezromeo/taixiu/domain/**")
    })
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    classDirectories.setFrom(tasks.jacocoTestReport.get().classDirectories)
    violationRules { rule { limit { minimum = "0.80".toBigDecimal() } } }
}

tasks.check { dependsOn(tasks.jacocoTestCoverageVerification) }
