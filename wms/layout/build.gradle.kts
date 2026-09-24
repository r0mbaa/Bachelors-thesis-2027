plugins {
    id("wms.java-conventions")
}

dependencies {
    // LocationCode входит в публичный API модели (Cell), поэтому api, а не implementation.
    api(project(":shared"))

    testImplementation(libs.jqwik)
}
