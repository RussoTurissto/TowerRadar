package com.russoturissto.towerradar
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject

/** Persistent merged observations. Different source identities are never conflated. */
class TowerDatabase(ctx: Context): SQLiteOpenHelper(ctx, "tower-observations.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE towers (id TEXT PRIMARY KEY, lat REAL NOT NULL, lon REAL NOT NULL, body TEXT NOT NULL, modified INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX by_geolocation ON towers(lat,lon)")
        db.execSQL("CREATE TABLE coverage (key TEXT PRIMARY KEY, lat REAL, lon REAL, radius INTEGER, checked INTEGER)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    data class Result(val added: Int, val changed: Int, val retained: Int) {
        val processed: Int get() = added + changed + retained
    }
    @Synchronized fun merge(items: List<TowerSite>, progress: (Int,Int)->Unit = { _, _ -> }): Result {
        val unique = items.filter { it.id.isNotBlank() && it.latitude in -90.0..90.0 &&
            it.longitude in -180.0..180.0 }.distinctBy { it.id }
        var added=0; var changed=0; var retained=0
        val db=writableDatabase
        db.beginTransaction()
        try {
            unique.forEachIndexed { idx, tower ->
                val body=encode(tower)
                var previous:String?=null
                db.query("towers",arrayOf("body"),"id=?",arrayOf(tower.id),null,null,null).use {
                    if(it.moveToFirst()) previous=it.getString(0)
                }
                when {
                    previous==null -> added++
                    previous==body -> retained++
                    else -> changed++
                }
                if(previous!=body) {
                    val cv=ContentValues().apply {
                        put("id",tower.id); put("lat",tower.latitude); put("lon",tower.longitude)
                        put("body",body); put("modified",System.currentTimeMillis())
                    }
                    db.insertWithOnConflict("towers",null,cv,SQLiteDatabase.CONFLICT_REPLACE)
                }
                if((idx+1)%25==0 || idx+1==unique.size) progress(idx+1,unique.size)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return Result(added,changed,retained)
    }
    @Synchronized fun all():List<TowerSite> {
        val found=ArrayList<TowerSite>()
        readableDatabase.query("towers",arrayOf("body"),null,null,null,null,
            "modified DESC","25000").use {
            while(it.moveToNext()) try { found.add(decode(it.getString(0))) } catch (_:Exception) {}
        }
        return found
    }
    @Synchronized fun count():Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM towers",null).use {
            return if(it.moveToFirst()) it.getInt(0) else 0
        }
    }
    @Synchronized fun lastChanged(id:String):Long? {
        readableDatabase.query("towers",arrayOf("modified"),"id=?",arrayOf(id),
            null,null,null).use { return if(it.moveToFirst()) it.getLong(0) else null }
    }
    @Synchronized fun recentlyChecked(lat:Double,lon:Double,radius:Int,maxAge:Long):Boolean {
        readableDatabase.query("coverage",arrayOf("lat","lon","checked"),
            "radius=?",arrayOf(radius.toString()),null,null,"checked DESC","30").use {
            while(it.moveToNext()) {
                val age=System.currentTimeMillis()-it.getLong(2)
                if(age in 0..maxAge && TowerData.distanceKm(
                    lat,lon,it.getDouble(0),it.getDouble(1)) < (radius/4.0).coerceIn(0.3,1.5))
                    return true
            }
        }
        return false
    }
    @Synchronized fun markChecked(lat:Double,lon:Double,radius:Int) {
        val cv=ContentValues().apply {
            put("key",radius.toString()+":"+ (lat*100).toInt()+":"+(lon*100).toInt())
            put("lat",lat); put("lon",lon); put("radius",radius)
            put("checked",System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict("coverage",null,cv,SQLiteDatabase.CONFLICT_REPLACE)
    }
    @Synchronized fun clear() {
        val db=writableDatabase
        db.beginTransaction()
        try {
            db.delete("towers",null,null); db.delete("coverage",null,null)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    companion object {
        fun encode(s:TowerSite):String=JSONObject().apply {
            put("id",s.id);put("lat",s.latitude);put("lon",s.longitude)
            put("name",s.name);put("operator",s.operator);put("mobile",s.mobile)
            put("technology",s.technology);put("height",s.height)
            put("url",s.url);put("source",s.source);put("samples",s.samples)
            put("range",s.estimatedRangeMeters);put("signal",s.averageSignalDbm)
            put("extra",s.extraInfo)
        }.toString()
        fun decode(body:String):TowerSite {
            val j=JSONObject(body)
            fun string(k:String):String?=j.optString(k).takeIf { it.isNotBlank() && it!="null" }
            fun number(k:String):Int?=if(!j.has(k)||j.isNull(k))null else j.getInt(k)
            return TowerSite(j.getString("id"),j.getDouble("lat"),j.getDouble("lon"),
                string("name"),string("operator"),j.optBoolean("mobile"),
                string("technology"),string("height"),string("url")?:"",
                string("source")?:"Неизвестный источник",number("samples"),
                number("range"),number("signal"),string("extra"))
        }
    }
}
