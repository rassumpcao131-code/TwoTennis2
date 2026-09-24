package com.example.twotennis;

import android.content.Context;
import android.content.SharedPreferences;

final class ConfiguracoesJogo {

    static final int TOTAL_EVENTOS = 11;
    static final int EVENTO_DUPLICACAO = 0;
    static final int EVENTO_REVERSAO = 1;
    static final int EVENTO_TURBO = 2;
    static final int EVENTO_CONGELAMENTO = 3;
    static final int EVENTO_FANTASMA = 4;
    static final int EVENTO_PORTAIS = 5;
    static final int EVENTO_ORBITA = 6;
    static final int EVENTO_CADEIA = 7;
    static final int EVENTO_ELETRICO = 8;
    static final int EVENTO_FUSAO = 9;
    static final int EVENTO_SUPERNOVA = 10;

    private static final String ARQUIVO = "configuracoes_partida";
    private static final String CHAVE_PONTOS = "pontos_maximos";
    private static final String CHAVE_VELOCIDADE = "nivel_velocidade";
    private static final String CHAVE_MODO_RAPIDO = "modo_rapido";
    private static final String CHAVE_CURVAS = "curvas_atracao";
    private static final String CHAVE_VARIACOES = "variacoes_velocidade";
    private static final String CHAVE_EVENTO = "evento_";

    private ConfiguracoesJogo() {
    }

    static int obterPontosMaximos(Context context) {
        return preferencias(context).getInt(CHAVE_PONTOS, 10);
    }

    static int obterNivelVelocidade(Context context) {
        return preferencias(context).getInt(CHAVE_VELOCIDADE, 1);
    }

    static float obterMultiplicadorVelocidade(Context context) {
        switch (obterNivelVelocidade(context)) {
            case 0:
                return 0.82f;
            case 2:
                return 1.22f;
            case 3:
                return 1.48f;
            default:
                return 1f;
        }
    }

    static String obterNomeVelocidade(int nivel) {
        switch (nivel) {
            case 0:
                return "CALMA  •  x0.82";
            case 2:
                return "RÁPIDA  •  x1.22";
            case 3:
                return "INSANA  •  x1.48";
            default:
                return "NORMAL  •  x1.00";
        }
    }

    static boolean obterModoRapido(Context context) {
        return preferencias(context).getBoolean(CHAVE_MODO_RAPIDO, false);
    }

    static boolean obterCurvasAtivas(Context context) {
        return preferencias(context).getBoolean(CHAVE_CURVAS, true);
    }

    static boolean obterVariacoesAtivas(Context context) {
        return preferencias(context).getBoolean(CHAVE_VARIACOES, true);
    }

    static boolean[] obterEventosAtivos(Context context) {
        SharedPreferences preferencias = preferencias(context);
        boolean[] eventos = new boolean[TOTAL_EVENTOS];

        for (int i = 0; i < eventos.length; i++) {
            eventos[i] = preferencias.getBoolean(CHAVE_EVENTO + i, true);
        }

        return eventos;
    }

    static void salvar(
            Context context,
            int pontosMaximos,
            int nivelVelocidade,
            boolean modoRapido,
            boolean curvasAtivas,
            boolean variacoesAtivas,
            boolean[] eventosAtivos
    ) {
        SharedPreferences.Editor editor = preferencias(context).edit()
                .putInt(CHAVE_PONTOS, Math.max(5, Math.min(30, pontosMaximos)))
                .putInt(CHAVE_VELOCIDADE, Math.max(0, Math.min(3, nivelVelocidade)))
                .putBoolean(CHAVE_MODO_RAPIDO, modoRapido)
                .putBoolean(CHAVE_CURVAS, curvasAtivas)
                .putBoolean(CHAVE_VARIACOES, variacoesAtivas);

        for (int i = 0; i < TOTAL_EVENTOS; i++) {
            boolean ativo = i < eventosAtivos.length && eventosAtivos[i];
            editor.putBoolean(CHAVE_EVENTO + i, ativo);
        }

        editor.apply();
    }

    private static SharedPreferences preferencias(Context context) {
        return context.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE);
    }
}
