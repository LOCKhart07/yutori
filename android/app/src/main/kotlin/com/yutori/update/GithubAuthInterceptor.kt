package com.yutori.update

import okhttp3.Interceptor
import okhttp3.Response

// Removal target for #71(a). Empty token is a first-class state:
// the interceptor becomes a no-op, which is exactly what the public-repo
// build wants. Token is injected so unit tests don't need BuildConfig.
// See plans/autoupdater-spec.md §5 and docs/RELEASING.md "Going public".
class GithubAuthInterceptor(private val token: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val request = if (token.isEmpty()) {
            original
        } else {
            original.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        }
        return chain.proceed(request)
    }
}
