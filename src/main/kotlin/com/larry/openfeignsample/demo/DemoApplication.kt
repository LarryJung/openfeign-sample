package com.larry.openfeignsample.demo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import feign.Feign
import feign.FeignException.errorStatus
import feign.Param
import feign.RequestLine
import feign.Response
import feign.codec.ErrorDecoder
import feign.jackson.JacksonDecoder
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

data class Contributor(val login: String?, val contributions: Int)
class CustomException(status: Int, reason: String) : RuntimeException()


@SpringBootApplication
class DemoApplication

fun main(args: Array<String>) {
    runApplication<DemoApplication>(*args)
}

// configuration
interface GitHub {
    @RequestLine("GET /repos/{owner}/{repo}/contributors")
    fun contributors(@Param("owner") owner: String,
                     @Param("repo") repo: String): List<Contributor>
}

@Configuration
class Config {
    @Bean
    fun githubAdapter(): GitHub =
            Feign.builder()
                    .errorDecoder(StashErrorDecoder())
                    .decoder(JacksonDecoder(ObjectMapper()
                            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                            .registerModule(KotlinModule())))
                    .target(GitHub::class.java, "https://api.github.com")
}

class StashErrorDecoder : ErrorDecoder {
    override fun decode(methodKey: String?, response: Response): Exception =
            when (response.status()) {
                in 400..499 -> CustomException(response.status(), response.reason())
                in 500..599 -> CustomException(response.status(), response.reason())
                else -> errorStatus(methodKey, response)
            }
}

// usage
@RestController
class HelloController(val gitHubAdapter: GitHub) {
    @GetMapping("hello/{owner}/{repo}")
    fun greeting(@PathVariable owner: String,
                 @PathVariable repo: String): List<Contributor> {
        return gitHubAdapter.contributors(owner, repo);
    }
}
