plugins {
    id("wms.java-conventions")
}

dependencies {
    api(project(":layout"))

    testImplementation(libs.jqwik)
}
