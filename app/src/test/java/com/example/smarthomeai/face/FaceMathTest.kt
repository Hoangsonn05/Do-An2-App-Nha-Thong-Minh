package com.example.smarthomeai.face

import org.junit.Assert.*
import org.junit.Test

class FaceMathTest {
    @Test fun identicalFaceVectorsMatch() { assertEquals(1f, FaceMath.similarity(floatArrayOf(1f,2f),floatArrayOf(2f,4f))!!,.0001f) }
    @Test fun orthogonalVectorsDoNotMatch() { assertEquals(0f, FaceMath.similarity(floatArrayOf(1f,0f),floatArrayOf(0f,1f))!!,.0001f) }
    @Test fun oppositeVectorsDoNotMatch() { assertEquals(-1f, FaceMath.similarity(floatArrayOf(1f),floatArrayOf(-1f))!!,.0001f) }
    @Test fun rejectZeroVectors() { assertNull(FaceMath.similarity(floatArrayOf(0f,0f),floatArrayOf(1f,0f))) }
    @Test fun rejectInvalidStoredData() { assertNull(FaceMath.similarity(floatArrayOf(Float.NaN),floatArrayOf(1f))); assertNull(FaceMath.normalize(floatArrayOf(Float.POSITIVE_INFINITY))) }
    @Test fun rejectWrongDimensions() { assertNull(FaceMath.similarity(floatArrayOf(1f),floatArrayOf(1f,2f))) }
    @Test fun requiresThreeConsecutiveFrames() { val gate=RecognitionGate(); assertFalse(gate.observe("A")); assertFalse(gate.observe("A")); assertTrue(gate.observe("A")) }
    @Test fun personChangeResetsConfirmation() { val gate=RecognitionGate(); gate.observe("A"); gate.observe("A"); assertFalse(gate.observe("B")); assertFalse(gate.observe("B")); assertTrue(gate.observe("B")) }
    @Test fun lostFaceResetsConfirmation() { val gate=RecognitionGate(); gate.observe("A"); gate.observe("A"); gate.observe(null); assertFalse(gate.observe("A")) }
    @Test fun doorAccessRequiresCurrentVerifiedPerson() {
        val session=FaceAccessSession();assertFalse(session.canOpen("An",100))
        session.observe("An",100);assertTrue(session.canOpen("An",200));assertFalse(session.canOpen("Binh",200))
    }
    @Test fun lostFaceRevokesDoorAccess() {
        val session=FaceAccessSession();session.observe("An",100);session.observe(null,200);assertFalse(session.canOpen("An",201))
    }
    @Test fun frozenCameraDoesNotKeepDoorAccess() {
        val session=FaceAccessSession();session.observe("An",100);assertFalse(session.canOpen("An",1601));assertFalse(session.canOpen("An",99))
    }
    @Test fun doorRequestConsumesVerification() {
        val session=FaceAccessSession();session.observe("An",100);assertTrue(session.consume("An",200));assertFalse(session.consume("An",201))
        session.observe("An",300);assertTrue(session.consume("An",301))
    }
}
