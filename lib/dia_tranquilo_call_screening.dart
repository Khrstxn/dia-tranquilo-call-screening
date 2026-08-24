import 'package:flutter/services.dart';

class DiaTranquiloCallScreening {
  static const MethodChannel _channel =
      MethodChannel('com.diatranquilo.callscreening/methods');

  static Future<bool> estaDisponivel() async {
    final resultado =
        await _channel.invokeMethod<bool>('estaDisponivel');

    return resultado ?? false;
  }

  static Future<bool> possuiAutorizacao() async {
    final resultado =
        await _channel.invokeMethod<bool>('possuiAutorizacao');

    return resultado ?? false;
  }

  static Future<bool> solicitarAutorizacao() async {
    final resultado =
        await _channel.invokeMethod<bool>('solicitarAutorizacao');

    return resultado ?? false;
  }

 static Future<bool> configurarBloqueioDesconhecidos({
  required bool ativo,
}) async {
  final resultado = await _channel.invokeMethod<bool>(
    'configurarBloqueioDesconhecidos',
    {
      'ativo': ativo,
    },
  );

  return resultado ?? false;
}
  static Future<bool> configurarBloqueioPorHorario({
    required bool ativo,
    required int inicioMinutos,
    required int terminoMinutos,
  }) async {
    if (inicioMinutos < 0 || inicioMinutos > 1439) {
      throw ArgumentError.value(
        inicioMinutos,
        'inicioMinutos',
        'Deve estar entre 0 e 1439.',
      );
    }

    if (terminoMinutos < 0 || terminoMinutos > 1439) {
      throw ArgumentError.value(
        terminoMinutos,
        'terminoMinutos',
        'Deve estar entre 0 e 1439.',
      );
    }

    final resultado = await _channel.invokeMethod<bool>(
      'configurarBloqueioPorHorario',
      {
        'ativo': ativo,
        'inicioMinutos': inicioMinutos,
        'terminoMinutos': terminoMinutos,
      },
    );

    return resultado ?? false;
  }

  static Future<Map<String, dynamic>> obterConfiguracaoBloqueioPorHorario() async {
  final resultado = await _channel.invokeMapMethod<String, dynamic>(
    'obterConfiguracaoBloqueioPorHorario',
  );

  return resultado ?? <String, dynamic>{};
}
}
