package kr.co.ninetyseconds.recommendation.server.project

import org.springframework.stereotype.Service

@Service
class GetProjectConfiguration(private val store: ProjectConfigurationStore) {
    operator fun invoke(projectCode: String): ProjectConfiguration =
        store.findActiveByCode(projectCode) ?: throw ProjectNotFoundException(projectCode)
}
