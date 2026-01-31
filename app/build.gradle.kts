plugins {
	id("chirp.spring-boot-app")
}

group = "com.project"
version = "0.0.1-SNAPSHOT"

dependencies {
	implementation(projects.user)
	implementation(projects.chat)
	implementation(projects.notification)
	implementation(projects.common)
}