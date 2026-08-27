package kr.co.ninetyseconds.recommendation.server.project

data class ProjectConfiguration(val projectCode: String, val configVersion: Int, val json: String)

fun interface ProjectConfigurationStore {
    fun findActiveByCode(projectCode: String): ProjectConfiguration?
}

class ProjectNotFoundException(val projectCode: String) : RuntimeException("Project not found: $projectCode")
