package com.diatranquilo.callscreening

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import java.time.LocalTime

class DiaTranquiloCallScreeningService : CallScreeningService() {

    companion object {
        private const val TAG = "DiaTranquiloScreening"

        const val PREFS_NAME = "dia_tranquilo_call_screening"

        const val KEY_BLOQUEIO_DESCONHECIDOS_ATIVO =
            "bloqueioDesconhecidosAtivo"

        const val KEY_BLOQUEIO_SPAM_ATIVO =
            "bloqueioSpamAtivo"

        const val KEY_BLOQUEIO_HORARIO_ATIVO =
            "bloqueioHorarioAtivo"

        const val KEY_INICIO_MINUTOS =
            "inicioBloqueioMinutos"

        const val KEY_TERMINO_MINUTOS =
            "terminoBloqueioMinutos"

        /*
         * Valores definidos pela API Android para
         * callerNumberVerificationStatus.
         *
         * 0 = NOT_VERIFIED
         * 1 = PASSED
         * 2 = FAILED
         *
         * Evitamos referenciar diretamente as constantes de
         * android.telecom.Connection porque elas só existem
         * a partir da API 30, enquanto o Dia Tranquilo
         * suporta Android API 29+.
         */
        private const val VERIFICATION_STATUS_NOT_VERIFIED = 0
        private const val VERIFICATION_STATUS_FAILED = 2
    }

    override fun onScreenCall(callDetails: Call.Details) {
        Log.d(
            TAG,
            "onScreenCall chamado. direction=${callDetails.callDirection}",
        )

        if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) {
            Log.d(
                TAG,
                "Chamada ignorada: não é chamada recebida.",
            )
            return
        }

        try {
            val prefs = getSharedPreferences(
                PREFS_NAME,
                MODE_PRIVATE,
            )

            val bloqueioDesconhecidosAtivo =
                prefs.getBoolean(
                    KEY_BLOQUEIO_DESCONHECIDOS_ATIVO,
                    false,
                )

            val bloqueioSpamAtivo =
                prefs.getBoolean(
                    KEY_BLOQUEIO_SPAM_ATIVO,
                    false,
                )

            val bloqueioHorarioAtivo =
                prefs.getBoolean(
                    KEY_BLOQUEIO_HORARIO_ATIVO,
                    false,
                )

            val inicioMinutos =
                prefs.getInt(
                    KEY_INICIO_MINUTOS,
                    -1,
                )

            val terminoMinutos =
                prefs.getInt(
                    KEY_TERMINO_MINUTOS,
                    -1,
                )

            Log.d(
                TAG,
                "Configuracao: " +
                    "desconhecidos=$bloqueioDesconhecidosAtivo, " +
                    "spam=$bloqueioSpamAtivo, " +
                    "horario=$bloqueioHorarioAtivo, " +
                    "inicio=$inicioMinutos, " +
                    "termino=$terminoMinutos",
            )

            val dentroDoHorarioBloqueado =
                deveBloquearAgora(
                    bloqueioAtivo = bloqueioHorarioAtivo,
                    inicioMinutos = inicioMinutos,
                    terminoMinutos = terminoMinutos,
                )

            val estaNosContatos =
                numeroEstaNosContatos(callDetails)

            val statusVerificacao =
                obterStatusVerificacao(callDetails)

            val numeroSuspeito =
                statusVerificacao ==
                    VERIFICATION_STATUS_FAILED

            Log.d(
                TAG,
                "Analise: " +
                    "dentroDoHorario=$dentroDoHorarioBloqueado, " +
                    "estaNosContatos=$estaNosContatos, " +
                    "statusVerificacao=$statusVerificacao, " +
                    "numeroSuspeito=$numeroSuspeito",
            )

            val deveBloquear =
                dentroDoHorarioBloqueado ||
                    (
                        bloqueioDesconhecidosAtivo &&
                            !estaNosContatos
                    ) ||
                    (
                        bloqueioSpamAtivo &&
                            numeroSuspeito
                    )

            Log.d(
                TAG,
                "Decisao final: bloquear=$deveBloquear",
            )

            responderChamada(
                callDetails = callDetails,
                bloquear = deveBloquear,
            )
        } catch (exception: Exception) {
            Log.e(
                TAG,
                "Erro durante onScreenCall",
                exception,
            )

            responderChamada(
                callDetails = callDetails,
                bloquear = false,
            )
        }
    }

    private fun obterStatusVerificacao(
        callDetails: Call.Details,
    ): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.d(
                TAG,
                "Verificacao de numero indisponivel nesta versao do Android.",
            )

            return VERIFICATION_STATUS_NOT_VERIFIED
        }

        val status =
            callDetails.callerNumberVerificationStatus

        Log.d(
            TAG,
            "Status de verificacao do numero=$status",
        )

        return status
    }

    private fun numeroEstaNosContatos(
        callDetails: Call.Details,
    ): Boolean {
        val permissaoContatos =
            checkSelfPermission(
                Manifest.permission.READ_CONTACTS,
            ) == PackageManager.PERMISSION_GRANTED

        Log.d(
            TAG,
            "READ_CONTACTS concedida=$permissaoContatos",
        )

        if (!permissaoContatos) {
            return false
        }

        val handle = callDetails.handle

        if (handle == null) {
            Log.d(
                TAG,
                "Chamada sem handle.",
            )
            return false
        }

        if (handle.scheme != "tel") {
            Log.d(
                TAG,
                "Handle nao e tel: ${handle.scheme}",
            )
            return false
        }

        val numero = handle.schemeSpecificPart
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        if (numero == null) {
            Log.d(
                TAG,
                "Numero ausente ou vazio.",
            )
            return false
        }

        Log.d(
            TAG,
            "Consultando contatos para numero=$numero",
        )

        val lookupUri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(numero),
        )

        val encontrado =
            contentResolver.query(
                lookupUri,
                arrayOf(ContactsContract.PhoneLookup._ID),
                null,
                null,
                null,
            )?.use { cursor ->
                cursor.moveToFirst()
            } ?: false

        Log.d(
            TAG,
            "Numero encontrado nos contatos=$encontrado",
        )

        return encontrado
    }

    private fun deveBloquearAgora(
        bloqueioAtivo: Boolean,
        inicioMinutos: Int,
        terminoMinutos: Int,
    ): Boolean {
        if (!bloqueioAtivo) {
            Log.d(
                TAG,
                "Bloqueio por horario esta desligado.",
            )
            return false
        }

        if (
            inicioMinutos !in 0..1439 ||
            terminoMinutos !in 0..1439
        ) {
            Log.d(
                TAG,
                "Horario invalido: inicio=$inicioMinutos termino=$terminoMinutos",
            )
            return false
        }

        if (inicioMinutos == terminoMinutos) {
            Log.d(
                TAG,
                "Inicio e termino iguais; bloqueio por horario ignorado.",
            )
            return false
        }

        val agora = LocalTime.now()

        val atualMinutos =
            agora.hour * 60 + agora.minute

        val dentroDoHorario =
            if (inicioMinutos < terminoMinutos) {
                atualMinutos >= inicioMinutos &&
                    atualMinutos < terminoMinutos
            } else {
                atualMinutos >= inicioMinutos ||
                    atualMinutos < terminoMinutos
            }

        Log.d(
            TAG,
            "Horario atual=$atualMinutos, " +
                "inicio=$inicioMinutos, " +
                "termino=$terminoMinutos, " +
                "dentro=$dentroDoHorario",
        )

        return dentroDoHorario
    }

    private fun responderChamada(
        callDetails: Call.Details,
        bloquear: Boolean,
    ) {
        Log.d(
            TAG,
            "respondToCall bloquear=$bloquear",
        )

        val resposta = CallResponse.Builder()
            .setDisallowCall(bloquear)
            .setRejectCall(bloquear)
            .build()

        respondToCall(
            callDetails,
            resposta,
        )
    }
}
