package kr.co.ninetyseconds.recommendation.server.project

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/projects")
class ProjectConfigurationController(private val getProjectConfiguration: GetProjectConfiguration) {
    @GetMapping("/{projectCode}/config", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getConfig(
        @PathVariable projectCode: String,
        @RequestHeader(HttpHeaders.IF_NONE_MATCH, required = false) ifNoneMatch: String?,
    ): ResponseEntity<String> {
        val config = getProjectConfiguration(projectCode)
        val etag = "\"${config.projectCode}-${config.configVersion}\""
        if (ifNoneMatch == etag) return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build()
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).eTag(etag).body(config.json)
    }
}
