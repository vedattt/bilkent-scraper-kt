package authentication

import OkHttpSingleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.Headers
import org.jsoup.Jsoup
import utility.getCookie
import java.util.regex.Pattern

private data class WebmailLoginPrerequisites(val token: String, val sessionId: String)
data class VerificationCode(val code: String, val ref: String)

private suspend fun getWebmailLoginPrerequisites(): WebmailLoginPrerequisites {
    val pageResponse = OkHttpSingleton.fetch("https://webmail.bilkent.edu.tr")
    pageResponse.use {
        val document = Jsoup.parse(it.body!!.string())
        val token = document.selectFirst("input[name=_token]")?.attr("value") ?: throw Exception("No token found")
        return WebmailLoginPrerequisites(token, getCookie(it, "roundcube_sessid"))
    }
}

private suspend fun getWebmailSession(email: String, password: String): String {
    val prereqs = getWebmailLoginPrerequisites()

    val formBody = FormBody.Builder()
        .add("_token", prereqs.token)
        .add("_task", "login")
        .add("_action", "login")
        .add("_timezone", "Europe/Istanbul")
        .add("_url", "")
        .add("_user", email)
        .add("_pass", password)
        .build()

    OkHttpSingleton.fetch(
        "https://webmail.bilkent.edu.tr/?_task=login",
        HttpMethods.POST,
        Headers.headersOf("Cookie", prereqs.sessionId),
        formBody
    ).use { response ->
        val cookies =
            response.headers.filter { it.first == "Set-Cookie" }.drop(1).map { it.second.substringBefore(';') }
        return cookies.joinToString("; ")
    }
}

private suspend fun getLatestUid(session: String, boxName: String): String {
    OkHttpSingleton.fetch(
        "https://webmail.bilkent.edu.tr/?_task=mail&_action=list&_refresh=1&_mbox=$boxName&_remote=1&_unlock=&_=",
        HttpMethods.GET,
        Headers.headersOf("Cookie", session)
    ).use {
        return Json.parseToJsonElement(it.body!!.string()).jsonObject["env"]!!.jsonObject["messagecount"]!!.jsonPrimitive.content
    }

}

private suspend fun getVerificationMail(session: String, boxName: String, uid: String): VerificationCode {
    OkHttpSingleton.fetch(
        "https://webmail.bilkent.edu.tr/?_task=mail&_mbox=$boxName&_uid=$uid&_action=show",
        HttpMethods.GET,
        Headers.headersOf("Cookie", session)
    ).use {
        val mailContent = Jsoup.parse(it.body!!.string()).getElementById("messagebody")!!.text()

        val pattern = Pattern.compile(": (\\d{5}) .* coded ([A-Z]{4})\\.")
        val matcher = pattern.matcher(mailContent)
        if (!matcher.find()) throw Exception("No verification code matched")
        return VerificationCode(matcher.group(1), matcher.group(2))
    }
}

suspend fun getVerificationCode(email: String, password: String, boxName: String = "STARS Auth"): VerificationCode {
    val session = getWebmailSession(email, password)
    val uid = getLatestUid(session, boxName)
    return getVerificationMail(session, boxName, uid)
}
