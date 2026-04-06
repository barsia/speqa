package io.github.barsia.speqa.error

import com.intellij.openapi.diagnostic.IdeaLoggingEvent
import com.intellij.openapi.diagnostic.SubmittedReportInfo
import com.intellij.util.Consumer
import org.junit.Assert.assertEquals
import org.junit.Test
import java.awt.Panel
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SpeqaErrorReportSubmitterTest {

    @Test
    fun `submit reports failure when transport fails`() {
        val originalSender = SpeqaSentryClient.sender
        SpeqaSentryClient.sender = { _, _, _, _, _, _ -> false }
        try {
            val submitter = SpeqaErrorReportSubmitter()
            val latch = CountDownLatch(1)
            var status: SubmittedReportInfo.SubmissionStatus? = null

            val accepted = submitter.submit(
                events = arrayOf(IdeaLoggingEvent("boom", RuntimeException("boom"))),
                additionalInfo = "comment",
                parentComponent = Panel(),
                consumer = Consumer { info ->
                    status = info.status
                    latch.countDown()
                },
            )

            assertEquals(true, accepted)
            assertEquals(true, latch.await(5, TimeUnit.SECONDS))
            assertEquals(SubmittedReportInfo.SubmissionStatus.FAILED, status)
        } finally {
            SpeqaSentryClient.sender = originalSender
        }
    }
}
