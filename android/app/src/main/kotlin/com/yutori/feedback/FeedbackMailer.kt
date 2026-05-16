package com.yutori.feedback

import android.content.Intent
import android.net.Uri
import java.net.URLEncoder

/**
 * Builds the e-mail the Settings → Send feedback sheet (#113) hands
 * off to the user's mail client.
 *
 * No GitHub API, no embedded PAT, no network — an [Intent.ACTION_SENDTO]
 * `mailto:` intent. The user reviews (and can redact) the draft in
 * their own mail app before sending, so the auto-appended
 * [FeedbackContext] trailer is always inspectable first. This is the
 * post-#71(a) feedback path: it behaves identically whether the repo
 * is private or public, which is why its rollout was decoupled from
 * the public flip.
 */
object FeedbackMailer {

    /** Where feedback is addressed. Already public on every commit. */
    const val RECIPIENT = "dsouzajenslee@gmail.com"

    /**
     * RFC-6068 `mailto:` URI. Pure (no Android types) so it is
     * unit-testable on the JVM; [intent] is the thin Android wrapper.
     */
    fun mailtoUri(
        subject: String,
        body: String,
        recipient: String = RECIPIENT,
    ): String = "mailto:$recipient?subject=${enc(subject)}&body=${enc(body)}"

    /** The `ACTION_SENDTO` intent the screen launches. */
    fun intent(
        subject: String,
        body: String,
        recipient: String = RECIPIENT,
    ): Intent = Intent(Intent.ACTION_SENDTO, Uri.parse(mailtoUri(subject, body, recipient)))

    // RFC 6068 wants percent-encoding. URLEncoder emits
    // application/x-www-form-urlencoded, which encodes space as '+'
    // (mail clients render that literally) — so restore those to %20.
    // A literal '+' in the input is emitted as %2B and is unaffected.
    private fun enc(s: String): String =
        URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
