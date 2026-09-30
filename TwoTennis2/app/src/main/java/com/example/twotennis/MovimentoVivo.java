package com.example.twotennis;

import java.util.Random;

/** Controle temporario independente do Android, com unidades por segundo. */
final class MovimentoVivo {
    private float restante;
    private float proximaDecisao;
    private float curva;
    private float velocidadeAlvo;
    float vx;
    float vy;

    void iniciar(float vxInicial, float vyInicial) {
        restante = 10f;
        proximaDecisao = 0;
        vx = vxInicial;
        vy = vyInicial;
    }

    boolean ativo() { return restante > 0f; }
    float segundosRestantes() { return restante; }
    void cancelar() { restante = 0; }

    void atualizar(float dt, float escala, float vxAtual, float vyAtual, Random random) {
        if (!ativo()) return;
        dt = Math.max(0f, dt);
        restante = Math.max(0f, restante - dt);
        vx = vxAtual;
        vy = vyAtual;
        if (!ativo()) return;
        proximaDecisao -= dt;
        if (proximaDecisao <= 0f) {
            // Retas cardinais, curvas fortes e direcoes totalmente livres.
            int comportamento = random.nextInt(6);
            float angulo = comportamento < 2
                    ? random.nextInt(4) * (float) Math.PI / 2f
                    : random.nextFloat() * (float) Math.PI * 2f;
            float velocidade = (160f + random.nextFloat() * 820f) * escala;
            velocidadeAlvo = (130f + random.nextFloat() * 970f) * escala;
            curva = comportamento < 2 ? 0f
                    : (random.nextFloat() * 2f - 1f) * 8f;
            vx = (float) Math.cos(angulo) * velocidade;
            vy = (float) Math.sin(angulo) * velocidade;
            proximaDecisao = 0.16f + random.nextFloat() * 0.72f;
        }
        float velocidade = (float) Math.hypot(vx, vy);
        float nova = velocidade + (velocidadeAlvo - velocidade) * Math.min(1f, dt * 4f);
        float angulo = (float) Math.atan2(vy, vx) + curva * dt;
        vx = (float) Math.cos(angulo) * nova;
        vy = (float) Math.sin(angulo) * nova;
    }
}
