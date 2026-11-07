package com.erhu.erhu.feature.library.transition

import com.erhu.erhu.core.model.*
import org.junit.Assert.*
import org.junit.Test

class TransitionPointPrecisionTest {
 private val spec=TransitionEditing.setCurve(TransitionSpec(durationMs=4000),TransitionCurve.CUSTOM)
 private val out=TransitionCurveHandle(TransitionRole.OUTGOING,1)
 private fun edit(handle:TransitionCurveHandle=out,time:Double=600.0,db:Double=-6.020599913,silence:Boolean=false)=
  TransitionPointEdit(handle,TransitionPointValues(time,db,silence))

 @Test fun millisecondsUseActualOverlapAndDecibelsConvertToLinearGain() {
  val updated=TransitionPointPrecision.updated(spec,edit(),2000)
  val point=updated.outgoingCurve!!.points[1]
  assertEquals(0.3f,point.t,0.00001f)
  assertEquals(0.5f,point.gain,0.00001f)
  assertEquals(spec.incomingCurve,updated.incomingCurve)
  assertEquals(spec.durationMs,updated.durationMs)
  assertEquals(spec.filter,updated.filter)
  assertEquals(spec.outgoingCutMs,updated.outgoingCutMs)
 }

 @Test fun neighboursAndEveryFixedEndpointArePreserved() {
  val low=TransitionPointPrecision.updated(spec,edit(time=-99999.0),2000)
  assertEquals(VolumeCurve.MIN_GAP,low.outgoingCurve!!.points[1].t,0.00001f)
  val high=TransitionPointPrecision.updated(spec,edit(time=99999.0),2000)
  assertEquals(0.5f-VolumeCurve.MIN_GAP,high.outgoingCurve!!.points[1].t,0.00001f)
  for(role in TransitionRole.entries) {
   val curve=TransitionEditing.curveOf(spec,role)
   for(index in listOf(0,curve.points.lastIndex)) {
    val changed=TransitionPointPrecision.updated(spec,edit(TransitionCurveHandle(role,index),time=99999.0,db=-30.0,silence=true),2000)
    val result=TransitionEditing.curveOf(changed,role).points[index]
    assertEquals(curve.points[index].t,result.t)
    TransitionEditing.fixedGain(role,index,curve.points.lastIndex)?.let { assertEquals(it,result.gain) }
   }
  }
 }

 @Test fun silenceIsExactAndGainIsBoundedWithoutAllowingNonFiniteNumbers() {
  assertEquals(0f,TransitionPointPrecision.updated(spec,edit(silence=true),2000).outgoingCurve!!.points[1].gain)
  assertEquals(1f,TransitionPointPrecision.updated(spec,edit(db=20.0),2000).outgoingCurve!!.points[1].gain)
  val minimum=TransitionPointPrecision.updated(spec,edit(db=-500.0),2000).outgoingCurve!!.points[1].gain
  assertTrue(minimum>0f)
  assertEquals(0.00001584893f,minimum,0.00000001f)
  assertEquals(spec,TransitionPointPrecision.updated(spec,edit(time=Double.NaN),2000))
  assertEquals(spec,TransitionPointPrecision.updated(spec,edit(db=Double.POSITIVE_INFINITY),2000))
  assertEquals(spec,TransitionPointPrecision.updated(spec,edit(),0))
  assertEquals(spec,TransitionPointPrecision.updated(spec,edit(TransitionCurveHandle(TransitionRole.OUTGOING,99)),2000))
 }

 @Test fun valuesRoundTripForAnUnchangedDraft() {
  for(role in TransitionRole.entries) {
   val curve=TransitionEditing.curveOf(spec,role)
   for(index in curve.points.indices) {
    val handle=TransitionCurveHandle(role,index)
    val values=TransitionPointPrecision.values(spec,handle,2000)!!
    val result=TransitionPointPrecision.updated(spec,TransitionPointEdit(handle,values),2000)
    val point=TransitionEditing.curveOf(result,role).points[index]
    assertEquals(curve.points[index].t,point.t,0.00001f)
    assertEquals(curve.points[index].gain,point.gain,0.00001f)
   }
  }
 }
}
