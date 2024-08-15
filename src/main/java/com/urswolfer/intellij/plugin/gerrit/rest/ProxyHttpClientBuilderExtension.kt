/*
 * Copyright 2013-2014 Urs Wolfer
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

import com.intellij.util.net.HttpConfigurable
import com.intellij.util.net.IdeaWideProxySelector
import com.urswolfer.gerrit.client.rest.GerritAuthData
import com.urswolfer.gerrit.client.rest.http.HttpClientBuilderExtension
import org.apache.http.HttpHost
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.CredentialsProvider
import org.apache.http.impl.client.HttpClientBuilder
import java.net.InetSocketAddress
import java.net.URI

/**
 * @author Urs Wolfer
 */
class ProxyHttpClientBuilderExtension : HttpClientBuilderExtension() {
    override fun extendCredentialProvider(
        httpClientBuilder: HttpClientBuilder,
        credentialsProvider: CredentialsProvider,
        authData: GerritAuthData
    ): CredentialsProvider {
        val proxySettings = HttpConfigurable.getInstance()
        val ideaWideProxySelector = IdeaWideProxySelector(proxySettings)

        // This will always return at least one proxy, which can be the "NO_PROXY" instance.
        val proxies = ideaWideProxySelector.select(URI.create(authData.host))

        // Find the first real proxy with an address type we support.
        for (proxy in proxies) {
            val socketAddress = proxy.address()

            if (HttpConfigurable.isRealProxy(proxy) && socketAddress is InetSocketAddress) {
                val proxyHttpHost = HttpHost(socketAddress.hostName, socketAddress.port)
                httpClientBuilder.setProxy(proxyHttpHost)

                // Here we use the single username/password that we got from IDEA's settings. It feels kinda strange
                // to use these credential but it's probably what the user expects.
                if (proxySettings.PROXY_AUTHENTICATION && proxySettings.proxyLogin != null) {
                    val authScope = AuthScope(proxySettings.PROXY_HOST, proxySettings.PROXY_PORT)
                    val credentials =
                        UsernamePasswordCredentials(proxySettings.proxyLogin, proxySettings.plainProxyPassword)
                    credentialsProvider.setCredentials(authScope, credentials)
                    break
                }
            }
        }
        return credentialsProvider
    }
}
