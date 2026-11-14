package com.russoturissto.towerradar

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale

/**
 * Yandex's official JavaScript API 2.1. Unlike Tiles API, its built-in map
 * types support satellite and hybrid without undocumented raster endpoints.
 */
class YandexWebMap(context: Context,
                   private val onCamera: (Double, Double, Double) -> Unit,
                   private val onSelect: (Double, Double) -> Unit,
                   private val onLongTap: (Double, Double) -> Unit,
                   private val onError: (String) -> Unit) {
    @SuppressLint("SetJavaScriptEnabled")
    val view = WebView(context).apply {
        setBackgroundColor(Color.rgb(233, 231, 216))
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.javaScriptCanOpenWindowsAutomatically = false
        webChromeClient = WebChromeClient()
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                w: WebView?, request: android.webkit.WebResourceRequest?
            ): Boolean = request?.isForMainFrame == true &&
                !request.url.toString().startsWith("https://com.russoturissto.towerradar/")
            override fun onReceivedError(w: WebView?, request: android.webkit.WebResourceRequest?,
                                         error: android.webkit.WebResourceError?) {
                if (request?.isForMainFrame == true)
                    onError("Не удалось открыть карту Яндекс JS API")
            }
        }
        addJavascriptInterface(Bridge(), "TowerRadarBridge")
        visibility = View.GONE
    }
    private var activeKey = ""
    private var activeMode = "yandex#map"
    private var ready = false
    private var loaded = false
    private var pendingObjects: String? = null
    private fun js(code: String) {
        view.post { if (loaded) view.evaluateJavascript(code, null) }
    }
    inner class Bridge {
        @JavascriptInterface fun ready() {
            view.post {
                ready = true
                js("TowerRadar.setMode(" + JSONObject.quote(activeMode) + ")")
                pendingObjects?.let { js("TowerRadar.setObjects(" + it + ")") }
            }
        }
        @JavascriptInterface fun camera(lat: Double, lon: Double, zoom: Double) {
            if (lat in -85.0..85.0 && lon in -180.0..180.0 && zoom in 0.0..22.0)
                view.post { onCamera(lat, lon, zoom) }
        }
        @JavascriptInterface fun tap(lat: Double, lon: Double) {
            if (lat in -90.0..90.0 && lon in -180.0..180.0)
                view.post { onSelect(lat, lon) }
        }
        @JavascriptInterface fun longTap(lat: Double, lon: Double) {
            if (lat in -90.0..90.0 && lon in -180.0..180.0)
                view.post { onLongTap(lat, lon) }
        }
        @JavascriptInterface fun failed() {
            view.post { onError("Ключ JavaScript API отклонён или сервер карты недоступен") }
        }
    }

    fun show(key: String, mode: String, lat: Double, lon: Double, zoom: Double) {
        require(mode in setOf("yandex#map", "yandex#satellite", "yandex#hybrid"))
        view.visibility = View.VISIBLE
        activeMode = mode
        if (loaded && key == activeKey) {
            if (ready) js("TowerRadar.setMode(" + JSONObject.quote(mode) + ")")
            return
        }
        loaded = true; ready = false; activeKey = key
        val endpoint = "https://api-maps.yandex.ru/2.1/?apikey=" +
            URLEncoder.encode(key, "UTF-8") + "&lang=ru_RU"
        val html = """
<!doctype html><html lang="ru"><head><meta charset="UTF-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no"/>
<style>html,body,#map{width:100%;height:100%;margin:0;overflow:hidden;}
body{background:#e9e7d8}</style>
<script src="$endpoint" onerror="TowerRadarBridge.failed()"></script>
<script>
(function(){
var map,objects=[],timer;
function add(obj){map.geoObjects.add(obj);objects.push(obj)}
function dot(lat,lon,name,color){
 var p=new ymaps.Placemark([lat,lon],{hintContent:name},
   {preset:'islands#circleDotIcon',iconColor:color,hasBalloon:false});
 p.events.add('click',function(e){e.preventDefault();TowerRadarBridge.tap(lat,lon)});
 add(p);
}
window.TowerRadar={
 setMode:function(type){if(map)map.setType(type)},
 move:function(lat,lon,zoom){if(map)map.setCenter([lat,lon],zoom,{duration:0})},
 setObjects:function(data){
  if(!map)return;
  objects.forEach(function(o){map.geoObjects.remove(o)});objects=[];
  (data.towers||[]).forEach(function(t){
   dot(t.lat,t.lon,t.name||'Вышка',t.manual?'#a8b977':
      (t.estimated?'#2ba4b5':'#708050'))
  });
  if(data.me){
   dot(data.me.lat,data.me.lon,'Моё положение','#09a0b2');
  }
  if(data.serving){
   dot(data.serving.lat,data.serving.lon,'Обслуживающая сота','#d38a38');
   if(data.me)add(new ymaps.Polyline([[data.me.lat,data.me.lon],
     [data.serving.lat,data.serving.lon]],{},{strokeColor:'#d38a38',strokeWidth:3,strokeStyle:'dash'}))
  }
  if(data.coverage && data.target && data.coverage > 0) {
    add(new ymaps.Circle([[data.target.lat,data.target.lon],data.coverage],
       {hintContent:'Схематический радиус · НЕ прогноз сигнала'},
       {fillColor:'#8da96333',strokeColor:'#8da963',
        strokeWidth:2,strokeStyle:'dash'}));
  }
  if(data.me&&data.target)add(new ymaps.Polyline([
    [data.me.lat,data.me.lon],[data.target.lat,data.target.lon]
   ],{},{strokeColor:'#c15434',strokeWidth:2,strokeStyle:'dash'}))
 }
};
function init(){
 map=new ymaps.Map('map',{center:[$lat,$lon],zoom:$zoom,
    type:__MAPTYPE__,controls:[]},{suppressMapOpenBlock:true});
 map.behaviors.enable(['drag','multiTouch','scrollZoom']);
 map.events.add('boundschange',function(){
   clearTimeout(timer);timer=setTimeout(function(){
      var c=map.getCenter();TowerRadarBridge.camera(c[0],c[1],map.getZoom())
   },350);
 });
 map.events.add('click',function(e){var c=e.get('coords');TowerRadarBridge.tap(c[0],c[1])});
 map.events.add('contextmenu',function(e){var c=e.get('coords');TowerRadarBridge.longTap(c[0],c[1])});
 TowerRadarBridge.ready();
}
if(typeof ymaps==='undefined')TowerRadarBridge.failed();else ymaps.ready(init);
})();
</script></head><body><div id="map"></div></body></html>
""".trimIndent().replace("__MAPTYPE__", JSONObject.quote(mode))
        // Android WebView uses the HTTPS base origin as the Referer.
        view.loadDataWithBaseURL("https://com.russoturissto.towerradar/",
            html, "text/html", "UTF-8", null)
    }
    val showing: Boolean get() = view.visibility == View.VISIBLE
    fun hide() { view.visibility = View.GONE }
    fun move(lat: Double, lon: Double, zoom: Double) {
        if (ready && showing) js("TowerRadar.move(" +
            String.format(Locale.US, "%.8f,%.8f,%.2f", lat, lon, zoom) + ")")
    }
    fun setObjects(towers: List<TowerSite>, me: Pair<Double,Double>?,
                   serving: TowerSite?, target: TowerSite?,
                   coverageRadiusMeters: Int = 0) {
        val root = JSONObject()
        val markers = JSONArray()
        towers.take(1600).forEach {
            markers.put(JSONObject().put("lat", it.latitude).put("lon", it.longitude)
                .put("estimated", !it.source.startsWith("OpenStreetMap"))
                .put("manual", it.id.startsWith("user/"))
                .put("name", (it.name ?: it.operator ?: "Объект связи").take(85)))
        }
        root.put("towers", markers)
        fun point(lat: Double, lon: Double) = JSONObject().put("lat", lat).put("lon", lon)
        if (me != null) root.put("me", point(me.first, me.second))
        if (serving != null) root.put("serving", point(serving.latitude, serving.longitude))
        if (target != null) root.put("target", point(target.latitude, target.longitude))
        if (coverageRadiusMeters > 0) root.put("coverage", coverageRadiusMeters)
        pendingObjects = root.toString()
        if (ready && showing) js("TowerRadar.setObjects(" + pendingObjects + ")")
    }
    fun onResume() { view.onResume() }
    fun onPause() { view.onPause() }
    fun destroy() { view.removeJavascriptInterface("TowerRadarBridge"); view.destroy() }
}
