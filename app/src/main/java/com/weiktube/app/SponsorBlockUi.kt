package com.weiktube.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun SponsorBlockOverlay(controller:SponsorBlockController,modifier:Modifier=Modifier) {
    val manual=controller.manual;val notice=controller.notice
    if(manual==null&&notice==null)return
    Surface(modifier.widthIn(max=340.dp),shape=RoundedCornerShape(12.dp),color=Color.Black.copy(alpha=.82f),contentColor=Color.White){
        Column(Modifier.padding(horizontal=10.dp,vertical=2.dp)){
            if(notice!=null)Row(verticalAlignment=Alignment.CenterVertically){
                Text("${SponsorCategory.from(notice.category)?.label?:"Abschnitt"} übersprungen · ${notice.seconds.roundToInt()} Sek.",
                    Modifier.weight(1f),style=MaterialTheme.typography.labelMedium)
                TextButton(onClick=controller::undo){Text("Rückgängig")}
            }
            if(manual!=null)TextButton(onClick=controller::skipManual){Text("${SponsorCategory.from(manual.category)?.label?:"Abschnitt"} überspringen")}
        }
    }
}

@Composable
internal fun SponsorBlockSettings(model:WeiktubeModel,value:SponsorBlockPreferences,activity:MainActivity){
    var confirm by remember {mutableStateOf(false)}
    val controller=activity.player.sponsorBlock
    Card(Modifier.fillMaxWidth()){
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Text("SponsorBlock",style=MaterialTheme.typography.titleLarge)
            Text("Community-Zeitabschnitte für Sponsoren und andere Videoteile. Unabhängig vom AdBlock; Angaben können falsch oder veraltet sein.",style=MaterialTheme.typography.bodySmall)
            SponsorSwitch("SponsorBlock aktivieren",value.enabled){if(it)confirm=true else model.boolean("sponsorEnabled",false)}
            if(value.enabled){
                Text(controller.status,style=MaterialTheme.typography.bodySmall)
                SponsorCategory.entries.forEach {category->
                    var open by remember(category) {mutableStateOf(false)}
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                        Text(category.label,Modifier.weight(1f))
                        Box{
                            TextButton(onClick={open=true}){Text((value.modes[category.key]?:category.defaultMode).label)}
                            DropdownMenu(open,{open=false}){
                                SponsorMode.entries.forEach {mode->DropdownMenuItem(text={Text(mode.label)},onClick={model.string("sponsorMode_${category.key}",mode.name);open=false})}
                            }
                        }
                    }
                }
                Text("exclusive_access ist eine Ganzvideo-Kennzeichnung, kein überspringbarer Abschnitt. Musikfremde Abschnitte und Füllmaterial bleiben standardmäßig aus.",style=MaterialTheme.typography.bodySmall)
                SponsorSwitch("Farbige Abschnitte anzeigen",value.markers){model.boolean("sponsorMarkers",it)}
                if(value.markers)Text(controller.markerStatus,style=MaterialTheme.typography.bodySmall)
                SponsorSwitch("Überspring-Hinweise anzeigen",value.notices){model.boolean("sponsorNotices",it)}
                Text("Hinweise enthalten Rückgängig. Manuelle Skip-Buttons bleiben unabhängig davon im großen Player verfügbar. Im kleinen Player/PiP erscheinen keine SponsorBlock-Overlays.",style=MaterialTheme.typography.bodySmall)
            }
            SponsorSwitch("SponsorBlock-Statistiken anzeigen",value.showStatistics){model.boolean("sponsorStatistics",it)}
            if(value.showStatistics){
                val totals=controller.statistics.totals
                val seconds=totals.seconds.toLong()
                Text("${totals.count} bestätigte Sprünge · ${seconds/3600} Std. ${(seconds%3600)/60} Min. ${seconds%60} Sek. übersprungen")
                SponsorCategory.entries.filter {(totals.categories[it.key]?:0)>0}.forEach {Text("${it.label}: ${totals.categories[it.key]}",style=MaterialTheme.typography.bodySmall)}
                Text("Nur lokal; überlappende Abschnitte zählen als ein Sprung. Rückgängig korrigiert die Statistik.",style=MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick=controller::resetStatistics){Text("SponsorBlock-Statistiken zurücksetzen")}
            TextButton(onClick=controller::clearCache){Text("SponsorBlock-Cache leeren")}
            Text("Verwendet SponsorBlock-Daten von sponsor.ajay.app unter CC BY-NC-SA 4.0. Gefilterte lokale Daten, keine Einreichungen, Bewertungen oder Aufrufstatistik an den Dienst.",style=MaterialTheme.typography.bodySmall)
            TextButton(onClick={activity.external("https://sponsor.ajay.app/")}){Text("Datenquelle: SponsorBlock")}
            TextButton(onClick={activity.external("https://creativecommons.org/licenses/by-nc-sa/4.0/")}){Text("Datenlizenz: CC BY-NC-SA 4.0")}
        }
    }
    if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("SponsorBlock aktivieren?")},text={Text("Weiktube fragt Community-Segmente bei sponsor.ajay.app ab. Übertragen werden nur vier Zeichen des SHA-256-Hashes der Video-ID und die Kategorien. Das verschleiert die Video-ID, ist aber keine Anonymitätsgarantie; der Dienst sieht deine IP-Adresse. Keine Google-Cookies, Tokens, Bewertungen oder Wiedergabestatistiken werden übertragen. Sponsor, Eigenwerbung und Like-/Abo-Aufrufe werden zunächst automatisch übersprungen. Daten: CC BY-NC-SA 4.0.")},
        confirmButton={TextButton(onClick={model.boolean("sponsorEnabled",true);confirm=false}){Text("Aktivieren")}},
        dismissButton={TextButton(onClick={confirm=false}){Text("Abbrechen")}})
}
@Composable
private fun SponsorSwitch(label:String,checked:Boolean,change:(Boolean)->Unit){
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(label,Modifier.weight(1f));Switch(checked,change)}
}
