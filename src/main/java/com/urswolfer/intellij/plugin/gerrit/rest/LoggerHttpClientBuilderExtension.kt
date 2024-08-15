/*
 * Copyright 2017 Urs Wolfer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.urswolfer.intellij.plugin.gerrit.rest

import com.google.inject.Inject
import com.intellij.openapi.diagnostic.Logger
import com.urswolfer.gerrit.client.rest.GerritAuthData
import com.urswolfer.gerrit.client.rest.http.HttpClientBuilderExtension
import org.apache.http.HttpRequest
import org.apache.http.HttpRequestInterceptor
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.protocol.HttpContext

class LoggerHttpClientBuilderExtension @Inject constructor(
    private val log: Logger
) : HttpClientBuilderExtension() {
    override fun extend(httpClientBuilder: HttpClientBuilder, authData: GerritAuthData): HttpClientBuilder {
        httpClientBuilder.addInterceptorFirst { httpRequest: HttpRequest, _: HttpContext? ->
            if (log.isDebugEnabled) {
                log.debug(httpRequest.toString())
            }
        }
        return httpClientBuilder
    }
}
