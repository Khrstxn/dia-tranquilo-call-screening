package com.diatranquilo.callscreening

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.PluginRegistry

class DiaTranquiloCallScreeningPlugin :
    FlutterPlugin,
    MethodChannel.MethodCallHandler,
    ActivityAware,
    PluginRegistry.ActivityResultListener {

    companion object {
        private const val CHANNEL_NAME =
            "com.diatranquilo.callscreening/methods"

        private const val REQUEST_CODE_CALL_SCREENING_ROLE = 43129
    }

    private var channel: MethodChannel? = null
    private var applicationContext: Context? = null

    private var activity: Activity? = null
    private var activityBinding: ActivityPluginBinding? = null

    private var pendingAuthorizationResult: MethodChannel.Result? = null

    override fun onAttachedToEngine(
        binding: FlutterPlugin.FlutterPluginBinding,
    ) {
        applicationContext = binding.applicationContext

        channel = MethodChannel(
            binding.binaryMessenger,
            CHANNEL_NAME,
        ).also {
            it.setMethodCallHandler(this)
        }
    }

    override fun onDetachedFromEngine(
        binding: FlutterPlugin.FlutterPluginBinding,
    ) {
        channel?.setMethodCallHandler(null)
        channel = null

        activityBinding?.removeActivityResultListener(this)
        activityBinding = null
        activity = null

        pendingAuthorizationResult?.error(
            "ENGINE_DETACHED",
            "O plugin foi desconectado antes de concluir a autorização.",
            null,
        )
        pendingAuthorizationResult = null

        applicationContext = null
    }

    override fun onMethodCall(
        call: MethodCall,
        result: MethodChannel.Result,
    ) {
        when (call.method) {
            "estaDisponivel" -> {
                result.success(estaDisponivel())
            }

            "possuiAutorizacao" -> {
                result.success(possuiAutorizacao())
            }

            "solicitarAutorizacao" -> {
                solicitarAutorizacao(result)
            }

            "configurarBloqueioDesconhecidos" -> {
                configurarBloqueioDesconhecidos(
                    call = call,
                    result = result,
                )
            }

            "obterConfiguracaoBloqueioDesconhecidos" -> {
                obterConfiguracaoBloqueioDesconhecidos(result)
            }

            "configurarBloqueioPorHorario" -> {
                configurarBloqueioPorHorario(
                    call = call,
                    result = result,
                )
            }

            "obterConfiguracaoBloqueioPorHorario" -> {
                obterConfiguracaoBloqueioPorHorario(result)
            }

            else -> {
                result.notImplemented()
            }
        }
    }

    private fun obterRoleManager(): RoleManager? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return null
        }

        val context = applicationContext ?: return null

        return context.getSystemService(RoleManager::class.java)
    }

    private fun estaDisponivel(): Boolean {
        val roleManager = obterRoleManager() ?: return false

        return roleManager.isRoleAvailable(
            RoleManager.ROLE_CALL_SCREENING,
        )
    }

    private fun possuiAutorizacao(): Boolean {
        val roleManager = obterRoleManager() ?: return false

        if (!roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            return false
        }

        return roleManager.isRoleHeld(
            RoleManager.ROLE_CALL_SCREENING,
        )
    }

    @Suppress("DEPRECATION")
    private fun solicitarAutorizacao(
        result: MethodChannel.Result,
    ) {
        val roleManager = obterRoleManager()

        if (roleManager == null) {
            result.success(false)
            return
        }

        if (!roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            result.success(false)
            return
        }

        if (roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
            result.success(true)
            return
        }

        if (pendingAuthorizationResult != null) {
            result.error(
                "AUTHORIZATION_IN_PROGRESS",
                "Já existe uma solicitação de autorização em andamento.",
                null,
            )
            return
        }

        val currentActivity = activity

        if (currentActivity == null) {
            result.error(
                "NO_ACTIVITY",
                "Não há uma Activity disponível para solicitar a autorização.",
                null,
            )
            return
        }

        try {
            val intent = roleManager.createRequestRoleIntent(
                RoleManager.ROLE_CALL_SCREENING,
            )

            pendingAuthorizationResult = result

            currentActivity.startActivityForResult(
                intent,
                REQUEST_CODE_CALL_SCREENING_ROLE,
            )
        } catch (exception: Exception) {
            pendingAuthorizationResult = null

            result.error(
                "AUTHORIZATION_REQUEST_FAILED",
                exception.message
                    ?: "Não foi possível solicitar a autorização.",
                null,
            )
        }
    }

    private fun configurarBloqueioDesconhecidos(
        call: MethodCall,
        result: MethodChannel.Result,
    ) {
        val context = applicationContext

        if (context == null) {
            result.error(
                "NO_CONTEXT",
                "O contexto Android não está disponível.",
                null,
            )
            return
        }

        val ativo = call.argument<Boolean>("ativo")

        if (ativo == null) {
            result.error(
                "INVALID_ARGUMENTS",
                "ativo é obrigatório.",
                null,
            )
            return
        }

        val prefs = context.getSharedPreferences(
            DiaTranquiloCallScreeningService.PREFS_NAME,
            Context.MODE_PRIVATE,
        )

        val salvo = prefs.edit()
            .putBoolean(
                DiaTranquiloCallScreeningService.KEY_BLOQUEIO_DESCONHECIDOS_ATIVO,
                ativo,
            )
            .commit()

        result.success(salvo)
    }

    private fun obterConfiguracaoBloqueioDesconhecidos(
        result: MethodChannel.Result,
    ) {
        val context = applicationContext

        if (context == null) {
            result.error(
                "NO_CONTEXT",
                "O contexto Android não está disponível.",
                null,
            )
            return
        }

        val prefs = context.getSharedPreferences(
            DiaTranquiloCallScreeningService.PREFS_NAME,
            Context.MODE_PRIVATE,
        )

        val ativo = prefs.getBoolean(
            DiaTranquiloCallScreeningService.KEY_BLOQUEIO_DESCONHECIDOS_ATIVO,
            false,
        )

        result.success(ativo)
    }

    private fun configurarBloqueioPorHorario(
        call: MethodCall,
        result: MethodChannel.Result,
    ) {
        val context = applicationContext

        if (context == null) {
            result.error(
                "NO_CONTEXT",
                "O contexto Android não está disponível.",
                null,
            )
            return
        }

        val ativo = call.argument<Boolean>("ativo")

        val inicioMinutos =
            call.argument<Number>("inicioMinutos")?.toInt()

        val terminoMinutos =
            call.argument<Number>("terminoMinutos")?.toInt()

        if (
            ativo == null ||
            inicioMinutos == null ||
            terminoMinutos == null
        ) {
            result.error(
                "INVALID_ARGUMENTS",
                "ativo, inicioMinutos e terminoMinutos são obrigatórios.",
                null,
            )
            return
        }

        if (inicioMinutos !in 0..1439) {
            result.error(
                "INVALID_START_TIME",
                "inicioMinutos deve estar entre 0 e 1439.",
                null,
            )
            return
        }

        if (terminoMinutos !in 0..1439) {
            result.error(
                "INVALID_END_TIME",
                "terminoMinutos deve estar entre 0 e 1439.",
                null,
            )
            return
        }

        val prefs = context.getSharedPreferences(
            DiaTranquiloCallScreeningService.PREFS_NAME,
            Context.MODE_PRIVATE,
        )

        val salvo = prefs.edit()
            .putBoolean(
                DiaTranquiloCallScreeningService.KEY_BLOQUEIO_HORARIO_ATIVO,
                ativo,
            )
            .putInt(
                DiaTranquiloCallScreeningService.KEY_INICIO_MINUTOS,
                inicioMinutos,
            )
            .putInt(
                DiaTranquiloCallScreeningService.KEY_TERMINO_MINUTOS,
                terminoMinutos,
            )
            .commit()

        result.success(salvo)
    }

    private fun obterConfiguracaoBloqueioPorHorario(
        result: MethodChannel.Result,
    ) {
        val context = applicationContext

        if (context == null) {
            result.error(
                "NO_CONTEXT",
                "O contexto Android não está disponível.",
                null,
            )
            return
        }

        val prefs = context.getSharedPreferences(
            DiaTranquiloCallScreeningService.PREFS_NAME,
            Context.MODE_PRIVATE,
        )

        val configuracao = mapOf<String, Any>(
            "ativo" to prefs.getBoolean(
                DiaTranquiloCallScreeningService.KEY_BLOQUEIO_HORARIO_ATIVO,
                false,
            ),
            "inicioMinutos" to prefs.getInt(
                DiaTranquiloCallScreeningService.KEY_INICIO_MINUTOS,
                -1,
            ),
            "terminoMinutos" to prefs.getInt(
                DiaTranquiloCallScreeningService.KEY_TERMINO_MINUTOS,
                -1,
            ),
        )

        result.success(configuracao)
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ): Boolean {
        if (requestCode != REQUEST_CODE_CALL_SCREENING_ROLE) {
            return false
        }

        val pendingResult = pendingAuthorizationResult
        pendingAuthorizationResult = null

        if (pendingResult != null) {
            val autorizado = possuiAutorizacao()

            pendingResult.success(autorizado)
        }

        return true
    }

    override fun onAttachedToActivity(
        binding: ActivityPluginBinding,
    ) {
        anexarActivity(binding)
    }

    override fun onReattachedToActivityForConfigChanges(
        binding: ActivityPluginBinding,
    ) {
        anexarActivity(binding)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        activityBinding?.removeActivityResultListener(this)

        activityBinding = null
        activity = null
    }

    override fun onDetachedFromActivity() {
        activityBinding?.removeActivityResultListener(this)

        activityBinding = null
        activity = null

        pendingAuthorizationResult?.error(
            "ACTIVITY_DETACHED",
            "A Activity foi desconectada antes de concluir a autorização.",
            null,
        )
        pendingAuthorizationResult = null
    }

    private fun anexarActivity(
        binding: ActivityPluginBinding,
    ) {
        activityBinding?.removeActivityResultListener(this)

        activityBinding = binding
        activity = binding.activity

        binding.addActivityResultListener(this)
    }
}
