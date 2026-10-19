package com.fabio.eagleeyes.funcionalidades.historico

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.fabio.eagleeyes.R

class HistoricoAdapter(private val lista: List<HistoricoUso>) :
    RecyclerView.Adapter<HistoricoAdapter.HistoricoViewHolder>() {

    private data class AppPresentation(val name: String, val icon: Drawable?)
    private val apps = mutableMapOf<String, AppPresentation>()

    class HistoricoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtNomeApp: TextView = view.findViewById(R.id.txtDadosBrutos)
        val txtDetalhes: TextView = view.findViewById(R.id.txtTimeStamp)
        val icon: ImageView = view.findViewById(R.id.imgHistoricoApp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoricoViewHolder =
        HistoricoViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_historico, parent, false))

    override fun onBindViewHolder(holder: HistoricoViewHolder, position: Int) {
        val history = lista[position]
        val app = apps.getOrPut(history.nomeApp) {
            val pm = holder.itemView.context.packageManager
            try {
                @Suppress("DEPRECATION")
                val info = pm.getApplicationInfo(history.nomeApp, 0)
                val label = pm.getApplicationLabel(info).toString()
                AppPresentation(
                    if (label.isBlank() || label == history.nomeApp) friendlyName(history.nomeApp) else label,
                    pm.getApplicationIcon(info)
                )
            } catch (_: PackageManager.NameNotFoundException) {
                AppPresentation(friendlyName(history.nomeApp), null)
            } catch (_: SecurityException) {
                AppPresentation(friendlyName(history.nomeApp), null)
            }
        }
        holder.txtNomeApp.text = app.name
        if (app.icon != null) holder.icon.setImageDrawable(app.icon)
        else holder.icon.setImageResource(R.drawable.ic_app_placeholder)

        val parts = history.data.split('-')
        val date = if (parts.size == 3) "${parts[2]}/${parts[1]}/${parts[0]}" else history.data
        val minutes = history.tempoGasto
        val time = when {
            minutes < 60 -> "$minutes min"
            minutes % 60 == 0L -> "${minutes / 60} h"
            else -> "${minutes / 60} h ${minutes % 60} min"
        }
        holder.txtDetalhes.text = "$date · $time de uso"
    }

    // Apenas apresentação: o identificador original continua no banco.
    private fun friendlyName(value: String): String {
        val known = mapOf(
            "com.fabio.eagleeyes" to "EagleEyes",
            "com.example.chatbox" to "Chatbox",
            "com.google.android.apps.nexuslauncher" to "Pixel Launcher",
            "com.android.launcher3" to "Tela inicial",
            "com.instagram.android" to "Instagram",
            "com.whatsapp" to "WhatsApp",
            "com.google.android.youtube" to "YouTube",
            "com.android.chrome" to "Chrome",
            "com.zhiliaoapp.musically" to "TikTok",
            "com.spotify.music" to "Spotify"
        )
        known[value]?.let { return it }
        if (!value.contains('.')) return value.ifBlank { "Aplicativo" }
        val name = value.substringAfterLast('.')
            .replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .replace('_', ' ').replace('-', ' ').trim()
        return name.replaceFirstChar { it.titlecase() }.ifBlank { "Aplicativo" }
    }

    override fun getItemCount() = lista.size
}
