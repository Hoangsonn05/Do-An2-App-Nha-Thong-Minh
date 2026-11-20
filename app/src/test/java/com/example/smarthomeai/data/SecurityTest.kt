package com.example.smarthomeai.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SecurityTest {
    @Test fun newExitWindowAllowsOpeningDoorUntilMonitoringStarts() {
        val waiting=SecurityStatus.parse(sensor().put("phase","EXIT_DELAY").put("remaining",20).put("security_profile","instant-20s"))!!
        assertFalse(waiting.protecting)
        assertTrue(waiting.copy(phase=SecurityPhase.ARMED).protecting)
        assertTrue(waiting.copy(exitBlocked=true).protecting)
    }
    @Test fun newSleepModeIsArmedAndUsesImmediateAlarms() {
        val security=SecurityStatus.parse(sensor().put("mode","SLEEP").put("phase","ARMED").put("security_profile","instant-20s").put("entry_seconds",0))!!
        assertTrue(security.protecting);assertTrue(security.instantAlarm);assertEquals(0,security.entrySeconds);assertFalse(security.needsAttention)
    }
    @Test fun sleepAlarmIsAcceptedWithoutEntryDelay() {
        val security=SecurityStatus.parse(sensor().put("mode","SLEEP").put("phase","ALARM").put("security_profile","instant-20s"))!!
        assertTrue(security.needsAttention);assertTrue(security.protecting)
    }
    @Test fun blockedExitDoesNotInventRemainingTime() {
        val security=SecurityStatus.parse(sensor().put("phase","EXIT_DELAY").put("remaining",0).put("exit_blocked",1).put("security_profile","instant-20s"))!!
        assertTrue(security.exitBlocked);assertEquals(0,security.remaining)
    }
    private fun sensor(extra: String = "") = JSONObject("""{"firmware":3,"stream_id":"board-a","mode":"AWAY","phase":"ENTRY_DELAY","remaining":20,"boot":2,"incident":1 $extra}""")
    private fun event(seq: Long, kind: String, incident: Long = 1, time: Long = 0) = DeviceEvent("board-a:$seq",seq,"board-a",2,incident,kind,time,seq*1000)
    @Test fun parsesDevicePolicyAndCountdown() {
        val security=SecurityStatus.parse(sensor())!!
        assertTrue(security.protecting); assertTrue(security.needsAttention)
        assertEquals(20,security.remaining); assertEquals("board-a:2:1",security.incidentKey)
    }
    @Test fun legacyFirmwareDoesNotInventSecurityPolicy() { assertNull(SecurityStatus.parse(JSONObject("""{"motion":0}"""))) }
    @Test fun inconsistentModeAndPhaseIsRejected() { assertNull(SecurityStatus.parse(sensor().put("mode","HOME"))) }
    @Test fun malformedPhaseIsRejected() { assertNull(SecurityStatus.parse(sensor().put("phase","UNKNOWN"))) }
    @Test fun clampsUntrustedCountdownAndTimings() {
        val security=SecurityStatus.parse(sensor().put("remaining",999).put("entry_seconds",1))!!
        assertEquals(120,security.remaining); assertEquals(5,security.entrySeconds)
    }
    @Test fun sleepIsNotArmedAndDoesNotAskForEntryConfirmation() {
        val security=SecurityStatus.parse(sensor().put("mode","SLEEP").put("phase","NIGHT"))!!
        assertFalse(security.protecting); assertFalse(security.needsAttention)
    }
    @Test fun eventBatchRequiresMonotonicUniqueSequenceNumbers() {
        val json=JSONObject("""{"stream_id":"board-a","oldest":1,"next":3,"events":[{"seq":2,"boot":1,"incident":1,"kind":"alarm_started","uptime":1},{"seq":1,"boot":1,"incident":1,"kind":"motion_entry","uptime":0}]}""")
        assertEquals(listOf(1L,2L),EventBatch.parse(json).events.map { it.seq })
        json.getJSONArray("events").getJSONObject(0).put("seq",1)
        assertTrue(runCatching { EventBatch.parse(json) }.isFailure)
    }
    @Test fun outOfWindowEventsAreRejected() {
        val json=JSONObject("""{"stream_id":"board-a","oldest":4,"next":5,"events":[{"seq":1,"boot":1,"incident":1,"kind":"motion_entry","uptime":0}]}""")
        assertTrue(runCatching { EventBatch.parse(json) }.isFailure)
    }
    @Test fun reconnectDeduplicatesEvents() {
        val old=listOf(event(2,"alarm_started"),event(1,"motion_entry"))
        assertEquals(3,mergeDeviceEvents(old,listOf(event(2,"alarm_started"),event(3,"acknowledged"))).size)
    }
    @Test fun eventHistoryIsBounded() { assertEquals(200,mergeDeviceEvents(emptyList(),(1L..250).map { event(it,"motion_entry") }).size) }
    @Test fun offlineTimelineBecomesOneIncident() {
        val events=listOf(event(1,"motion_entry"),event(2,"alarm_started"),event(3,"acknowledged"))
        val alerts=mergeIncidentAlerts(emptyList(),events,events)
        assertEquals(1,alerts.size); assertTrue(alerts.single().title.contains("đã kết thúc"))
        assertEquals("board-a:2:1",alerts.single().incidentKey)
    }
    @Test fun newBootDoesNotMergeWithEarlierIncident() {
        val events=listOf(event(1,"motion_entry"),event(2,"motion_entry").copy(boot=3))
        assertEquals(2,mergeIncidentAlerts(emptyList(),events,events).size)
    }
    @Test fun escalationMakesReviewedIncidentUnread() {
        val start=event(1,"motion_entry")
        val old=mergeIncidentAlerts(emptyList(),listOf(start),listOf(start)).map { it.copy(reviewed=true) }
        val alarm=event(2,"alarm_started")
        assertFalse(mergeIncidentAlerts(old,listOf(alarm),listOf(start,alarm)).single().reviewed)
    }
    @Test fun acknowledgingKeepsReviewStatusAndEvidence() {
        val start=event(1,"motion_entry")
        val old=mergeIncidentAlerts(emptyList(),listOf(start),listOf(start)).map { it.copy(reviewed=true,imagePath="private-photo") }
        val end=event(2,"acknowledged")
        val result=mergeIncidentAlerts(old,listOf(end),listOf(start,end)).single()
        assertTrue(result.reviewed); assertEquals("private-photo",result.imagePath)
    }
    @Test fun unknownDeviceTimeIsNeverReplacedWithSyncTime() {
        val e=event(1,"motion_entry")
        val alert=mergeIncidentAlerts(emptyList(),listOf(e),listOf(e)).single()
        assertEquals(0,alert.timestamp); assertTrue(alert.time.contains("chưa có giờ"))
    }
}
