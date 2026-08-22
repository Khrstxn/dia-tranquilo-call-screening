package com.diatranquilo.callscreening

import android.telecom.Call
import android.telecom.CallScreeningService
import java.time.LocalTime

class DiaTranquiloCallScreeningService : CallScreeningService() {

    companion object {
        const val PREFS_NAME = "dia_tranquilo_call_screening"

        const val KEY_BLOQUEIO_HORARIO_ATIVO = "bloqueioHorarioAtivo"
        const val KEY_INICIO_MINUTOS = "inicioBloqueioMinutos"
        const val KEY_TERMINO_MINUTOS = "terminoBloqueioMinutos"
    }

    override fun onScreenCall(callDetails: Call.Details) {
        if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) {
            return
        }

        try {
            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

            val bloqueioAtivo =
                prefs.getBoolean(KEY_BLOQUEIO_HORARIO_ATIVO, false)

            val inicioMinutos =
                prefs.getInt(KEY_INICIO_MINUTOS, -1)

            val terminoMinutos =
                prefs.getInt(KEY_TERMINO_MINUTOS, -1)

            val deveBloquear = deveBloquearAgora(
                bloqueioAtivo = bloqueioAtivo,
                inicioMinutos = inicioMinutos,
                terminoMinutos = terminoMinutos,
            )

            responderChamada(
                callDetails = callDetails,
                bloquear = deveBloquear,
            )
        } catch (_: Exception) {
            responderChamada(
                callDetails = callDetails,
                bloquear = false,
            )
        }
    }

    private fun deveBloquearAgora(
        bloqueioAtivo: Boolean,
        inicioMinutos: Int,
        terminoMinutos: Int,
    ): Boolean {
        if (!bloqueioAtivo) {
            return false
        }

        if (inicioMinutos !in 0..1439 || terminoMinutos !in 0..1439) {
            return false
        }

        if (inicioMinutos == terminoMinutos) {
            return false
        }

        val agora = LocalTime.now()
        val atualMinutos = agora.hour * 60 + agora.minute

        return if (inicioMinutos < terminoMinutos) {
            atualMinutos >= inicioMinutos &&
                atualMinutos < terminoMinutos
        } else {
            atualMinutos >= inicioMinutos ||
                atualMinutos < terminoMinutos
        }
    }

    private fun responderChamada(
        callDetails: Call.Details,
        bloquear: Boolean,
    ) {
        val resposta = CallResponse.Builder()
            .setDisallowCall(bloquear)
            .setRejectCall(bloquear)
            .build()

        respondToCall(callDetails, resposta)
    }
}
