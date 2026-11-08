package com.russoturissto.towerradar
import org.junit.Assert.*
import org.junit.Test
class TowerCodecTest {
    @Test fun dataSurvivesSerialization() {
        val original=TowerSite("node/5",55.55,37.37,"name","МТС",true,"LTE",
            "28","https://www.openstreetmap.org/node/5","OpenStreetMap",4,800,-80,"ref A")
        assertEquals(original,TowerDatabase.decode(TowerDatabase.encode(original)))
    }
    @Test fun nullableFieldsRemainAbsent() {
        val original=TowerSite("node/6",51.0,31.0,null,null,false,null,null,"source")
        assertEquals(original,TowerDatabase.decode(TowerDatabase.encode(original)))
    }
}
