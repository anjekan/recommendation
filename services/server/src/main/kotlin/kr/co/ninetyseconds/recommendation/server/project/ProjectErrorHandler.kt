package kr.co.ninetyseconds.recommendation.server.project

import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

data class ApiError(val code: String, val message: String, val requestId: String)

@RestControllerAdvice
class ProjectErrorHandler {
    @ExceptionHandler(ProjectNotFoundException::class)
    fun notFound(error: ProjectNotFoundException): ResponseEntity<ApiError> = ResponseEntity.status(HttpStatus.NOT_FOUND).body(
        ApiError("PROJECT_NOT_FOUND", error.message ?: "Project not found", UUID.randomUUID().toString()),
    )
}
