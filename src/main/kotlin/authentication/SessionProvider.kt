package authentication

import OkHttpSingleton
import okhttp3.FormBody
import okhttp3.Headers
import org.jsoup.Jsoup
import utility.getCookie
import utility.getRedirect

internal data class LoginRequestInfo(val cookie: String, val reference: String)

private const val VERIFY_EMAIL_URL = "https://stars.bilkent.edu.tr/accounts/auth/verifyEmail"

internal suspend fun initializeLogin(id: String, password: String): LoginRequestInfo {
    val oauthMainResponse = OkHttpSingleton.fetch("https://stars.bilkent.edu.tr/srs/oauth-login.php")

    val cookie = getCookie(oauthMainResponse, "PHPSESSID")
    val headers = Headers.headersOf("Cookie", cookie)

    val oauthAuthResponse = OkHttpSingleton.fetch(getRedirect(oauthMainResponse), HttpMethods.GET, headers)
    val loginPageResponse = OkHttpSingleton.fetch(getRedirect(oauthAuthResponse), HttpMethods.GET, headers)

    val formCode: String = loginPageResponse.use {
        Jsoup.parse(it.body!!.string())
            .getElementById("LoginForm_password_em_")
            ?.parent()
            ?.selectFirst("input")
            ?.attr("name") ?: throw Exception("Couldn't find `formCode`")
    }

    OkHttpSingleton.fetch(
        loginPageResponse.request.url.toString(),
        HttpMethods.POST,
        headers,
        FormBody.Builder()
            .add("LoginForm[username]", id)
            .add("LoginForm[password]", password)
            .add(formCode, "")
            .add("yt0", "")
            .build()
    )

    OkHttpSingleton.fetch(VERIFY_EMAIL_URL, HttpMethods.GET, headers).use {
        val reference = Jsoup.parse(it.body!!.string())
            .selectFirst("#verifyEmail-form div.controls > p.help-block > span > strong")
            ?.text()
            ?: throw Exception("No reference code found on 2FA verification page, incorrect credentials likely")
        return LoginRequestInfo(cookie, reference)
    }
}

internal suspend fun verifyEmail(initialCookie: String, code: String): String {
    val codeResponse = OkHttpSingleton.fetch(
        VERIFY_EMAIL_URL,
        HttpMethods.POST,
        Headers.headersOf("Cookie", initialCookie),
        FormBody.Builder().add("EmailVerifyForm[verifyCode]", code).add("yt0", "").build()
    )

    val newCookie = try {
        getCookie(codeResponse, "PHPSESSID")
    } catch (e: Exception) {
        throw Exception("No PHPSESSID cookie, incorrect verification code likely")
    }

    val headers = Headers.headersOf("Cookie", newCookie, "Referer", VERIFY_EMAIL_URL)

    val oauthAuthResponse = OkHttpSingleton.fetch(getRedirect(codeResponse), HttpMethods.GET, headers)

    val authorizationHeaders = headers.newBuilder()
        .set("Cookie", headers["Cookie"]!! + "; ${getCookie(oauthAuthResponse, "authorize")}")
        .build()

    val oauthMainResponse = OkHttpSingleton.fetch(getRedirect(oauthAuthResponse), HttpMethods.GET, authorizationHeaders)
    val srsResponse = OkHttpSingleton.fetch(
        "https://stars.bilkent.edu.tr${getRedirect(oauthMainResponse)}", HttpMethods.GET, authorizationHeaders
    )
    OkHttpSingleton.fetch(getRedirect(srsResponse), HttpMethods.GET, authorizationHeaders)

    return newCookie
}
