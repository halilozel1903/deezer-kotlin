package io.github.halilozel1903.deezer.transport

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders

/** The default transport: GET requests with Ktor. Closes the client only when it created it. */
internal class KtorTransport(
    httpClient: HttpClient?,
    engine: HttpClientEngine?,
    private val userAgent: String,
    requestTimeoutMillis: Long,
) : DeezerTransport, AutoCloseable {

    private val ownsClient = httpClient == null
    private val client: HttpClient = httpClient ?: run {
        val setup: HttpClientConfig<*>.() -> Unit = {
            expectSuccess = false
            install(HttpTimeout) {
                this.requestTimeoutMillis = requestTimeoutMillis
            }
        }
        if (engine != null) HttpClient(engine, setup) else HttpClient(CIO, setup)
    }

    override suspend fun get(url: String): DeezerHttpResponse {
        val response = client.get(url) {
            header(HttpHeaders.UserAgent, userAgent)
            accept(ContentType.Application.Json)
        }
        return DeezerHttpResponse(status = response.status.value, body = response.bodyAsText())
    }

    override fun close() {
        if (ownsClient) client.close()
    }
}
