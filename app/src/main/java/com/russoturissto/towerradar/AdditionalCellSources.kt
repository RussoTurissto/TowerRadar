package com.russoturissto.towerradar
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Estimated cell positions only, never verified mast coordinates. No IP fallback. */
object AdditionalCellSources {
    private fun site(cell:ServingCell,lat:Double,lon:Double,accuracy:Int?,
        source:String,url:String):TowerSite=TowerSite(
        id=source.lowercase()+"/"+cell.identity,
        latitude=lat, longitude=lon,
        name="Радиосота "+cell.network+" · "+cell.cellId,
        operator=cell.operator.takeIf { it.isNotBlank() },
        mobile=true, technology=cell.network, height=null, url=url,
        source=source+" · оценка, не координаты мачты",estimatedRangeMeters=accuracy)
    private fun valid(lat:Double,lon:Double):Boolean=
        lat.isFinite()&&lon.isFinite()&&lat in -90.0..90.0&&lon in -180.0..180.0
    fun mylnikov(cell:ServingCell):TowerSite? {
        if(!cell.validId)return null
        val url="https://api.mylnikov.org/geolocation/cell?v=1.1&data=open"+
            "&mcc="+cell.mcc+"&mnc="+cell.mnc+"&lac="+cell.area+"&cellid="+cell.cellId
        val conn=URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout=6500;conn.readTimeout=8500
        conn.setRequestProperty("User-Agent","TowerRadar/0.18.0")
        return try {
            if(conn.responseCode !in 200..299)return null
            val obj=JSONObject(conn.inputStream.bufferedReader().use{it.readText()})
            if(obj.optInt("result")!=200)return null
            val body=obj.optJSONObject("data")?:return null
            val lat=body.optString("lat").toDoubleOrNull()?:return null
            val lon=body.optString("lon").toDoubleOrNull()?:return null
            if(!valid(lat,lon))return null
            site(cell,lat,lon,body.optString("range").toDoubleOrNull()?.toInt(),
                "mylnikov","https://www.mylnikov.org/")
        } catch (_:Exception){null} finally {conn.disconnect()}
    }
    fun beaconDB(cell:ServingCell):TowerSite? {
        if(!cell.validId||cell.network !in setOf("LTE","UMTS","GSM","NR"))return null
        val body=JSONObject().apply{
            put("considerIp",false)
            put("radioType",if(cell.network=="UMTS")"wcdma" else cell.network.lowercase())
            put("cellTowers",JSONArray().put(JSONObject().apply{
                put("mobileCountryCode",cell.mcc);put("mobileNetworkCode",cell.mnc)
                put("locationAreaCode",cell.area)
                if(cell.network=="NR")put("newRadioCellId",cell.cellId)
                else put("cellId",cell.cellId)
            }))
        }
        val c=URL("https://api.beacondb.net/v1/geolocate").openConnection() as HttpURLConnection
        c.requestMethod="POST";c.doOutput=true;c.connectTimeout=6500;c.readTimeout=9000
        c.setRequestProperty("Content-Type","application/json")
        c.setRequestProperty("User-Agent","TowerRadar/0.18.0 (+https://github.com/RussoTurissto/TowerRadar)")
        return try {
            c.outputStream.use{it.write(body.toString().toByteArray(Charsets.UTF_8))}
            if(c.responseCode !in 200..299)return null
            val result=JSONObject(c.inputStream.bufferedReader().use{it.readText()})
            val pos=result.optJSONObject("location")?:return null
            val lat=pos.optDouble("lat",Double.NaN)
            val lon=pos.optDouble("lng",Double.NaN)
            if(!valid(lat,lon))return null
            site(cell,lat,lon,result.optDouble("accuracy",Double.NaN)
                .takeIf{it.isFinite()&&it>=0}?.toInt(),"beacondb","https://beacondb.net/")
        } catch (_:Exception){null} finally {c.disconnect()}
    }
    private fun mccMnc(cell: ServingCell): TowerSite? {
        if (!cell.validId) return null
        val url = "https://mcc-mnc.org/api/v1/cells?mcc=" + cell.mcc +
            "&mnc=" + cell.mnc + "&cell=" + cell.cellId +
            "&area=" + cell.area + "&radio=" + cell.network
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 6000; c.readTimeout = 8000
        c.setRequestProperty("User-Agent",
            "TowerRadar/0.18.0 (+https://github.com/RussoTurissto/TowerRadar)")
        return try {
            if (c.responseCode !in 200..299) return null
            val array = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                .optJSONArray("data") ?: return null
            for (i in 0 until array.length()) {
                val row = array.optJSONObject(i) ?: continue
                if (row.optInt("area", -1) != cell.area ||
                    row.optString("radio") != cell.network) continue
                val lat = row.optDouble("lat", Double.NaN)
                val lon = row.optDouble("lon", Double.NaN)
                if (!valid(lat, lon)) continue
                return site(cell, lat, lon,
                    row.optInt("range", -1).takeIf { it > 0 },
                    "mccmnc", "https://mcc-mnc.org/api")
                    .copy(source = "OpenCellID через MCC-MNC · приблизительно (CC BY-SA)")
            }
            null
        } catch (_: Exception) { null } finally { c.disconnect() }
    }

    fun lookup(key:String,cell:ServingCell,context:Context):TowerSite? {
        val open=if(key.isBlank())null else CellSources.lookup(key,cell)
        if (open != null) return open
        mylnikov(cell)?.let { return it }
        beaconDB(cell)?.let { return it }
        // MCC-MNC free tier is IP-based and limited to 100/week.
        // Conservative local hard cap of 12 lookups per rolling seven days.
        val prefs=context.getSharedPreferences("mcc_mnc_budget",Context.MODE_PRIVATE)
        val now=System.currentTimeMillis()
        val since=prefs.getLong("window",0)
        val used=if (now-since in 0 until 604800000L) prefs.getInt("requests",0)
            else 0
        if (used>=12) return null
        if (!prefs.edit().putLong("window",if(used==0)now else since)
                .putInt("requests",used+1).commit()) return null
        return mccMnc(cell)
    }
}
