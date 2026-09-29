package de.sanniki.wakesleuth.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SourceClassifierTest {

    @Test
    fun `packages map to their system bucket`() {
        assertEquals(SourceKind.ANDROID_SYSTEM, SourceClassifier.kindOfPackage("android"))
        assertEquals(SourceKind.GOOGLE_PLAY_SERVICES, SourceClassifier.kindOfPackage("com.google.android.gms.persistent"))
        assertEquals(SourceKind.PLAY_STORE, SourceClassifier.kindOfPackage("com.android.vending"))
        assertEquals(SourceKind.SYSTEM_UI, SourceClassifier.kindOfPackage("com.android.systemui"))
        assertEquals(SourceKind.SAMSUNG_TELEPHONY_SIM, SourceClassifier.kindOfPackage("com.android.stk2"))
        assertEquals(SourceKind.MMS_CELLULAR_SERVICE, SourceClassifier.kindOfPackage("com.android.mms.service"))
        assertEquals(SourceKind.PHONE_SERVICE, SourceClassifier.kindOfPackage("com.android.phone"))
        assertEquals(SourceKind.TELEPHONY_STORAGE, SourceClassifier.kindOfPackage("com.android.providers.telephony"))
        assertEquals(SourceKind.CONTACTS, SourceClassifier.kindOfPackage("com.android.providers.contacts"))
        assertEquals(SourceKind.CALENDAR, SourceClassifier.kindOfPackage("com.android.providers.calendar"))
        assertEquals(SourceKind.GOOGLE_CELLULAR_IMS, SourceClassifier.kindOfPackage("com.google.android.ims"))
        assertEquals(SourceKind.BLUETOOTH, SourceClassifier.kindOfPackage("com.android.bluetooth"))
        assertEquals(SourceKind.NETWORK_STACK, SourceClassifier.kindOfPackage("com.android.networkstack.process"))
        assertEquals(SourceKind.SAMSUNG_SYSTEM_SERVICE, SourceClassifier.kindOfPackage("com.sec.android.app.clockpackage"))
        assertEquals(SourceKind.ONEPLUS_SYSTEM_SERVICE, SourceClassifier.kindOfPackage("com.oplus.athena"))
        assertEquals(SourceKind.APP, SourceClassifier.kindOfPackage("com.whatsapp"))
    }

    @Test
    fun `technical tokens map to their bucket`() {
        assertEquals(SourceKind.SAMSUNG_TELEPHONY_SIM, SourceClassifier.classifyRaw("RILJ_ACK_WL").kind)
        assertEquals(SourceKind.SAMSUNG_OFFLINE_FINDING, SourceClassifier.classifyRaw("FMM-acquireWakeLock").kind)
        assertEquals(SourceKind.ONEPLUS_SCREEN_GESTURES, SourceClassifier.classifyRaw("OplusScreenOffGestureManager").kind)
        assertEquals(SourceKind.RADIO_NETWORK, SourceClassifier.classifyRaw("ipa_client_ws").kind)
        assertEquals(SourceKind.TIME_TICK, SourceClassifier.classifyRaw("*alarm*:android.intent.action.TIME_TICK").kind)
        assertEquals(SourceKind.GOOGLE_PLAY_SERVICES, SourceClassifier.classifyRaw("GCoreFlp").kind)
        assertEquals(SourceKind.ANDROID_SYSTEM, SourceClassifier.classifyRaw("android/com.android.server.X").kind)
        assertEquals(SourceKind.UNKNOWN_SYSTEM, SourceClassifier.classifyRaw("foo_bar_wake").kind)

        val gmail = SourceClassifier.classifyRaw("gmail-ls/sync")
        assertEquals(SourceKind.APP, gmail.kind)
        assertEquals("com.google.android.gm", gmail.packageName)
    }

    @Test
    fun `sources merge by identity not by token`() {
        val gms1 = SourceClassifier.classify("com.google.android.gms", "*alarm*:com.google.android.gms.a")
        val gms2 = SourceClassifier.classify("com.google.android.gms.persistent", "*job*:x")
        assertEquals(gms1.groupKey, gms2.groupKey)

        val app1 = SourceClassifier.classify("com.example.one")
        val app2 = SourceClassifier.classify("com.example.two")
        assertNotEquals(app1.groupKey, app2.groupKey)

        val unknown1 = SourceClassifier.classify(null, "vendor_thing/extra")
        val unknown2 = SourceClassifier.classify(null, "vendor_thing/other")
        assertEquals(unknown1.groupKey, unknown2.groupKey)
    }

    @Test
    fun `package is extracted from a BatteryStats token`() {
        assertEquals("com.foo", CpuEvidenceRules.extractPackageName("com.foo/androidx.work.impl.background.systemjob.SystemJobService"))
        assertEquals(null, CpuEvidenceRules.extractPackageName("RILJ_ACK_WL"))
    }
}
