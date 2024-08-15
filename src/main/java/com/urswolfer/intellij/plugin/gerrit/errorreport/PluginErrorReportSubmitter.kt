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
package com.urswolfer.intellij.plugin.gerrit.errorreport

import com.google.common.base.Strings
import com.google.gson.Gson
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.diagnostic.ErrorReportSubmitter
import com.intellij.openapi.diagnostic.IdeaLoggingEvent
import com.intellij.openapi.diagnostic.SubmittedReportInfo
import com.intellij.openapi.ui.Messages
import com.intellij.util.Consumer
import com.urswolfer.intellij.plugin.gerrit.Version
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClients
import java.awt.Component
import java.io.IOException

/**
 * @author Urs Wolfer
 */
class PluginErrorReportSubmitter : ErrorReportSubmitter() {
    override fun getReportActionText(): String {
        return "Report to Plugin Developer (Please include your email address)"
    }

    override fun submit(
        events: Array<IdeaLoggingEvent>,
        additionalInfo: String?,
        parentComponent: Component,
        consumer: Consumer<in SubmittedReportInfo?>
    ): Boolean {
        var additionalInfo = additionalInfo
        if (Strings.isNullOrEmpty(additionalInfo) || !additionalInfo!!.contains("@")) {
            val emailAddress = Messages.showInputDialog(
                """
                 It seems you have not included your email address.
                 If you enter it below, you will get most probably a message with a solution for your issue or a question which will help to solve it.
                 """.trimIndent(), "Information Required", null
            )
            if (!emailAddress.isNullOrEmpty()) {
                additionalInfo =
                    if (additionalInfo == null) emailAddress
                    else
                        """
                         $additionalInfo
                         $emailAddress
                         """.trimIndent()
            }
        }
        val errorBean = createErrorBean(events[0], additionalInfo)
        val json = Gson().toJson(errorBean)
        postError(json)
        return true
    }

    private fun createErrorBean(loggingEvent: IdeaLoggingEvent, additionalInfo: String?): ErrorBean {
        val errorBean = ErrorBean()
        errorBean.additionInfo = additionalInfo
        errorBean.pluginVersion = Version.get()
        val appInfo = ApplicationInfoEx.getInstanceEx()
        val intellijVersion = String.format(
            "%s %s.%s %s",
            appInfo.versionName, appInfo.majorVersion, appInfo.minorVersion, appInfo.apiVersion
        )
        errorBean.intellijVersion = intellijVersion
        errorBean.os = String.format(
            "%s %s",
            System.getProperty("os.name"),
            System.getProperty("os.version")
        )
        errorBean.java = String.format(
            "%s %s",
            System.getProperty("java.vendor"),
            System.getProperty("java.version")
        )
        errorBean.exception = loggingEvent.throwableText
        errorBean.exceptionMessage = loggingEvent.message
        return errorBean
    }

    private fun postError(json: String) {
        try {
            HttpClients.createDefault().use { httpClient ->
                val httpPost = HttpPost(ERROR_REPORT_URL)
                httpPost.entity = StringEntity(json, ContentType.APPLICATION_JSON)
                val response = httpClient.execute(httpPost)
                if (response.statusLine.statusCode == 406) {
                    val reasonPhrase = response.statusLine.reasonPhrase
                    Messages.showErrorDialog(reasonPhrase, "Gerrit Plugin Message")
                }
            }
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    companion object {
        private const val ERROR_REPORT_URL = "https://urswolfer.com/gerrit-intellij-plugin/service/error-report/"
    }
}
