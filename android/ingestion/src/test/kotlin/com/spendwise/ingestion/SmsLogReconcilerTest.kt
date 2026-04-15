package com.spendwise.ingestion

import com.spendwise.database.entities.SmsLogEntity
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SmsLogReconcilerTest {

    private lateinit var smsLog: FakeSmsLogDao
    private lateinit var lookup: FakeInboxLookup
    private lateinit var reconciler: SmsLogReconciler

    @BeforeEach
    fun setUp() {
        smsLog = FakeSmsLogDao()
        lookup = FakeInboxLookup()
        reconciler = SmsLogReconciler(smsLog, lookup)
    }

    @Test
    fun `empty sms_log produces zero outcome`() = runTest {
        val result = reconciler.reconcile()
        result shouldBe SmsLogReconciler.Outcome(
            scanned = 0, resolved = 0, conflictsSkipped = 0, notFound = 0,
        )
    }

    @Test
    fun `row with null android_sms_id is filled when lookup returns an id`() = runTest {
        val inserted = insertNullRow(sender = "JD-KOTAKB-S", body = "foo", ts = 1000)
        lookup.register("JD-KOTAKB-S", "foo", 1000, id = 42L)

        val result = reconciler.reconcile()

        result.resolved shouldBe 1
        smsLog.getById(inserted)!!.androidSmsId shouldBe 42L
    }

    @Test
    fun `row with matching id already held by another row is left alone`() = runTest {
        // Import previously created sms_log #2 with android_sms_id = 42.
        smsLog.insert(
            SmsLogEntity(
                androidSmsId = 42L,
                sender = "JD-KOTAKB-S",
                body = "foo",
                receivedAtMs = 1000,
                classification = "UPI_PAYMENT",
                patternMatched = "kotak_upi_debit",
                source = "SMS_IMPORT",
            ),
        )
        // Receiver inserted the same SMS first, with NULL id.
        val receiverRowId = insertNullRow(sender = "JD-KOTAKB-S", body = "foo", ts = 1000)
        lookup.register("JD-KOTAKB-S", "foo", 1000, id = 42L)

        val result = reconciler.reconcile()

        result.resolved shouldBe 0
        result.conflictsSkipped shouldBe 1
        smsLog.getById(receiverRowId)!!.androidSmsId shouldBe null
    }

    @Test
    fun `row with no matching inbox entry is counted as notFound`() = runTest {
        insertNullRow(sender = "JD-KOTAKB-S", body = "foo", ts = 1000)

        val result = reconciler.reconcile()

        result.notFound shouldBe 1
        result.resolved shouldBe 0
    }

    @Test
    fun `non-null rows are not rescanned`() = runTest {
        smsLog.insert(
            SmsLogEntity(
                androidSmsId = 7L,
                sender = "JD-KOTAKB-S",
                body = "x",
                receivedAtMs = 500,
                classification = "UPI_PAYMENT",
                patternMatched = null,
                source = "SMS_REALTIME",
            ),
        )
        val result = reconciler.reconcile()
        result.scanned shouldBe 0
    }

    @Test
    fun `second reconcile pass is a no-op after first resolved everything`() = runTest {
        insertNullRow(sender = "JD-KOTAKB-S", body = "a", ts = 100)
        insertNullRow(sender = "JD-KOTAKB-S", body = "b", ts = 200)
        lookup.register("JD-KOTAKB-S", "a", 100, id = 1L)
        lookup.register("JD-KOTAKB-S", "b", 200, id = 2L)

        val first = reconciler.reconcile()
        first.resolved shouldBe 2

        val second = reconciler.reconcile()
        second.scanned shouldBe 0
        second.resolved shouldBe 0
    }

    @Test
    fun `multiple candidates mixed - only unresolvable stay null`() = runTest {
        val a = insertNullRow("S1", "a", 100)
        val b = insertNullRow("S2", "b", 200)
        val c = insertNullRow("S3", "c", 300)
        lookup.register("S1", "a", 100, id = 10L)
        // S2/b not registered → notFound
        lookup.register("S3", "c", 300, id = 30L)
        // Pre-existing clash for id=30 to trigger conflict path on c.
        smsLog.insert(
            SmsLogEntity(
                androidSmsId = 30L,
                sender = "S3", body = "c", receivedAtMs = 300,
                classification = "UPI_PAYMENT",
                patternMatched = "kotak_upi_debit",
                source = "SMS_IMPORT",
            ),
        )

        val result = reconciler.reconcile()
        result.scanned shouldBe 3
        result.resolved shouldBe 1
        result.notFound shouldBe 1
        result.conflictsSkipped shouldBe 1

        smsLog.getById(a)!!.androidSmsId shouldBe 10L
        smsLog.getById(b)!!.androidSmsId shouldBe null
        smsLog.getById(c)!!.androidSmsId shouldBe null
    }

    // ---- helpers ----

    private suspend fun insertNullRow(sender: String, body: String, ts: Long): Long =
        smsLog.insert(
            SmsLogEntity(
                androidSmsId = null,
                sender = sender,
                body = body,
                receivedAtMs = ts,
                classification = "UPI_PAYMENT",
                patternMatched = "kotak_upi_debit",
                source = "SMS_REALTIME",
            ),
        )
}

/** In-memory fake for [SmsInboxLookup]. */
private class FakeInboxLookup : SmsInboxLookup {
    private val registered = mutableMapOf<Triple<String, String, Long>, Long>()

    fun register(sender: String, body: String, receivedAtMs: Long, id: Long) {
        registered[Triple(sender, body, receivedAtMs)] = id
    }

    override suspend fun findId(
        sender: String,
        body: String,
        receivedAtMs: Long,
        toleranceMs: Long,
    ): Long? = registered[Triple(sender, body, receivedAtMs)]
}
