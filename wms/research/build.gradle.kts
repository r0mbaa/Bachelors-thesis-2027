plugins {
    id("wms.java-conventions")
}

// Стенд зависит только от библиотек: ни БД, ни веб-сервера в его classpath нет.
dependencies {
    implementation(project(":planner-engine"))

    testImplementation(libs.jqwik)
}
