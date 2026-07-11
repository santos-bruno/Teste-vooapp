package com.vooapp.birdflight.game

import android.graphics.Color

/**
 * Tipo de pássaro jogável. Além da paleta de cores, cada um tem pequenas
 * diferenças de desempenho (força de subida e velocidade).
 */
data class BirdType(
    val id: String,
    val name: String,
    val body: Int,
    val wing: Int,
    val edge: Int,
    val head: Int,
    val beak: Int,
    val liftMul: Float,
    val speedMul: Float,
) {
    companion object {
        val ALL = listOf(
            BirdType(
                id = "aguia", name = "Águia",
                body = Color.rgb(107, 74, 43), wing = Color.rgb(125, 88, 54),
                edge = Color.rgb(63, 44, 24), head = Color.rgb(239, 228, 200),
                beak = Color.rgb(242, 176, 30), liftMul = 1.15f, speedMul = 0.95f,
            ),
            BirdType(
                id = "falcao", name = "Falcão",
                body = Color.rgb(70, 87, 104), wing = Color.rgb(91, 109, 126),
                edge = Color.rgb(47, 59, 69), head = Color.rgb(159, 179, 195),
                beak = Color.rgb(242, 192, 30), liftMul = 0.95f, speedMul = 1.2f,
            ),
            BirdType(
                id = "papagaio", name = "Papagaio",
                body = Color.rgb(47, 158, 68), wing = Color.rgb(64, 192, 87),
                edge = Color.rgb(35, 122, 52), head = Color.rgb(224, 49, 49),
                beak = Color.rgb(240, 140, 0), liftMul = 1.05f, speedMul = 1.0f,
            ),
            BirdType(
                id = "arara", name = "Arara",
                body = Color.rgb(25, 113, 194), wing = Color.rgb(34, 139, 230),
                edge = Color.rgb(21, 90, 150), head = Color.rgb(245, 159, 0),
                beak = Color.rgb(43, 43, 43), liftMul = 1.0f, speedMul = 1.05f,
            ),
        )
    }
}
