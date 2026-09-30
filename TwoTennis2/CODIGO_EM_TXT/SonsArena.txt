package com.example.twotennis;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.SystemClock;
import java.util.HashSet;
import java.util.Set;

/** Clips curtos carregados uma vez, com limite de vozes e de repeticao. */
final class SonsArena {
    private final SoundPool pool;
    private final int parede, raquete, colisao, ponto, evento, gelo, portal, viva, trombeta, relogio, vento, carga;
    private final Set<Integer> prontos = new HashSet<>();
    private final long[] ultimo = new long[8];
    private boolean ativos;
    private boolean pausado;
    private boolean liberado;

    SonsArena(Context context) {
        ativos = ConfiguracoesJogo.obterSonsAtivos(context);
        pool = new SoundPool.Builder().setMaxStreams(4)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .build();
        pool.setOnLoadCompleteListener((p, id, status) -> {
            synchronized (prontos) { if (status == 0) prontos.add(id); }
        });
        parede = pool.load(context, R.raw.sfx_parede, 1);
        raquete = pool.load(context, R.raw.sfx_raquete, 1);
        colisao = pool.load(context, R.raw.sfx_colisao, 1);
        ponto = pool.load(context, R.raw.sfx_ponto, 1);
        evento = pool.load(context, R.raw.sfx_evento, 1);
        gelo = pool.load(context, R.raw.sfx_gelo, 1);
        portal = pool.load(context, R.raw.sfx_portal, 1);
        viva = pool.load(context, R.raw.sfx_viva, 1);
        trombeta = pool.load(context, R.raw.sfx_trombeta, 1);
        relogio = pool.load(context, R.raw.sfx_relogio, 1);
        vento = pool.load(context, R.raw.sfx_vento, 1);
        carga = pool.load(context, R.raw.sfx_carga, 1);
    }

    void setAtivos(boolean valor) { ativos = valor; }
    void pausar() { pausado = true; if (!liberado) pool.autoPause(); }
    void retomar() { pausado = false; } // Efeitos antigos nao tocam novamente.
    void liberar() { if (!liberado) { liberado = true; pool.release(); } }
    void parede(float x) { tocar(parede, 0, x, 0.28f, 1f, 60); }
    void raquete(boolean cima, float x) { tocar(raquete, 1, x, 0.58f, cima ? 1.12f : 0.90f, 40); }
    void colisao(float x) { tocar(colisao, 2, x, 0.45f, 1f, 80); }
    void ponto() { tocar(ponto, 3, 0.5f, 0.60f, 1f, 200); }
    void evento(String nome) {
        int id = nome.contains("RELÓGIO") ? relogio
                : nome.contains("VENTO") ? vento
                : nome.contains("CARGA") || nome.contains("METEOROS") || nome.contains("RICOCHETE") ? carga
                : nome.contains("VIVA") || nome.contains("SERPENTE") || nome.contains("CAÇA") ? viva
                : nome.contains("CONGELAMENTO") || nome.contains("LENTA") ? gelo
                : nome.contains("PORTAIS") || nome.contains("SALTO") || nome.contains("TROCA")
                  || nome.contains("REBOBINAR") || nome.contains("ATRAÇÃO") || nome.contains("ÓRBITA") ? portal : evento;
        tocar(id, 4, 0.5f, 0.48f, 1f, 250);
    }
    void trombeta() {
        if(pausado || liberado) return;
        synchronized(prontos) { if(!prontos.contains(trombeta)) return; }
        pool.play(trombeta,0.8f,0.8f,10,0,1f);
    }
    private void tocar(int id, int canal, float x, float volume, float rate, long intervalo) {
        if (!ativos || pausado || liberado) return;
        synchronized (prontos) { if (!prontos.contains(id)) return; }
        long agora = SystemClock.elapsedRealtime();
        if (agora - ultimo[canal] < intervalo) return;
        ultimo[canal] = agora;
        x = Math.max(0f, Math.min(1f, x));
        pool.play(id, volume * (1f - 0.45f * x), volume * (0.55f + 0.45f * x), 1, 0, rate);
    }
}
