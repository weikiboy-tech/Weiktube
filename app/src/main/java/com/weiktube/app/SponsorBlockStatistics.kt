package com.weiktube.app

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*

internal class SponsorBlockStatistics(private val store: SharedPreferences) {
    constructor(context: Context): this(context.getSharedPreferences("sponsor_block_statistics",Context.MODE_PRIVATE))
    data class Totals(val count: Long=0,val seconds: Double=0.0,val categories: Map<String,Long> = emptyMap())
    private data class Record(val category: String,val seconds: Double,var undone:Boolean=false)
    private val seen=linkedMapOf<String,Record>()
    var totals by mutableStateOf(read());private set
    private fun read()=Totals(store.getLong("count",0),Double.fromBits(store.getLong("seconds",0)),
        SponsorCategory.entries.associate {it.key to store.getLong(it.key,0)})
    private fun save(value: Totals){
        totals=value
        val edit=store.edit().putLong("count",value.count).putLong("seconds",value.seconds.toBits())
        value.categories.forEach { (key,count)->edit.putLong(key,count) };edit.apply()
    }
    fun record(key: String,category: String,seconds: Double) {
        if(key in seen||SponsorCategory.from(category)==null||!seconds.isFinite()||seconds<=0||seconds>604800)return
        seen[key]=Record(category,seconds)
        while(seen.size>512)seen.remove(seen.keys.first())
        save(totals.copy(count=totals.count+1,seconds=totals.seconds+seconds,
            categories=totals.categories+(category to ((totals.categories[category]?:0)+1))))
    }
    fun undo(key: String){
        val previous=seen[key]?:return
        if(previous.undone)return
        previous.undone=true
        save(totals.copy(count=(totals.count-1).coerceAtLeast(0),seconds=(totals.seconds-previous.seconds).coerceAtLeast(0.0),
            categories=totals.categories+(previous.category to ((totals.categories[previous.category]?:0)-1).coerceAtLeast(0))))
    }
    fun reset(){seen.clear();store.edit().clear().apply();totals=Totals()}
}
