package com.example.twotennis;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

public class gameView extends SurfaceView
        implements Runnable, SurfaceHolder.Callback {

    private static final int MAX_BOLAS = 5;
    private static final int MAX_PARTICULAS = 140;
    private static final long TEMPO_FRAME_NS = 16_666_667L;

    private final SurfaceHolder holder;
    private final Paint paint;
    private final Paint paintCache;
    private final Random random;
    private final ArrayList<Bola> bolas;
    private final ArrayList<Particula> particulas;
    private final float[] hsvTemporario = new float[3];

    private final boolean modoUmJogador;
    private final boolean modoRapido;
    private final int pontosParaVencer;
    private final float multiplicadorVelocidadeInicial;
    private final boolean[] eventosAtivos;
    private final int[] eventosDisponiveis =
            new int[ConfiguracoesJogo.TOTAL_EVENTOS];
    private final boolean curvasAtivas;
    private final boolean variacoesVelocidadeAtivas;

    public volatile boolean rodando;
    public Thread thread;

    private boolean inicializado;
    private boolean esperandoInicio = true;
    private boolean primeiraRodada = true;

    private int larguraTela;
    private int alturaTela;

    private float densidade;

    // Camadas reaproveitadas pela arena para evitar criar gradientes a cada frame.
    private Shader gradienteFundoCima;
    private Shader gradienteFundoBaixo;
    private Shader nebulosaCimaA;
    private Shader nebulosaCimaB;
    private Shader nebulosaBaixoA;
    private Shader nebulosaBaixoB;
    private Shader brilhoHorizonte;
    private final Path caminhoArena = new Path();
    private final Rect destinoCacheArena = new Rect();
    private Bitmap cacheArena;
    private int nivelVisualCache = -1;
    private boolean cacheArenaIndisponivel;

    private float raqueteCimaX;
    private float raqueteBaixoX;

    private volatile float alvoRaqueteCimaX;
    private volatile float alvoRaqueteBaixoX;

    private float raqueteCimaY;
    private float raqueteBaixoY;
    private float raqueteLargura;
    private float raqueteAltura;

    private float velocidadeRaqueteCima;
    private float velocidadeRaqueteBaixo;

    private int ponteiroCima = -1;
    private int ponteiroBaixo = -1;

    private int pontosCima;
    private int pontosBaixo;

    private float tempoRodada;
    private float tempoParaReiniciar;
    private float tempoProximoEvento = 11f;

    private boolean jogoEncerrado;
    private String vencedor = "";

    private String mensagemEvento = "";
    private float tempoMensagemEvento;

    private float erroIA;
    private float tempoNovoErroIA;

    private float flashImpacto;
    private float tempoAtualizacaoHud;
    private String textoNivelHud = "NÍVEL 1";
    private String textoRitmoHud = "RITMO x1.00";

    // Eventos globais que podem afetar mais de uma bola.
    private float tempoPortal;
    private float portalAX;
    private float portalAY;
    private float portalBX;
    private float portalBY;
    private float raioPortal;

    private Bola bolaEletricaA;
    private Bola bolaEletricaB;
    private float tempoLigacaoEletrica;
    private float tempoFaiscaEletrica;

    private int duplicacoesCadeiaPendentes;
    private float tempoProximaDuplicacaoCadeia;

    private float supernovaX;
    private float supernovaY;
    private float tempoSupernovaCarga;
    private float tempoSupernovaEfeito;
    private boolean supernovaExplodiu;

    public gameView(Context context) {
        this(context, false);
    }

    public gameView(Context context, boolean modoUmJogador) {
        super(context);

        this.modoUmJogador = modoUmJogador;
        modoRapido = ConfiguracoesJogo.obterModoRapido(context);
        pontosParaVencer = ConfiguracoesJogo.obterPontosMaximos(context);
        multiplicadorVelocidadeInicial =
                ConfiguracoesJogo.obterMultiplicadorVelocidade(context);
        eventosAtivos = ConfiguracoesJogo.obterEventosAtivos(context);
        curvasAtivas = ConfiguracoesJogo.obterCurvasAtivas(context);
        variacoesVelocidadeAtivas =
                ConfiguracoesJogo.obterVariacoesAtivas(context);

        holder = getHolder();
        holder.addCallback(this);

        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintCache = new Paint(
                Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG
        );
        random = new Random();
        bolas = new ArrayList<>();
        particulas = new ArrayList<>();

        densidade = getResources().getDisplayMetrics().density;

        setFocusable(true);
        setKeepScreenOn(true);
    }

    // =========================================================
    // LOOP DO JOGO
    // =========================================================

    @Override
    public void run() {
        long tempoAnterior = System.nanoTime();
        long tempoFrameAlvo = modoRapido
                ? 22_222_222L
                : TEMPO_FRAME_NS;

        while (rodando) {
            long inicioFrame = System.nanoTime();

            float deltaTime =
                    (inicioFrame - tempoAnterior) / 1_000_000_000f;

            tempoAnterior = inicioFrame;

            // Evita saltos enormes ao voltar do segundo plano.
            deltaTime = limitar(deltaTime, 0f, 0.033f);

            if (inicializado) {
                atualizar(deltaTime);
                desenhar();
            }

            long duracaoFrame = System.nanoTime() - inicioFrame;
            long tempoRestante = tempoFrameAlvo - duracaoFrame;

            if (tempoRestante > 0) {
                try {
                    long milissegundos = tempoRestante / 1_000_000L;
                    int nanos = (int) (tempoRestante % 1_000_000L);

                    Thread.sleep(milissegundos, nanos);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    public synchronized void iniciar() {
        if (rodando) {
            return;
        }

        rodando = true;
        thread = new Thread(this, "TwoTennisGameLoop");
        thread.start();
    }

    public void parar() {
        Thread threadAtual;

        synchronized (this) {
            rodando = false;
            threadAtual = thread;
        }

        if (threadAtual != null
                && threadAtual != Thread.currentThread()) {

            try {
                threadAtual.join(1000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }

        synchronized (this) {
            if (thread == threadAtual) {
                thread = null;
            }
        }
    }

    // =========================================================
    // SURFACE
    // =========================================================

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        configurarTamanhos(getWidth(), getHeight());
    }

    @Override
    public void surfaceChanged(
            SurfaceHolder surfaceHolder,
            int formato,
            int largura,
            int altura
    ) {
        configurarTamanhos(largura, altura);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        parar();
        liberarCacheArena();
    }

    private void configurarTamanhos(int largura, int altura) {
        if (largura <= 0 || altura <= 0) {
            return;
        }

        larguraTela = largura;
        alturaTela = altura;

        liberarCacheArena();
        cacheArenaIndisponivel = false;
        destinoCacheArena.set(0, 0, larguraTela, alturaTela);
        configurarGradientesArena();

        raqueteLargura = larguraTela * 0.27f;
        raqueteAltura = Math.max(dp(12), alturaTela * 0.018f);

        raqueteCimaY = alturaTela * 0.075f;
        raqueteBaixoY =
                alturaTela - raqueteCimaY - raqueteAltura;

        if (!inicializado) {
            raqueteCimaX =
                    larguraTela / 2f - raqueteLargura / 2f;

            raqueteBaixoX = raqueteCimaX;

            alvoRaqueteCimaX = raqueteCimaX;
            alvoRaqueteBaixoX = raqueteBaixoX;

            prepararRodada(true);
            inicializado = true;
        } else {
            raqueteCimaX = limitarRaquete(raqueteCimaX);
            raqueteBaixoX = limitarRaquete(raqueteBaixoX);

            alvoRaqueteCimaX =
                    limitarRaquete(alvoRaqueteCimaX);

            alvoRaqueteBaixoX =
                    limitarRaquete(alvoRaqueteBaixoX);
        }
    }

    // =========================================================
    // ATUALIZAÇÃO
    // =========================================================

    private void atualizar(float dt) {
        atualizarParticulas(dt);
        atualizarTextoHud(dt);

        if (flashImpacto > 0) {
            flashImpacto = Math.max(0, flashImpacto - dt * 3.8f);
        }

        if (tempoMensagemEvento > 0) {
            tempoMensagemEvento -= dt;
        }

        atualizarRaquetes(dt);

        if (jogoEncerrado) {
            return;
        }

        if (esperandoInicio) {
            if (!primeiraRodada) {
                tempoParaReiniciar -= dt;

                if (tempoParaReiniciar <= 0) {
                    comecarRodada();
                }
            }

            return;
        }

        tempoRodada += dt;
        tempoProximoEvento -= dt;

        if (tempoProximoEvento <= 0) {
            executarEventoAleatorio();
        }

        atualizarEventosGlobais(dt);

        float dificuldade = obterDificuldade();

        for (Bola bola : bolas) {
            atualizarBola(bola, dt, dificuldade);
        }

        integrarMovimentoBolas(dt);
        processarSeparacoesPendentes();

        Iterator<Bola> iterator = bolas.iterator();

        while (iterator.hasNext()) {
            Bola bola = iterator.next();

            if (bola.y + bola.raio < 0) {
                pontosBaixo++;
                iterator.remove();

                if (pontosBaixo >= pontosParaVencer) {
                    encerrarJogo("JOGADOR DE BAIXO VENCEU!");
                    return;
                }
            } else if (bola.y - bola.raio > alturaTela) {
                pontosCima++;
                iterator.remove();

                if (pontosCima >= pontosParaVencer) {
                    encerrarJogo(
                            modoUmJogador
                                    ? "COMPUTADOR VENCEU!"
                                    : "JOGADOR DE CIMA VENCEU!"
                    );
                    return;
                }
            }
        }

        if (bolas.isEmpty()) {
            prepararRodada(false);
        }
    }

    private void atualizarRaquetes(float dt) {
        float cimaAnterior = raqueteCimaX;
        float baixoAnterior = raqueteBaixoX;

        if (modoUmJogador) {
            atualizarIA(dt);
        }

        float velocidadeJogador = dp(1700);

        raqueteCimaX = moverAte(
                raqueteCimaX,
                alvoRaqueteCimaX,
                velocidadeJogador * dt
        );

        raqueteBaixoX = moverAte(
                raqueteBaixoX,
                alvoRaqueteBaixoX,
                velocidadeJogador * dt
        );

        raqueteCimaX = limitarRaquete(raqueteCimaX);
        raqueteBaixoX = limitarRaquete(raqueteBaixoX);

        if (dt > 0) {
            velocidadeRaqueteCima =
                    (raqueteCimaX - cimaAnterior) / dt;

            velocidadeRaqueteBaixo =
                    (raqueteBaixoX - baixoAnterior) / dt;
        }

        velocidadeRaqueteCima = limitar(
                velocidadeRaqueteCima,
                -dp(1500),
                dp(1500)
        );

        velocidadeRaqueteBaixo = limitar(
                velocidadeRaqueteBaixo,
                -dp(1500),
                dp(1500)
        );
    }

    // =========================================================
    // INTELIGÊNCIA ARTIFICIAL
    // =========================================================

    private void atualizarIA(float dt) {
        Bola alvo = null;
        float menorTempo = Float.MAX_VALUE;

        for (Bola bola : bolas) {
            if (bola.vy >= 0) {
                continue;
            }

            float distancia =
                    bola.y - (raqueteCimaY + raqueteAltura);

            float tempo = distancia / Math.abs(bola.vy);

            if (tempo >= 0 && tempo < menorTempo) {
                menorTempo = tempo;
                alvo = bola;
            }
        }

        tempoNovoErroIA -= dt;

        if (tempoNovoErroIA <= 0) {
            float dificuldade = obterDificuldade();
            float erroMaximo = interpolar(
                    dp(58),
                    dp(14),
                    dificuldade
            );

            erroIA =
                    (random.nextFloat() * 2f - 1f) * erroMaximo;

            tempoNovoErroIA = interpolar(
                    0.55f,
                    0.18f,
                    dificuldade
            );
        }

        float centroDesejado = larguraTela / 2f;

        if (alvo != null) {
            float tempoAteRaquete =
                    (alvo.y
                            - raqueteCimaY
                            - raqueteAltura
                            - alvo.raio)
                            / Math.abs(alvo.vy);

            tempoAteRaquete = Math.max(0, tempoAteRaquete);

            centroDesejado =
                    preverXComParedes(alvo, tempoAteRaquete)
                            + erroIA;
        }

        float xDesejado =
                centroDesejado - raqueteLargura / 2f;

        float dificuldade = obterDificuldade();

        float velocidadeIA = interpolar(
                dp(330),
                dp(720),
                dificuldade
        );

        alvoRaqueteCimaX = moverAte(
                alvoRaqueteCimaX,
                limitarRaquete(xDesejado),
                velocidadeIA * dt
        );
    }

    private float preverXComParedes(Bola bola, float tempo) {
        float minimo = bola.raio;
        float maximo = larguraTela - bola.raio;
        float espaco = maximo - minimo;

        if (espaco <= 0) {
            return larguraTela / 2f;
        }

        float futuro =
                bola.x + bola.vx * tempo - minimo;

        float periodo = espaco * 2f;

        futuro = futuro % periodo;

        if (futuro < 0) {
            futuro += periodo;
        }

        if (futuro > espaco) {
            futuro = periodo - futuro;
        }

        return minimo + futuro;
    }

    // =========================================================
    // FÍSICA DA BOLA
    // =========================================================

    private void atualizarBola(
            Bola bola,
            float dt,
            float dificuldade
    ) {
        bola.registrarRastro();

        bola.tempoFantasma = Math.max(0, bola.tempoFantasma - dt);
        bola.tempoBloqueioDuplicacao = Math.max(
                0,
                bola.tempoBloqueioDuplicacao - dt
        );
        bola.tempoCooldownPortal = Math.max(
                0,
                bola.tempoCooldownPortal - dt
        );

        if (bola.tempoTurboQuantico > 0) {
            bola.tempoTurboQuantico -= dt;
        }

        if (bola.tempoTurboSupernova > 0) {
            bola.tempoTurboSupernova -= dt;
        }

        // Durante o congelamento a bola fica realmente imóvel. Quando o
        // contador termina, ela é lançada na direção já sorteada.
        if (bola.tempoCongelada > 0) {
            bola.tempoCongelada -= dt;
            bola.vx = 0;
            bola.vy = 0;

            if (bola.tempoCongelada <= 0) {
                lancarBolaCongelada(bola);
            }

            bola.corHue = 195f;
            return;
        }

        if (variacoesVelocidadeAtivas) {
            bola.tempoMudancaVelocidade -= dt;

            if (bola.tempoMudancaVelocidade <= 0) {
                sortearNovaVelocidade(bola, dificuldade);
            }
        }

        float velocidadeAtual =
                comprimento(bola.vx, bola.vy);

        float multiplicadorEvento = 1f;

        if (bola.tempoTurboQuantico > 0) {
            multiplicadorEvento *= 1.90f;
        }

        if (bola.tempoTurboSupernova > 0) {
            multiplicadorEvento *= 1.55f;
        }

        float velocidadeDesejada =
                obterVelocidadeDesejada(bola) * multiplicadorEvento;

        if (velocidadeAtual > 0) {
            float velocidadeMaxima = obterVelocidadeMaxima(bola);

            float novaVelocidade = aproximar(
                    velocidadeAtual,
                    velocidadeDesejada,
                    interpolar(dp(42), dp(125), dificuldade) * dt
            );

            novaVelocidade =
                    Math.min(novaVelocidade, velocidadeMaxima);

            multiplicarVelocidade(
                    bola,
                    novaVelocidade / velocidadeAtual
            );
        }

        // Curva suave causada pelo giro da bola.
        if (Math.abs(bola.giro) > 0.001f) {
            rotacionarVelocidade(
                    bola,
                    bola.giro * dt
            );

            bola.giro *= Math.pow(0.54f, dt);
        }

        // Evento de curva temporária.
        if (bola.tempoCurva > 0) {
            rotacionarVelocidade(
                    bola,
                    bola.forcaCurva * dt
            );

            bola.tempoCurva -= dt;
        }

        // Curva forte: um ponto invisível atrai a bola e uma força
        // tangencial faz o movimento lembrar uma pequena órbita.
        if (bola.tempoOrbitaCritica > 0) {
            aplicarOrbitaCritica(bola, dt);
        } else if (bola.tempoAtracao > 0) {
            aplicarPontoDeAtracao(bola, dt);
        }

        if (bola.tempoOrbitaCritica <= 0) {
            bola.tempoCaos -= dt;
        }

        if (bola.tempoCaos <= 0
                && bola.tempoOrbitaCritica <= 0) {
            aplicarImprevisibilidade(bola, dificuldade);
        }

        garantirAnguloJogavel(bola);

        bola.corHue += dt * (85f + dificuldade * 130f);

        if (bola.corHue >= 360f) {
            bola.corHue -= 360f;
        }
    }

    private void configurarGradientesArena() {
        float meio = alturaTela / 2f;

        gradienteFundoCima = new LinearGradient(
                0, 0, 0, meio,
                new int[]{
                        Color.rgb(1, 4, 15),
                        Color.rgb(2, 18, 43),
                        Color.rgb(3, 29, 57)
                },
                new float[]{0f, 0.48f, 1f},
                Shader.TileMode.CLAMP
        );

        gradienteFundoBaixo = new LinearGradient(
                0, meio, 0, alturaTela,
                new int[]{
                        Color.rgb(46, 4, 37),
                        Color.rgb(25, 2, 28),
                        Color.rgb(5, 0, 12)
                },
                new float[]{0f, 0.58f, 1f},
                Shader.TileMode.CLAMP
        );

        nebulosaCimaA = criarNebulosa(
                larguraTela * 0.16f,
                alturaTela * 0.23f,
                larguraTela * 0.62f,
                Color.rgb(0, 155, 255)
        );
        nebulosaCimaB = criarNebulosa(
                larguraTela * 0.86f,
                alturaTela * 0.11f,
                larguraTela * 0.43f,
                Color.rgb(40, 70, 220)
        );
        nebulosaBaixoA = criarNebulosa(
                larguraTela * 0.79f,
                alturaTela * 0.76f,
                larguraTela * 0.60f,
                Color.rgb(255, 0, 145)
        );
        nebulosaBaixoB = criarNebulosa(
                larguraTela * 0.10f,
                alturaTela * 0.93f,
                larguraTela * 0.45f,
                Color.rgb(115, 0, 150)
        );

        brilhoHorizonte = new LinearGradient(
                0, meio - dp(84), 0, meio + dp(84),
                new int[]{
                        Color.TRANSPARENT,
                        Color.argb(25, 25, 175, 255),
                        Color.argb(55, 70, 70, 145),
                        Color.argb(28, 255, 25, 165),
                        Color.TRANSPARENT
                },
                new float[]{0f, 0.34f, 0.5f, 0.66f, 1f},
                Shader.TileMode.CLAMP
        );
    }

    private Shader criarNebulosa(
            float x,
            float y,
            float raio,
            int cor
    ) {
        return new RadialGradient(
                x,
                y,
                raio,
                new int[]{
                        Color.argb(94, Color.red(cor), Color.green(cor), Color.blue(cor)),
                        Color.argb(38, Color.red(cor), Color.green(cor), Color.blue(cor)),
                        Color.TRANSPARENT
                },
                new float[]{0f, 0.42f, 1f},
                Shader.TileMode.CLAMP
        );
    }

    /**
     * Move todas as bolas no mesmo subpasso. Isso impede que uma bola seja
     * atualizada inteira antes da outra e reduz drasticamente o efeito de
     * atravessarem uma à outra em alta velocidade.
     */
    private void integrarMovimentoBolas(float dt) {
        float maiorVelocidade = 0f;
        float menorRaio = Float.MAX_VALUE;

        for (Bola bola : bolas) {
            maiorVelocidade = Math.max(
                    maiorVelocidade,
                    comprimento(bola.vx, bola.vy)
            );
            menorRaio = Math.min(menorRaio, bola.raio);
        }

        validarReferenciasEventos();

        if (bolas.isEmpty()) {
            return;
        }

        int passos = (int) Math.ceil(
                maiorVelocidade * dt
                        / Math.max(dp(3), menorRaio * 0.42f)
        );

        passos = Math.max(
                1,
                Math.min(passos, modoRapido ? 7 : 12)
        );
        float passoTempo = dt / passos;

        for (int passo = 0; passo < passos; passo++) {
            for (Bola bola : bolas) {
                bola.x += bola.vx * passoTempo;
                bola.y += bola.vy * passoTempo;

                colidirComParedes(bola);
                colidirComRaquetes(bola);
                verificarPortais(bola);
            }

            resolverColisoesEntreBolas();
        }
    }

    private void verificarPortais(Bola bola) {
        if (tempoPortal <= 0
                || bola.tempoCooldownPortal > 0
                || bola.tempoCongelada > 0) {
            return;
        }

        float distanciaA = distanciaQuadrada(
                bola.x,
                bola.y,
                portalAX,
                portalAY
        );
        float distanciaB = distanciaQuadrada(
                bola.x,
                bola.y,
                portalBX,
                portalBY
        );
        float alcance = raioPortal + bola.raio * 0.35f;

        if (distanciaA <= alcance * alcance) {
            transportarPeloPortal(
                    bola,
                    portalAX,
                    portalAY,
                    portalBX,
                    portalBY
            );
        } else if (distanciaB <= alcance * alcance) {
            transportarPeloPortal(
                    bola,
                    portalBX,
                    portalBY,
                    portalAX,
                    portalAY
            );
        }
    }

    private void transportarPeloPortal(
            Bola bola,
            float entradaX,
            float entradaY,
            float saidaX,
            float saidaY
    ) {
        float velocidade = Math.max(
                1f,
                comprimento(bola.vx, bola.vy)
        );
        float nx = bola.vx / velocidade;
        float ny = bola.vy / velocidade;
        float afastamento = raioPortal + bola.raio + dp(6);

        criarExplosao(
                entradaX,
                entradaY,
                Color.rgb(135, 85, 255),
                14,
                0.95f
        );

        bola.x = limitar(
                saidaX + nx * afastamento,
                bola.raio,
                larguraTela - bola.raio
        );
        bola.y = limitar(
                saidaY + ny * afastamento,
                raqueteCimaY + raqueteAltura + bola.raio,
                raqueteBaixoY - bola.raio
        );
        rotacionarVelocidade(
                bola,
                (float) Math.toRadians(
                        random.nextBoolean() ? 18 : -18
                )
        );
        bola.tempoCooldownPortal = 0.62f;
        bola.preencherRastro();

        criarExplosao(
                saidaX,
                saidaY,
                Color.rgb(255, 80, 225),
                18,
                1.05f
        );
    }

    private void aplicarImprevisibilidade(
            Bola bola,
            float dificuldade
    ) {
        if (curvasAtivas) {
            float grausMaximos =
                    interpolar(4f, 18f, dificuldade);

            float graus =
                    (random.nextFloat() * 2f - 1f)
                            * grausMaximos;

            rotacionarVelocidade(
                    bola,
                    (float) Math.toRadians(graus)
            );
        }

        // Mudança aleatória de ritmo: pode acelerar ou desacelerar.
        if (variacoesVelocidadeAtivas) {
            sortearNovaVelocidade(bola, dificuldade);
        }

        // Algumas mudanças geram uma curva suave.
        if (curvasAtivas && random.nextFloat()
                < interpolar(0.32f, 0.78f, dificuldade)) {

            float sentido = random.nextBoolean() ? 1f : -1f;

            bola.forcaCurva =
                    sentido
                            * interpolar(
                            0.18f,
                            1.05f,
                            dificuldade
                    );

            bola.tempoCurva =
                    interpolar(
                            0.38f,
                            1.35f,
                            random.nextFloat()
                    );
        }

        // Curva "doida" com ponto de atração invisível.
        if (curvasAtivas && random.nextFloat()
                < interpolar(0.38f, 0.86f, dificuldade)) {
            iniciarCurvaDeAtracao(bola, dificuldade);
        }

        float intervaloBase =
                interpolar(4.5f, 0.90f, dificuldade);

        bola.tempoCaos =
                intervaloBase
                        * interpolar(
                        0.75f,
                        1.30f,
                        random.nextFloat()
                );

        garantirAnguloJogavel(bola);
    }

    private void sortearNovaVelocidade(
            Bola bola,
            float dificuldade
    ) {
        float variacaoMaxima =
                interpolar(0.08f, 0.34f, dificuldade);

        float variacao =
                (random.nextFloat() * 2f - 1f)
                        * variacaoMaxima;

        // Uma pequena chance de turbo aumenta com o tempo da rodada.
        if (random.nextFloat()
                < interpolar(0.06f, 0.20f, dificuldade)) {
            variacao += interpolar(0.08f, 0.18f, dificuldade);
        }

        bola.fatorVelocidadeAlvo =
                limitar(1f + variacao, 0.70f, 1.40f);

        bola.tempoMudancaVelocidade =
                interpolar(4.2f, 1.15f, dificuldade)
                        * interpolar(
                        0.72f,
                        1.28f,
                        random.nextFloat()
                );
    }

    private void iniciarCurvaDeAtracao(
            Bola bola,
            float dificuldade
    ) {
        bola.pontoAtracaoX = interpolar(
                bola.raio * 2f,
                larguraTela - bola.raio * 2f,
                random.nextFloat()
        );

        bola.pontoAtracaoY = interpolar(
                alturaTela * 0.16f,
                alturaTela * 0.84f,
                random.nextFloat()
        );

        bola.forcaAtracao = interpolar(
                dp(210),
                dp(820),
                dificuldade
        ) * interpolar(0.78f, 1.24f, random.nextFloat());

        float sentidoOrbita = random.nextBoolean() ? 1f : -1f;

        bola.forcaOrbital =
                bola.forcaAtracao
                        * interpolar(0.28f, 0.68f, dificuldade)
                        * sentidoOrbita;

        bola.tempoAtracao = interpolar(
                0.55f,
                1.55f,
                random.nextFloat()
        );

        if (tempoMensagemEvento <= 0.18f) {
            mostrarMensagem("ATRAÇÃO!");
        }
    }

    private void aplicarPontoDeAtracao(Bola bola, float dt) {
        float dx = bola.pontoAtracaoX - bola.x;
        float dy = bola.pontoAtracaoY - bola.y;
        float distancia = comprimento(dx, dy);

        if (distancia < 1f) {
            distancia = 1f;
        }

        float velocidadeAntes = comprimento(bola.vx, bola.vy);
        float nx = dx / distancia;
        float ny = dy / distancia;

        // Atração radial + empurrão tangencial = curva quase orbital.
        float ax = nx * bola.forcaAtracao
                - ny * bola.forcaOrbital;
        float ay = ny * bola.forcaAtracao
                + nx * bola.forcaOrbital;

        bola.vx += ax * dt;
        bola.vy += ay * dt;

        // O ponto invisível muda a direção sem destruir a progressão
        // de velocidade definida pelo tempo da rodada.
        float velocidadeDepois = comprimento(bola.vx, bola.vy);

        if (velocidadeAntes > 1f && velocidadeDepois > 1f) {
            multiplicarVelocidade(
                    bola,
                    velocidadeAntes / velocidadeDepois
            );
        }

        bola.tempoAtracao -= dt;
    }

    private void iniciarCongelamentoQuantico(Bola bola) {
        float angulo;

        do {
            angulo = random.nextFloat()
                    * (float) (Math.PI * 2);
            bola.direcaoQuanticaX = (float) Math.cos(angulo);
            bola.direcaoQuanticaY = (float) Math.sin(angulo);
        } while (Math.abs(bola.direcaoQuanticaY) < 0.42f);

        bola.tempoCongelada = 0.82f;
        bola.vx = 0;
        bola.vy = 0;
        bola.tempoAtracao = 0;
        bola.tempoOrbitaCritica = 0;
        bola.preencherRastro();

        mostrarMensagem("CONGELAMENTO QUÂNTICO!");
    }

    private void lancarBolaCongelada(Bola bola) {
        float velocidade = Math.min(
                obterVelocidadeDesejada(bola) * 2f,
                dp(1180)
        );

        bola.vx = bola.direcaoQuanticaX * velocidade;
        bola.vy = bola.direcaoQuanticaY * velocidade;
        bola.tempoTurboQuantico = 1.30f;
        bola.tempoCaos = Math.max(bola.tempoCaos, 0.75f);

        criarExplosao(
                bola.x,
                bola.y,
                Color.rgb(145, 235, 255),
                26,
                1.45f
        );
        flashImpacto = Math.max(flashImpacto, 0.30f);
    }

    private void iniciarOrbitaCritica(Bola bola) {
        bola.pontoAtracaoX = interpolar(
                larguraTela * 0.24f,
                larguraTela * 0.76f,
                random.nextFloat()
        );
        bola.pontoAtracaoY = interpolar(
                alturaTela * 0.24f,
                alturaTela * 0.76f,
                random.nextFloat()
        );

        bola.tempoOrbitaCriticaTotal = interpolar(
                2.25f,
                3.35f,
                random.nextFloat()
        );
        bola.tempoOrbitaCritica = bola.tempoOrbitaCriticaTotal;
        bola.sentidoOrbitaCritica = random.nextBoolean() ? 1f : -1f;
        bola.tempoAtracao = 0;
        bola.tempoCurva = 0;

        mostrarMensagem("ÓRBITA CRÍTICA!");
    }

    private void aplicarOrbitaCritica(Bola bola, float dt) {
        float dx = bola.pontoAtracaoX - bola.x;
        float dy = bola.pontoAtracaoY - bola.y;
        float distancia = Math.max(1f, comprimento(dx, dy));
        float nx = dx / distancia;
        float ny = dy / distancia;
        float progresso = 1f - limitar(
                bola.tempoOrbitaCritica
                        / bola.tempoOrbitaCriticaTotal,
                0f,
                1f
        );

        float forcaRadial = interpolar(
                dp(720),
                dp(2250),
                progresso
        );
        float forcaTangencial = interpolar(
                dp(560),
                dp(1900),
                progresso
        ) * bola.sentidoOrbitaCritica;

        bola.vx += (nx * forcaRadial - ny * forcaTangencial) * dt;
        bola.vy += (ny * forcaRadial + nx * forcaTangencial) * dt;

        float velocidade = comprimento(bola.vx, bola.vy);
        float velocidadeAlvo = Math.min(
                obterVelocidadeDesejada(bola)
                        * interpolar(1.10f, 2.35f, progresso),
                dp(1280)
        );

        if (velocidade > 1f) {
            float velocidadeNova = aproximar(
                    velocidade,
                    velocidadeAlvo,
                    dp(380) * dt
            );
            multiplicarVelocidade(
                    bola,
                    velocidadeNova / velocidade
            );
        }

        bola.tempoOrbitaCritica -= dt;

        if (bola.tempoOrbitaCritica <= 0) {
            bola.tempoTurboQuantico = Math.max(
                    bola.tempoTurboQuantico,
                    0.75f
            );
            garantirAnguloJogavel(bola);
            criarExplosao(
                    bola.x,
                    bola.y,
                    obterCorBola(bola),
                    30,
                    1.65f
            );
            flashImpacto = Math.max(flashImpacto, 0.35f);
        }
    }

    private float obterVelocidadeMaxima(Bola bola) {
        if (bola.tempoOrbitaCritica > 0) {
            return dp(1280);
        }

        if (bola.tempoTurboQuantico > 0
                || bola.tempoTurboSupernova > 0) {
            return dp(1180);
        }

        return dp(950);
    }

    private void colidirComParedes(Bola bola) {
        if (bola.x - bola.raio <= 0 && bola.vx < 0) {
            bola.x = bola.raio;
            bola.vx = Math.abs(bola.vx);
            criarExplosao(
                    bola.x,
                    bola.y,
                    obterCorBola(bola),
                    5,
                    0.62f
            );
        }

        if (bola.x + bola.raio >= larguraTela
                && bola.vx > 0) {

            bola.x = larguraTela - bola.raio;
            bola.vx = -Math.abs(bola.vx);
            criarExplosao(
                    bola.x,
                    bola.y,
                    obterCorBola(bola),
                    5,
                    0.62f
            );
        }
    }

    private void colidirComRaquetes(Bola bola) {
        if (bola.vy < 0
                && circuloEncostaRetangulo(
                bola,
                raqueteCimaX,
                raqueteCimaY,
                raqueteLargura,
                raqueteAltura
        )) {
            bola.y =
                    raqueteCimaY
                            + raqueteAltura
                            + bola.raio;

            rebaterNaRaquete(
                    bola,
                    raqueteCimaX,
                    velocidadeRaqueteCima,
                    true
            );
        }

        if (bola.vy > 0
                && circuloEncostaRetangulo(
                bola,
                raqueteBaixoX,
                raqueteBaixoY,
                raqueteLargura,
                raqueteAltura
        )) {
            bola.y = raqueteBaixoY - bola.raio;

            rebaterNaRaquete(
                    bola,
                    raqueteBaixoX,
                    velocidadeRaqueteBaixo,
                    false
            );
        }
    }

    private boolean circuloEncostaRetangulo(
            Bola bola,
            float x,
            float y,
            float largura,
            float altura
    ) {
        float pontoX =
                limitar(bola.x, x, x + largura);

        float pontoY =
                limitar(bola.y, y, y + altura);

        float distanciaX = bola.x - pontoX;
        float distanciaY = bola.y - pontoY;

        return distanciaX * distanciaX
                + distanciaY * distanciaY
                <= bola.raio * bola.raio;
    }

    private void rebaterNaRaquete(
            Bola bola,
            float xRaquete,
            float velocidadeRaquete,
            boolean raqueteDeCima
    ) {
        float centroRaquete =
                xRaquete + raqueteLargura / 2f;

        float deslocamento =
                (bola.x - centroRaquete)
                        / (raqueteLargura / 2f);

        deslocamento =
                limitar(deslocamento, -1f, 1f);

        float velocidade =
                Math.max(
                        comprimento(bola.vx, bola.vy) * 1.025f,
                        obterVelocidadeDesejada(bola)
                );

        velocidade = Math.min(
                velocidade,
                obterVelocidadeMaxima(bola)
        );

        float anguloMaximo =
                (float) Math.toRadians(68);

        float angulo = deslocamento * anguloMaximo;

        bola.vx =
                (float) Math.sin(angulo) * velocidade;

        bola.vx += velocidadeRaquete * 0.13f;

        float velocidadeHorizontalMaxima =
                velocidade * 0.88f;

        bola.vx = limitar(
                bola.vx,
                -velocidadeHorizontalMaxima,
                velocidadeHorizontalMaxima
        );

        float velocidadeVertical =
                (float) Math.sqrt(
                        Math.max(
                                1,
                                velocidade * velocidade
                                        - bola.vx * bola.vx
                        )
                );

        bola.vy = raqueteDeCima
                ? velocidadeVertical
                : -velocidadeVertical;

        bola.giro =
                deslocamento * 0.65f
                        + limitar(
                        velocidadeRaquete / dp(1200),
                        -0.45f,
                        0.45f
                );

        bola.tempoCaos = Math.max(
                bola.tempoCaos,
                0.55f
        );

        criarExplosao(
                bola.x,
                bola.y,
                raqueteDeCima
                        ? Color.rgb(45, 210, 255)
                        : Color.rgb(255, 70, 180),
                13,
                1f + obterDificuldade() * 0.45f
        );

        flashImpacto = Math.max(flashImpacto, 0.16f);

        if (bola.fundida) {
            bola.separacaoPendente = true;
        }
    }

    // =========================================================
    // COLISÃO ELÁSTICA ENTRE BOLAS
    // =========================================================

    private void resolverColisoesEntreBolas() {
        for (int i = 0; i < bolas.size(); i++) {
            for (int j = i + 1; j < bolas.size(); j++) {
                Bola a = bolas.get(i);
                Bola b = bolas.get(j);

                if (a.tempoCongelada > 0
                        || b.tempoCongelada > 0) {
                    continue;
                }

                float dx = b.x - a.x;
                float dy = b.y - a.y;

                float distanciaQuadrada =
                        dx * dx + dy * dy;

                float distanciaMinima =
                        a.raio + b.raio;

                if (distanciaQuadrada
                        > distanciaMinima * distanciaMinima) {
                    continue;
                }

                float distancia =
                        (float) Math.sqrt(distanciaQuadrada);

                float nx;
                float ny;

                if (distancia < 0.001f) {
                    float relativaX = b.vx - a.vx;
                    float relativaY = b.vy - a.vy;
                    float tamanhoRelativa =
                            comprimento(relativaX, relativaY);

                    if (tamanhoRelativa > 0.001f) {
                        nx = relativaX / tamanhoRelativa;
                        ny = relativaY / tamanhoRelativa;
                    } else {
                        float angulo = random.nextFloat()
                                * (float) (Math.PI * 2);
                        nx = (float) Math.cos(angulo);
                        ny = (float) Math.sin(angulo);
                    }
                    distancia = 0.001f;
                } else {
                    nx = dx / distancia;
                    ny = dy / distancia;
                }

                float sobreposicao =
                        distanciaMinima - distancia;

                a.x -= nx * sobreposicao / 2f;
                a.y -= ny * sobreposicao / 2f;

                b.x += nx * sobreposicao / 2f;
                b.y += ny * sobreposicao / 2f;

                float velocidadeRelativa =
                        (b.vx - a.vx) * nx
                                + (b.vy - a.vy) * ny;

                // Valor negativo significa que estão se aproximando.
                if (velocidadeRelativa < 0) {
                    float restituicao = 0.97f;

                    float impulso =
                            -(1f + restituicao)
                                    * velocidadeRelativa
                                    / 2f;

                    a.vx -= impulso * nx;
                    a.vy -= impulso * ny;

                    b.vx += impulso * nx;
                    b.vy += impulso * ny;

                    criarExplosao(
                            (a.x + b.x) / 2f,
                            (a.y + b.y) / 2f,
                            Color.rgb(255, 225, 95),
                            16,
                            1.2f + obterDificuldade() * 0.55f
                    );

                    flashImpacto = Math.max(flashImpacto, 0.22f);

                    limitarVelocidade(a, obterVelocidadeMaxima(a));
                    limitarVelocidade(b, obterVelocidadeMaxima(b));
                    garantirAnguloJogavel(a);
                    garantirAnguloJogavel(b);
                }
            }
        }
    }

    // =========================================================
    // PARTÍCULAS DE IMPACTO
    // =========================================================

    private void criarExplosao(
            float x,
            float y,
            int cor,
            int quantidade,
            float intensidade
    ) {
        quantidade = modoRapido
                ? Math.max(2, Math.round(quantidade * 0.42f))
                : quantidade;
        int limiteParticulas = modoRapido ? 58 : MAX_PARTICULAS;

        while (particulas.size() + quantidade > limiteParticulas
                && !particulas.isEmpty()) {
            particulas.remove(0);
        }

        for (int i = 0; i < quantidade; i++) {
            Particula particula = new Particula();
            float angulo = random.nextFloat()
                    * (float) (Math.PI * 2);
            float velocidade = dp(
                    interpolar(65f, 235f, random.nextFloat())
                            * intensidade
            );

            particula.x = x;
            particula.y = y;
            particula.vx = (float) Math.cos(angulo) * velocidade;
            particula.vy = (float) Math.sin(angulo) * velocidade;
            particula.vidaMaxima = interpolar(
                    0.18f,
                    0.48f,
                    random.nextFloat()
            );
            particula.vida = particula.vidaMaxima;
            particula.raio = dp(
                    interpolar(1.2f, 3.8f, random.nextFloat())
            );
            particula.cor = cor;
            particulas.add(particula);
        }
    }

    private void atualizarParticulas(float dt) {
        Iterator<Particula> iterator = particulas.iterator();
        float arrasto = (float) Math.pow(0.045f, dt);

        while (iterator.hasNext()) {
            Particula particula = iterator.next();
            particula.vida -= dt;

            if (particula.vida <= 0) {
                iterator.remove();
                continue;
            }

            particula.x += particula.vx * dt;
            particula.y += particula.vy * dt;
            particula.vx *= arrasto;
            particula.vy *= arrasto;
        }
    }

    private void atualizarTextoHud(float dt) {
        tempoAtualizacaoHud -= dt;

        if (tempoAtualizacaoHud > 0) {
            return;
        }

        int nivel = 1 + Math.min(
                9,
                (int) (obterDificuldade() * 9f)
        );
        textoNivelHud = "NÍVEL " + nivel;
        textoRitmoHud = String.format(
                java.util.Locale.US,
                "RITMO x%.2f",
                obterMultiplicadorMedio()
        );
        tempoAtualizacaoHud = 0.18f;
    }

    // =========================================================
    // EVENTOS
    // =========================================================

    private void executarEventoAleatorio() {
        if (bolas.isEmpty()) {
            return;
        }

        float dificuldade = obterDificuldade();
        int quantidadeEventos = 0;

        for (int i = 0; i < eventosAtivos.length; i++) {
            if (eventosAtivos[i]) {
                eventosDisponiveis[quantidadeEventos] = i;
                quantidadeEventos++;
            }
        }

        if (quantidadeEventos == 0) {
            tempoProximoEvento = 9_999f;
            return;
        }

        int evento = eventosDisponiveis[
                random.nextInt(quantidadeEventos)
        ];
        boolean executado;

        switch (evento) {
            case ConfiguracoesJogo.EVENTO_DUPLICACAO:
                executado = duplicarBola();

                if (executado) {
                    mostrarMensagem("BOLA DUPLICADA!");
                }
                break;

            case ConfiguracoesJogo.EVENTO_REVERSAO:
                executarReversao();
                executado = true;
                break;

            case ConfiguracoesJogo.EVENTO_TURBO:
                executarTurbo(dificuldade);
                executado = true;
                break;

            case ConfiguracoesJogo.EVENTO_CONGELAMENTO:
                iniciarCongelamentoQuantico(sortearBolaParaEvento());
                executado = true;
                break;

            case ConfiguracoesJogo.EVENTO_FANTASMA:
                ativarBolaFantasma();
                executado = true;
                break;

            case ConfiguracoesJogo.EVENTO_PORTAIS:
                iniciarPortais();
                executado = true;
                break;

            case ConfiguracoesJogo.EVENTO_ORBITA:
                iniciarOrbitaCritica(sortearBolaParaEvento());
                executado = true;
                break;

            case ConfiguracoesJogo.EVENTO_CADEIA:
                executado = iniciarDuplicacaoEmCadeia();
                break;

            case ConfiguracoesJogo.EVENTO_ELETRICO:
                executado = iniciarLigacaoEletrica();
                break;

            case ConfiguracoesJogo.EVENTO_FUSAO:
                executado = fundirDuasBolas();
                break;

            case ConfiguracoesJogo.EVENTO_SUPERNOVA:
                iniciarSupernova();
                executado = true;
                break;

            default:
                executado = false;
                break;
        }

        // Não troca por um evento que o jogador desativou. Se o evento
        // escolhido ainda não puder ocorrer, tenta novamente em breve.
        if (!executado) {
            tempoProximoEvento = 1.8f;
            return;
        }

        tempoProximoEvento =
                interpolar(10.8f, 3.25f, dificuldade)
                        * interpolar(
                        0.82f,
                        1.18f,
                        random.nextFloat()
                );
    }

    private Bola sortearBolaParaEvento() {
        return bolas.get(random.nextInt(bolas.size()));
    }

    private void executarReversao() {
        Bola bola = sortearBolaParaEvento();
        bola.vy *= -1f;

        if (random.nextBoolean()) {
            bola.vx *= -1f;
        }

        bola.y += Math.signum(bola.vy) * bola.raio * 1.4f;
        mostrarMensagem("REVERSÃO!");
    }

    private void executarTurbo(float dificuldade) {
        Bola bola = sortearBolaParaEvento();
        bola.fatorVelocidadeAlvo = limitar(
                bola.fatorVelocidadeAlvo
                        * interpolar(1.10f, 1.24f, dificuldade),
                0.70f,
                1.40f
        );

        multiplicarVelocidade(
                bola,
                interpolar(1.06f, 1.14f, dificuldade)
        );

        limitarVelocidade(bola, obterVelocidadeMaxima(bola));
        mostrarMensagem("TURBO!");
    }

    private boolean iniciarDuplicacaoEmCadeia() {
        int espacos = MAX_BOLAS - bolas.size();

        if (espacos <= 0) {
            return false;
        }

        duplicacoesCadeiaPendentes = Math.min(
                espacos,
                2 + random.nextInt(2)
        );
        tempoProximaDuplicacaoCadeia = 0.12f;
        mostrarMensagem("DUPLICAÇÃO EM CADEIA!");
        return true;
    }

    private boolean duplicarBola() {
        return duplicarBola(false);
    }

    private boolean duplicarBola(boolean ignorarBloqueio) {
        if (bolas.size() >= MAX_BOLAS) {
            return false;
        }

        ArrayList<Bola> candidatas = new ArrayList<>();

        for (Bola bola : bolas) {
            if (!bola.fundida
                    && bola.tempoCongelada <= 0
                    && (ignorarBloqueio
                    || bola.tempoBloqueioDuplicacao <= 0)) {
                candidatas.add(bola);
            }
        }

        if (candidatas.isEmpty()) {
            return false;
        }

        Bola original = candidatas.get(
                random.nextInt(candidatas.size())
        );

        Bola nova = new Bola();

        nova.raio = original.raio;
        nova.x = limitar(
                original.x + original.raio * 2.3f,
                nova.raio,
                larguraTela - nova.raio
        );
        nova.y = original.y;

        nova.vx = original.vx;
        nova.vy = original.vy;

        float angulo =
                (float) Math.toRadians(
                        random.nextBoolean() ? 16 : -16
                );

        rotacionarVelocidade(nova, angulo);

        nova.giro = -original.giro;
        nova.corHue = (original.corHue + 120f) % 360f;
        nova.tempoCaos = 1.2f + random.nextFloat();
        nova.fatorVelocidadeAlvo = limitar(
                original.fatorVelocidadeAlvo
                        + (random.nextFloat() * 0.16f - 0.08f),
                0.70f,
                1.40f
        );
        nova.tempoMudancaVelocidade =
                1.1f + random.nextFloat() * 1.4f;

        original.tempoBloqueioDuplicacao = 4f;
        nova.tempoBloqueioDuplicacao = 4f;
        nova.tempoCooldownPortal = 0.45f;

        nova.preencherRastro();
        bolas.add(nova);

        criarExplosao(
                original.x,
                original.y,
                obterCorBola(original),
                18,
                1.15f
        );
        return true;
    }

    private void ativarBolaFantasma() {
        Bola bola = sortearBolaParaEvento();
        bola.tempoFantasma = 4.2f;
        mostrarMensagem("BOLA FANTASMA!");
    }

    private void iniciarPortais() {
        raioPortal = Math.max(dp(28), larguraTela * 0.075f);
        portalAX = interpolar(
                larguraTela * 0.20f,
                larguraTela * 0.80f,
                random.nextFloat()
        );
        portalAY = interpolar(
                alturaTela * 0.18f,
                alturaTela * 0.38f,
                random.nextFloat()
        );
        portalBX = interpolar(
                larguraTela * 0.20f,
                larguraTela * 0.80f,
                random.nextFloat()
        );
        portalBY = interpolar(
                alturaTela * 0.62f,
                alturaTela * 0.82f,
                random.nextFloat()
        );
        tempoPortal = 6.5f;
        mostrarMensagem("PORTAIS ABERTOS!");
    }

    private boolean iniciarLigacaoEletrica() {
        if (bolas.size() < 2) {
            return false;
        }

        int primeiro = random.nextInt(bolas.size());
        int segundo;

        do {
            segundo = random.nextInt(bolas.size());
        } while (segundo == primeiro);

        bolaEletricaA = bolas.get(primeiro);
        bolaEletricaB = bolas.get(segundo);
        tempoLigacaoEletrica = 4.6f;
        tempoFaiscaEletrica = 0f;
        mostrarMensagem("LIGAÇÃO ELÉTRICA!");
        return true;
    }

    private boolean fundirDuasBolas() {
        if (bolas.size() < 2) {
            return false;
        }

        ArrayList<Bola> candidatas = new ArrayList<>();

        for (Bola bola : bolas) {
            if (!bola.fundida && bola.tempoCongelada <= 0) {
                candidatas.add(bola);
            }
        }

        if (candidatas.size() < 2) {
            return false;
        }

        Bola a = candidatas.remove(random.nextInt(candidatas.size()));
        Bola b = candidatas.get(random.nextInt(candidatas.size()));
        Bola fundida = new Bola();

        fundida.raio = Math.max(a.raio, b.raio) * 1.55f;
        fundida.x = limitar(
                (a.x + b.x) / 2f,
                fundida.raio,
                larguraTela - fundida.raio
        );
        fundida.y = limitar(
                (a.y + b.y) / 2f,
                raqueteCimaY + raqueteAltura + fundida.raio,
                raqueteBaixoY - fundida.raio
        );

        float vx = a.vx + b.vx;
        float vy = a.vy + b.vy;
        float tamanho = comprimento(vx, vy);

        if (tamanho < 1f) {
            vx = a.vx;
            vy = a.vy;
            tamanho = Math.max(1f, comprimento(vx, vy));
        }

        float velocidade = Math.min(
                obterVelocidadeBaseRodada() * 1.28f,
                dp(1050)
        );
        fundida.vx = vx / tamanho * velocidade;
        fundida.vy = vy / tamanho * velocidade;
        fundida.corHue = (a.corHue + b.corHue) / 2f;
        fundida.fatorVelocidadeAlvo = limitar(
                (a.fatorVelocidadeAlvo + b.fatorVelocidadeAlvo) / 2f,
                0.85f,
                1.35f
        );
        fundida.tempoCaos = 1.5f;
        fundida.tempoMudancaVelocidade = 1.8f;
        fundida.tempoBloqueioDuplicacao = 5f;
        fundida.fundida = true;
        fundida.preencherRastro();

        bolas.remove(a);
        bolas.remove(b);
        bolas.add(fundida);

        criarExplosao(
                fundida.x,
                fundida.y,
                Color.WHITE,
                34,
                1.55f
        );
        flashImpacto = Math.max(flashImpacto, 0.36f);
        mostrarMensagem("FUSÃO!");
        validarReferenciasEventos();
        return true;
    }

    private void iniciarSupernova() {
        supernovaX = interpolar(
                larguraTela * 0.30f,
                larguraTela * 0.70f,
                random.nextFloat()
        );
        supernovaY = interpolar(
                alturaTela * 0.32f,
                alturaTela * 0.68f,
                random.nextFloat()
        );
        tempoSupernovaCarga = 0.95f;
        tempoSupernovaEfeito = 2.15f;
        supernovaExplodiu = false;
        mostrarMensagem("SUPERNOVA!");
    }

    private void atualizarEventosGlobais(float dt) {
        if (tempoPortal > 0) {
            tempoPortal -= dt;
        }

        if (duplicacoesCadeiaPendentes > 0) {
            tempoProximaDuplicacaoCadeia -= dt;

            if (tempoProximaDuplicacaoCadeia <= 0) {
                if (duplicarBola(true)) {
                    duplicacoesCadeiaPendentes--;
                    tempoProximaDuplicacaoCadeia = 0.52f;

                    if (bolas.size() >= MAX_BOLAS) {
                        duplicacoesCadeiaPendentes = 0;
                        mostrarMensagem("CAOS MÁXIMO!");
                    }
                } else {
                    // Aguarda o bloqueio de uma bola terminar, mas não deixa
                    // a cadeia presa para sempre.
                    tempoProximaDuplicacaoCadeia = 0.45f;
                }
            }
        }

        if (tempoLigacaoEletrica > 0) {
            tempoLigacaoEletrica -= dt;

            if (bolaEletricaA != null
                    && bolaEletricaB != null
                    && bolas.contains(bolaEletricaA)
                    && bolas.contains(bolaEletricaB)) {
                aplicarLigacaoEletrica(dt);
            } else {
                tempoLigacaoEletrica = 0;
            }
        }

        atualizarSupernova(dt);
    }

    private void aplicarLigacaoEletrica(float dt) {
        if (bolaEletricaA.tempoCongelada > 0
                || bolaEletricaB.tempoCongelada > 0) {
            return;
        }

        float dx = bolaEletricaB.x - bolaEletricaA.x;
        float dy = bolaEletricaB.y - bolaEletricaA.y;
        float distancia = Math.max(1f, comprimento(dx, dy));
        float nx = dx / distancia;
        float ny = dy / distancia;
        float distanciaIdeal = dp(175);
        float forca = limitar(
                (distancia - distanciaIdeal) * 4.2f,
                -dp(900),
                dp(900)
        );

        bolaEletricaA.vx += nx * forca * dt;
        bolaEletricaA.vy += ny * forca * dt;
        bolaEletricaB.vx -= nx * forca * dt;
        bolaEletricaB.vy -= ny * forca * dt;

        // Pequeno giro transforma a ligação num estilingue elétrico.
        float giro = dp(115) * dt;
        bolaEletricaA.vx += -ny * giro;
        bolaEletricaA.vy += nx * giro;
        bolaEletricaB.vx -= -ny * giro;
        bolaEletricaB.vy -= nx * giro;

        limitarVelocidade(
                bolaEletricaA,
                obterVelocidadeMaxima(bolaEletricaA)
        );
        limitarVelocidade(
                bolaEletricaB,
                obterVelocidadeMaxima(bolaEletricaB)
        );

        tempoFaiscaEletrica -= dt;

        if (tempoFaiscaEletrica <= 0) {
            float proporcao = random.nextFloat();
            criarExplosao(
                    interpolar(bolaEletricaA.x, bolaEletricaB.x, proporcao),
                    interpolar(bolaEletricaA.y, bolaEletricaB.y, proporcao),
                    Color.rgb(120, 225, 255),
                    3,
                    0.52f
            );
            tempoFaiscaEletrica = 0.12f;
        }
    }

    private void atualizarSupernova(float dt) {
        if (tempoSupernovaEfeito <= 0) {
            return;
        }

        tempoSupernovaEfeito -= dt;

        if (!supernovaExplodiu) {
            tempoSupernovaCarga -= dt;

            // Durante a carga, a estrela puxa levemente todas as bolas.
            for (Bola bola : bolas) {
                if (bola.tempoCongelada > 0) {
                    continue;
                }

                float dx = supernovaX - bola.x;
                float dy = supernovaY - bola.y;
                float distancia = Math.max(1f, comprimento(dx, dy));
                bola.vx += dx / distancia * dp(250) * dt;
                bola.vy += dy / distancia * dp(250) * dt;
            }

            if (tempoSupernovaCarga <= 0) {
                explodirSupernova();
            }
        }
    }

    private void explodirSupernova() {
        supernovaExplodiu = true;

        for (Bola bola : bolas) {
            float dx = bola.x - supernovaX;
            float dy = bola.y - supernovaY;
            float distancia = comprimento(dx, dy);

            if (distancia < 1f) {
                float angulo = random.nextFloat()
                        * (float) (Math.PI * 2);
                dx = (float) Math.cos(angulo);
                dy = (float) Math.sin(angulo);
                distancia = 1f;
            }

            float velocidade = Math.min(
                    obterVelocidadeDesejada(bola) * 1.65f,
                    dp(1180)
            );
            bola.vx = dx / distancia * velocidade;
            bola.vy = dy / distancia * velocidade;
            bola.tempoCongelada = 0;
            bola.tempoTurboSupernova = 1.65f;
            garantirAnguloJogavel(bola);
        }

        criarExplosao(
                supernovaX,
                supernovaY,
                Color.rgb(255, 235, 110),
                70,
                2.15f
        );
        flashImpacto = Math.max(flashImpacto, 0.72f);
    }

    private void validarReferenciasEventos() {
        if (bolaEletricaA == null
                || bolaEletricaB == null
                || !bolas.contains(bolaEletricaA)
                || !bolas.contains(bolaEletricaB)) {
            bolaEletricaA = null;
            bolaEletricaB = null;
            tempoLigacaoEletrica = 0;
        }
    }

    private void processarSeparacoesPendentes() {
        ArrayList<Bola> paraSeparar = new ArrayList<>();

        for (Bola bola : bolas) {
            if (bola.fundida && bola.separacaoPendente) {
                paraSeparar.add(bola);
            }
        }

        for (Bola fundida : paraSeparar) {
            if (!bolas.remove(fundida)) {
                continue;
            }

            float raio = fundida.raio / 1.55f;
            float angulo = (float) Math.toRadians(19);

            Bola a = criarBolaSeparada(fundida, raio, -angulo);
            Bola b = criarBolaSeparada(fundida, raio, angulo);

            a.x = limitar(
                    fundida.x - raio * 1.15f,
                    raio,
                    larguraTela - raio
            );
            b.x = limitar(
                    fundida.x + raio * 1.15f,
                    raio,
                    larguraTela - raio
            );

            bolas.add(a);

            if (bolas.size() < MAX_BOLAS) {
                bolas.add(b);
            }

            criarExplosao(
                    fundida.x,
                    fundida.y,
                    Color.WHITE,
                    30,
                    1.45f
            );
            mostrarMensagem("FUSÃO ROMPIDA!");
        }
    }

    private Bola criarBolaSeparada(
            Bola origem,
            float raio,
            float rotacao
    ) {
        Bola bola = new Bola();
        bola.x = origem.x;
        bola.y = origem.y;
        bola.vx = origem.vx;
        bola.vy = origem.vy;
        bola.raio = raio;
        bola.corHue = (origem.corHue
                + (rotacao < 0 ? 45f : 315f)) % 360f;
        bola.fatorVelocidadeAlvo = origem.fatorVelocidadeAlvo;
        bola.tempoCaos = 1.15f;
        bola.tempoMudancaVelocidade = 1.3f;
        bola.tempoBloqueioDuplicacao = 3.5f;
        rotacionarVelocidade(bola, rotacao);
        bola.preencherRastro();
        return bola;
    }

    // =========================================================
    // RODADA
    // =========================================================

    private void prepararRodada(boolean primeira) {
        // Toda rodada começa calma. A freneticidade volta a crescer
        // somente enquanto ainda houver ao menos uma bola em jogo.
        tempoRodada = 0f;
        tempoProximoEvento = 9f + random.nextFloat() * 3f;
        limparEventosGlobais();
        bolas.clear();

        Bola bola = new Bola();

        bola.raio =
                Math.max(dp(12), larguraTela * 0.035f);

        bola.x = larguraTela / 2f;
        bola.y = alturaTela / 2f;
        bola.corHue = random.nextInt(360);
        bola.tempoCaos = 2.5f;
        bola.fatorVelocidadeAlvo = 1f;
        bola.tempoMudancaVelocidade = 1.5f + random.nextFloat();
        bola.preencherRastro();

        bolas.add(bola);

        esperandoInicio = true;
        primeiraRodada = primeira;

        if (!primeira) {
            tempoParaReiniciar = 0.85f;
        }
    }

    private void comecarRodada() {
        for (Bola bola : bolas) {
            float velocidade = obterVelocidadeDesejada(bola);
            float anguloHorizontal =
                    interpolar(
                            -0.62f,
                            0.62f,
                            random.nextFloat()
                    );

            bola.vx =
                    (float) Math.sin(anguloHorizontal)
                            * velocidade;

            float vy =
                    (float) Math.cos(anguloHorizontal)
                            * velocidade;

            bola.vy = random.nextBoolean() ? vy : -vy;
            bola.tempoCaos = 2.2f + random.nextFloat();
        }

        esperandoInicio = false;
        primeiraRodada = false;
    }

    private void encerrarJogo(String textoVencedor) {
        jogoEncerrado = true;
        esperandoInicio = true;
        vencedor = textoVencedor;
        mensagemEvento = "";
        tempoMensagemEvento = 0f;
        limparEventosGlobais();
        bolas.clear();
    }

    private void limparEventosGlobais() {
        tempoPortal = 0;
        bolaEletricaA = null;
        bolaEletricaB = null;
        tempoLigacaoEletrica = 0;
        duplicacoesCadeiaPendentes = 0;
        tempoProximaDuplicacaoCadeia = 0;
        tempoSupernovaCarga = 0;
        tempoSupernovaEfeito = 0;
        supernovaExplodiu = false;
    }

    private void reiniciarPartida() {
        pontosCima = 0;
        pontosBaixo = 0;
        jogoEncerrado = false;
        vencedor = "";
        prepararRodada(true);
    }

    // =========================================================
    // TOQUES
    // =========================================================

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int acao = event.getActionMasked();
        int indice = event.getActionIndex();

        if ((acao == MotionEvent.ACTION_DOWN
                || acao == MotionEvent.ACTION_POINTER_DOWN)
                && jogoEncerrado) {
            reiniciarPartida();
            comecarRodada();
            return true;
        }

        if (acao == MotionEvent.ACTION_DOWN
                || acao == MotionEvent.ACTION_POINTER_DOWN) {

            if (esperandoInicio) {
                comecarRodada();
            }

            int id = event.getPointerId(indice);
            float x = event.getX(indice);
            float y = event.getY(indice);

            registrarPonteiro(id, x, y);
            return true;
        }

        if (acao == MotionEvent.ACTION_MOVE) {
            for (int i = 0;
                 i < event.getPointerCount();
                 i++) {

                int id = event.getPointerId(i);
                float x = event.getX(i);

                if (id == ponteiroCima
                        && !modoUmJogador) {

                    alvoRaqueteCimaX =
                            limitarRaquete(
                                    x - raqueteLargura / 2f
                            );
                }

                if (id == ponteiroBaixo) {
                    alvoRaqueteBaixoX =
                            limitarRaquete(
                                    x - raqueteLargura / 2f
                            );
                }
            }

            return true;
        }

        if (acao == MotionEvent.ACTION_UP
                || acao == MotionEvent.ACTION_POINTER_UP
                || acao == MotionEvent.ACTION_CANCEL) {

            int id = event.getPointerId(indice);

            if (id == ponteiroCima) {
                ponteiroCima = -1;
            }

            if (id == ponteiroBaixo) {
                ponteiroBaixo = -1;
            }

            if (acao == MotionEvent.ACTION_UP
                    || acao == MotionEvent.ACTION_CANCEL) {

                ponteiroCima = -1;
                ponteiroBaixo = -1;
                performClick();
            }

            return true;
        }

        return super.onTouchEvent(event);
    }

    private void registrarPonteiro(
            int id,
            float x,
            float y
    ) {
        if (y < alturaTela / 2f
                && !modoUmJogador
                && ponteiroCima == -1) {

            ponteiroCima = id;
            alvoRaqueteCimaX =
                    limitarRaquete(
                            x - raqueteLargura / 2f
                    );

        } else if (ponteiroBaixo == -1) {
            ponteiroBaixo = id;
            alvoRaqueteBaixoX =
                    limitarRaquete(
                            x - raqueteLargura / 2f
                    );
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    // =========================================================
    // DESENHO
    // =========================================================

    private void desenhar() {
        if (!holder.getSurface().isValid()) {
            return;
        }

        Canvas canvas = holder.lockCanvas();

        if (canvas == null) {
            return;
        }

        try {
            desenharFundo(canvas);
            desenharEventosGlobais(canvas);
            desenharEfeitosDeAtracao(canvas);

            desenharRaquetes(canvas);
            desenharParticulas(canvas);
            desenharBolas(canvas);
            desenharFlashImpacto(canvas);
            desenharPlacar(canvas);
            desenharInformacoes(canvas);

            if (tempoMensagemEvento > 0) {
                desenharMensagemEvento(canvas);
            }

            if (jogoEncerrado) {
                desenharFimDoJogo(canvas);
            } else if (esperandoInicio && primeiraRodada) {
                desenharInicio(canvas);
            }

            desenharMolduraEnergia(canvas);
        } finally {
            holder.unlockCanvasAndPost(canvas);
        }
    }

    private void desenharFundo(Canvas canvas) {
        atualizarCacheArena();

        if (cacheArena != null && !cacheArena.isRecycled()) {
            canvas.drawBitmap(
                    cacheArena,
                    null,
                    destinoCacheArena,
                    paintCache
            );
            return;
        }

        // Plano B para aparelhos com memória extremamente limitada.
        desenharFundoEstatico(canvas);
        desenharLinhaCentral(canvas);
    }

    private void atualizarCacheArena() {
        if (cacheArenaIndisponivel) {
            return;
        }

        int nivelDesejado = modoRapido
                ? 0
                : Math.min(4, (int) (obterDificuldade() * 5f));

        if (cacheArena != null && nivelVisualCache == nivelDesejado) {
            return;
        }

        liberarCacheArena();

        float escala = modoRapido ? 0.46f : 0.68f;
        int larguraCache = Math.max(1, Math.round(larguraTela * escala));
        int alturaCache = Math.max(1, Math.round(alturaTela * escala));

        try {
            cacheArena = Bitmap.createBitmap(
                    larguraCache,
                    alturaCache,
                    Bitmap.Config.RGB_565
            );
            Canvas canvasCache = new Canvas(cacheArena);
            canvasCache.scale(escala, escala);

            desenharFundoEstatico(canvasCache);
            desenharLinhaCentral(canvasCache);
            desenharMolduraCompleta(canvasCache);

            nivelVisualCache = nivelDesejado;
        } catch (OutOfMemoryError erro) {
            liberarCacheArena();
            cacheArenaIndisponivel = true;
        } finally {
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setAlpha(255);
        }
    }

    private void liberarCacheArena() {
        if (cacheArena != null && !cacheArena.isRecycled()) {
            cacheArena.recycle();
        }

        cacheArena = null;
        nivelVisualCache = -1;
    }

    private void desenharFundoEstatico(Canvas canvas) {
        float meio = alturaTela / 2f;
        float dificuldade = obterDificuldade();
        float pulso = 0.5f + 0.5f
                * (float) Math.sin(tempoRodada * (2.2f + dificuldade * 3f));

        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);

        paint.setShader(gradienteFundoCima);
        canvas.drawRect(0, 0, larguraTela, meio, paint);

        paint.setShader(gradienteFundoBaixo);
        canvas.drawRect(0, meio, larguraTela, alturaTela, paint);

        // Nebulosas em camadas: profundidade sem depender de bitmaps externos.
        paint.setAlpha((int) (145 + pulso * 55 + dificuldade * 35));
        paint.setShader(nebulosaCimaA);
        canvas.drawRect(0, 0, larguraTela, meio, paint);
        paint.setAlpha(175);
        paint.setShader(nebulosaCimaB);
        canvas.drawRect(0, 0, larguraTela, meio, paint);

        paint.setAlpha((int) (150 + pulso * 45 + dificuldade * 40));
        paint.setShader(nebulosaBaixoA);
        canvas.drawRect(0, meio, larguraTela, alturaTela, paint);
        paint.setAlpha(165);
        paint.setShader(nebulosaBaixoB);
        canvas.drawRect(0, meio, larguraTela, alturaTela, paint);

        paint.setShader(null);
        paint.setAlpha(255);

        desenharFilamentosNebulosa(canvas, dificuldade, pulso);

        desenharEstrelasEnergia(canvas, dificuldade);
        desenharGradeNeon(canvas, dificuldade);
        desenharFragmentosEspaciais(canvas, dificuldade);

        // Horizonte escurecido mantém placar e informações legíveis.
        paint.setShader(brilhoHorizonte);
        paint.setAlpha(220);
        canvas.drawRect(
                0,
                meio - dp(86),
                larguraTela,
                meio + dp(86),
                paint
        );

        paint.setShader(null);
        paint.setAlpha(255);
    }

    private void desenharFilamentosNebulosa(
            Canvas canvas,
            float dificuldade,
            float pulso
    ) {
        float deslocamento = (float) Math.sin(tempoRodada * 0.28f) * dp(8);
        int alpha = (int) interpolar(13f, 31f, dificuldade);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.1f));

        for (int i = 0; i < 5; i++) {
            float abertura = dp(18 + i * 13) + pulso * dp(8);
            paint.setColor(Color.argb(
                    Math.max(5, alpha - i * 4),
                    35,
                    185,
                    255
            ));
            canvas.drawOval(
                    larguraTela * 0.06f - abertura + deslocamento,
                    alturaTela * 0.16f - abertura * 0.55f,
                    larguraTela * 0.43f + abertura + deslocamento,
                    alturaTela * 0.35f + abertura * 0.55f,
                    paint
            );

            paint.setColor(Color.argb(
                    Math.max(5, alpha - i * 4),
                    255,
                    35,
                    170
            ));
            canvas.drawOval(
                    larguraTela * 0.57f - abertura - deslocamento,
                    alturaTela * 0.69f - abertura * 0.5f,
                    larguraTela * 0.99f + abertura - deslocamento,
                    alturaTela * 0.88f + abertura * 0.5f,
                    paint
            );
        }

        paint.setStyle(Paint.Style.FILL);
    }

    private void desenharEstrelasEnergia(
            Canvas canvas,
            float dificuldade
    ) {
        for (int i = 0; i < 58; i++) {
            float x = (i * 83f + 31f) % larguraTela;
            float y = (i * 137f + 47f) % alturaTela;
            float brilho = 0.5f + 0.5f * (float) Math.sin(
                    tempoRodada * (1.2f + (i % 4) * 0.17f) + i
            );
            int alpha = (int) (
                    interpolar(22f, 78f, dificuldade) * (0.35f + brilho * 0.65f)
            );

            boolean parteCima = y < alturaTela / 2f;
            paint.setColor(parteCima
                    ? Color.argb(alpha, 185, 230, 255)
                    : Color.argb(alpha, 255, 185, 235));
            canvas.drawCircle(
                    x,
                    y,
                    dp(0.45f + (i % 4) * 0.27f),
                    paint
            );

            if (i % 13 == 0 && brilho > 0.62f) {
                paint.setStrokeWidth(dp(0.65f));
                canvas.drawLine(x - dp(4), y, x + dp(4), y, paint);
                canvas.drawLine(x, y - dp(4), x, y + dp(4), paint);
            }
        }
    }

    private void desenharGradeNeon(
            Canvas canvas,
            float dificuldade
    ) {
        float meio = alturaTela / 2f;
        int alpha = (int) interpolar(18f, 43f, dificuldade);
        float fase = (tempoRodada * (0.045f + dificuldade * 0.055f)) % 1f;

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(0.72f));

        // Linhas convergentes criam a sensação de uma arena em perspectiva.
        for (int i = -8; i <= 8; i++) {
            float bordaX = larguraTela / 2f + i * larguraTela * 0.145f;
            int alphaLinha = i % 4 == 0 ? alpha + 14 : alpha;

            paint.setColor(Color.argb(alphaLinha, 45, 205, 255));
            canvas.drawLine(larguraTela / 2f, meio, bordaX, 0, paint);

            paint.setColor(Color.argb(alphaLinha, 255, 55, 175));
            canvas.drawLine(
                    larguraTela / 2f,
                    meio,
                    bordaX,
                    alturaTela,
                    paint
            );
        }

        for (int i = 0; i < 12; i++) {
            float proporcao = (i + fase) / 12f;
            float distancia = meio
                    * (float) Math.pow(proporcao, 1.62f);
            int alphaLinha = (int) (
                    alpha * (0.34f + proporcao * 0.86f)
            );

            paint.setColor(Color.argb(alphaLinha, 50, 205, 255));
            canvas.drawLine(
                    0,
                    meio - distancia,
                    larguraTela,
                    meio - distancia,
                    paint
            );

            paint.setColor(Color.argb(alphaLinha, 255, 55, 175));
            canvas.drawLine(
                    0,
                    meio + distancia,
                    larguraTela,
                    meio + distancia,
                    paint
            );
        }

        paint.setStyle(Paint.Style.FILL);
    }

    private void desenharFragmentosEspaciais(
            Canvas canvas,
            float dificuldade
    ) {
        for (int i = 0; i < 7; i++) {
            boolean esquerda = i % 2 == 0;
            float x = esquerda
                    ? larguraTela * (0.045f + (i % 3) * 0.025f)
                    : larguraTela * (0.955f - (i % 3) * 0.022f);
            float y = alturaTela * (0.10f + i * 0.128f);
            float tamanho = dp(6 + (i % 3) * 3);
            boolean cima = y < alturaTela / 2f;

            canvas.save();
            canvas.translate(x, y);
            canvas.rotate(i * 29f + tempoRodada * (i % 2 == 0 ? 1.4f : -1.1f));

            caminhoArena.reset();
            caminhoArena.moveTo(-tamanho, -tamanho * 0.25f);
            caminhoArena.lineTo(-tamanho * 0.28f, -tamanho);
            caminhoArena.lineTo(tamanho * 0.82f, -tamanho * 0.48f);
            caminhoArena.lineTo(tamanho, tamanho * 0.42f);
            caminhoArena.lineTo(tamanho * 0.10f, tamanho);
            caminhoArena.lineTo(-tamanho * 0.78f, tamanho * 0.58f);
            caminhoArena.close();

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(cima
                    ? Color.argb(120, 4, 18, 32)
                    : Color.argb(125, 29, 4, 27));
            canvas.drawPath(caminhoArena, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(0.7f));
            paint.setColor(cima
                    ? Color.argb((int) (42 + dificuldade * 45), 55, 185, 235)
                    : Color.argb((int) (42 + dificuldade * 45), 230, 45, 160));
            canvas.drawPath(caminhoArena, paint);
            canvas.restore();
        }

        paint.setStyle(Paint.Style.FILL);
    }

    private void desenharLinhaCentral(Canvas canvas) {
        float meio = alturaTela / 2f;
        float metadeLargura = larguraTela / 2f;

        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(dp(10));
        paint.setColor(Color.argb(42, 35, 205, 255));
        canvas.drawLine(0, meio, metadeLargura, meio, paint);
        paint.setColor(Color.argb(42, 255, 40, 180));
        canvas.drawLine(metadeLargura, meio, larguraTela, meio, paint);

        paint.setStrokeWidth(dp(2.3f));
        for (float x = dp(12); x < larguraTela; x += dp(34)) {
            float fim = Math.min(x + dp(17), larguraTela);
            paint.setColor(x < metadeLargura
                    ? Color.argb(220, 125, 235, 255)
                    : Color.argb(220, 255, 125, 220));
            canvas.drawLine(x, meio, fim, meio, paint);
        }

        paint.setStrokeCap(Paint.Cap.BUTT);
    }

    private void desenharRaquetes(Canvas canvas) {
        desenharRaqueteNeon(
                canvas,
                raqueteCimaX,
                raqueteCimaY,
                Color.rgb(45, 210, 255)
        );

        desenharRaqueteNeon(
                canvas,
                raqueteBaixoX,
                raqueteBaixoY,
                Color.rgb(255, 70, 180)
        );
    }

    private void desenharRaqueteNeon(
            Canvas canvas,
            float x,
            float y,
            int cor
    ) {
        paint.setStyle(Paint.Style.FILL);

        paint.setColor(cor);
        paint.setAlpha(25);
        canvas.drawRoundRect(
                x - dp(10),
                y - dp(10),
                x + raqueteLargura + dp(10),
                y + raqueteAltura + dp(10),
                dp(13),
                dp(13),
                paint
        );

        paint.setAlpha(70);
        canvas.drawRoundRect(
                x - dp(5),
                y - dp(5),
                x + raqueteLargura + dp(5),
                y + raqueteAltura + dp(5),
                dp(9),
                dp(9),
                paint
        );

        paint.setAlpha(255);
        canvas.drawRoundRect(
                x,
                y,
                x + raqueteLargura,
                y + raqueteAltura,
                raqueteAltura / 2f,
                raqueteAltura / 2f,
                paint
        );

        paint.setColor(Color.argb(185, 255, 255, 255));
        canvas.drawRoundRect(
                x + dp(8),
                y + dp(2),
                x + raqueteLargura - dp(8),
                y + Math.max(dp(3), raqueteAltura * 0.34f),
                dp(3),
                dp(3),
                paint
        );

        paint.setAlpha(255);
    }

    private void desenharBolas(Canvas canvas) {
        for (Bola bola : bolas) {
            int cor = bola.tempoCongelada > 0
                    ? Color.rgb(145, 235, 255)
                    : obterCorBola(bola);
            float velocidade = comprimento(bola.vx, bola.vy);
            float ritmo = limitar(
                    velocidade / obterVelocidadeInicial(),
                    0.8f,
                    2.9f
            );
            boolean fantasma = bola.tempoFantasma > 0;
            float oscilacaoFantasma = 0.5f + 0.5f
                    * (float) Math.sin(
                    tempoRodada * 16f + bola.corHue
            );
            int alphaCorpo = fantasma
                    ? (int) (28 + oscilacaoFantasma * 58)
                    : 255;

            int passoRastro = modoRapido ? 2 : 1;

            for (int i = Bola.TAMANHO_RASTRO - 1;
                 i >= 1;
                 i -= passoRastro) {

                float proporcao =
                        1f - i / (float) Bola.TAMANHO_RASTRO;

                paint.setColor(cor);
                paint.setAlpha((int) (
                        proporcao * interpolar(
                                fantasma ? 90f : 55f,
                                fantasma ? 190f : 145f,
                                ritmo / 2.9f
                        )
                ));

                canvas.drawCircle(
                        bola.rastroX[i],
                        bola.rastroY[i],
                        bola.raio
                                * (0.22f + proporcao * 0.62f),
                        paint
                );
            }

            // Duas camadas de brilho dão volume à bola RGB.
            paint.setColor(cor);
            paint.setAlpha(fantasma ? 18 : 35);
            canvas.drawCircle(
                    bola.x,
                    bola.y,
                    bola.raio * (1.9f + ritmo * 0.14f),
                    paint
            );

            paint.setAlpha(fantasma ? 38 : 82);
            canvas.drawCircle(
                    bola.x,
                    bola.y,
                    bola.raio * 1.38f,
                    paint
            );

            paint.setAlpha(alphaCorpo);
            paint.setColor(cor);

            canvas.drawCircle(
                    bola.x,
                    bola.y,
                    bola.raio,
                    paint
            );

            paint.setColor(Color.argb(
                    fantasma ? 38 : 155,
                    255,
                    255,
                    255
            ));

            canvas.drawCircle(
                    bola.x - bola.raio * 0.28f,
                    bola.y - bola.raio * 0.28f,
                    bola.raio * 0.25f,
                    paint
            );

            if (bola.fundida) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(2.2f));
                paint.setColor(Color.argb(185, 255, 255, 255));
                canvas.drawCircle(
                        bola.x,
                        bola.y,
                        bola.raio * 1.17f,
                        paint
                );
                paint.setStyle(Paint.Style.FILL);
            }

            if (bola.tempoCongelada > 0) {
                desenharCongelamento(canvas, bola);
            }
        }

        paint.setAlpha(255);
    }

    private void desenharCongelamento(Canvas canvas, Bola bola) {
        float pulso = 0.5f + 0.5f
                * (float) Math.sin(tempoRodada * 18f);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        paint.setColor(Color.argb(210, 220, 250, 255));
        canvas.drawCircle(
                bola.x,
                bola.y,
                bola.raio * (1.25f + pulso * 0.12f),
                paint
        );

        int raiosGelo = modoRapido ? 4 : 6;
        for (int i = 0; i < raiosGelo; i++) {
            float angulo = i * (float) (Math.PI * 2 / raiosGelo);
            canvas.drawLine(
                    bola.x + (float) Math.cos(angulo) * bola.raio * 0.25f,
                    bola.y + (float) Math.sin(angulo) * bola.raio * 0.25f,
                    bola.x + (float) Math.cos(angulo) * bola.raio * 1.65f,
                    bola.y + (float) Math.sin(angulo) * bola.raio * 1.65f,
                    paint
            );
        }

        float tamanhoSeta = dp(42);
        paint.setColor(Color.argb(155, 150, 235, 255));
        paint.setStrokeWidth(dp(1.5f));
        canvas.drawLine(
                bola.x,
                bola.y,
                bola.x + bola.direcaoQuanticaX * tamanhoSeta,
                bola.y + bola.direcaoQuanticaY * tamanhoSeta,
                paint
        );

        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);
    }

    private void desenharEventosGlobais(Canvas canvas) {
        desenharPortais(canvas);
        desenharLigacaoEletrica(canvas);
        desenharSupernova(canvas);
    }

    private void desenharPortais(Canvas canvas) {
        if (tempoPortal <= 0) {
            return;
        }

        desenharPortal(
                canvas,
                portalAX,
                portalAY,
                Color.rgb(105, 80, 255),
                1f
        );
        desenharPortal(
                canvas,
                portalBX,
                portalBY,
                Color.rgb(255, 65, 220),
                -1f
        );
    }

    private void desenharPortal(
            Canvas canvas,
            float x,
            float y,
            int cor,
            float sentido
    ) {
        float rotacao = tempoRodada * 3.5f * sentido;
        paint.setStyle(Paint.Style.STROKE);

        int aneisPortal = modoRapido ? 2 : 4;
        for (int i = 0; i < aneisPortal; i++) {
            paint.setColor(cor);
            paint.setAlpha(145 - i * 25);
            paint.setStrokeWidth(dp(3f - i * 0.45f));
            canvas.drawCircle(
                    x,
                    y,
                    raioPortal * (0.58f + i * 0.17f),
                    paint
            );
        }

        paint.setStrokeWidth(dp(2));
        paint.setAlpha(190);

        int raiosPortal = modoRapido ? 3 : 6;
        for (int i = 0; i < raiosPortal; i++) {
            float angulo = rotacao
                    + i * (float) (Math.PI * 2 / raiosPortal);
            canvas.drawLine(
                    x + (float) Math.cos(angulo) * raioPortal * 0.38f,
                    y + (float) Math.sin(angulo) * raioPortal * 0.38f,
                    x + (float) Math.cos(angulo) * raioPortal * 0.92f,
                    y + (float) Math.sin(angulo) * raioPortal * 0.92f,
                    paint
            );
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(80, 5, 2, 18));
        canvas.drawCircle(x, y, raioPortal * 0.54f, paint);
        paint.setAlpha(255);
    }

    private void desenharLigacaoEletrica(Canvas canvas) {
        if (tempoLigacaoEletrica <= 0
                || bolaEletricaA == null
                || bolaEletricaB == null
                || !bolas.contains(bolaEletricaA)
                || !bolas.contains(bolaEletricaB)) {
            return;
        }

        float dx = bolaEletricaB.x - bolaEletricaA.x;
        float dy = bolaEletricaB.y - bolaEletricaA.y;
        float distancia = Math.max(1f, comprimento(dx, dy));
        float perpendicularX = -dy / distancia;
        float perpendicularY = dx / distancia;
        float anteriorX = bolaEletricaA.x;
        float anteriorY = bolaEletricaA.y;

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2.2f));
        paint.setColor(Color.rgb(125, 230, 255));
        paint.setAlpha(210);

        int segmentosRaio = modoRapido ? 6 : 10;
        for (int i = 1; i <= segmentosRaio; i++) {
            float proporcao = i / (float) segmentosRaio;
            float deslocamento = i == segmentosRaio
                    ? 0
                    : (float) Math.sin(
                    tempoRodada * 28f + i * 2.7f
            ) * dp(7);
            float atualX = interpolar(
                    bolaEletricaA.x,
                    bolaEletricaB.x,
                    proporcao
            ) + perpendicularX * deslocamento;
            float atualY = interpolar(
                    bolaEletricaA.y,
                    bolaEletricaB.y,
                    proporcao
            ) + perpendicularY * deslocamento;

            canvas.drawLine(
                    anteriorX,
                    anteriorY,
                    atualX,
                    atualY,
                    paint
            );
            anteriorX = atualX;
            anteriorY = atualY;
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);
    }

    private void desenharSupernova(Canvas canvas) {
        if (tempoSupernovaEfeito <= 0) {
            return;
        }

        paint.setStyle(Paint.Style.STROKE);

        if (!supernovaExplodiu) {
            float progresso = 1f - limitar(
                    tempoSupernovaCarga / 0.95f,
                    0f,
                    1f
            );
            float raio = dp(18) + progresso * dp(48);

            int aneisSupernova = modoRapido ? 2 : 4;
            for (int i = 0; i < aneisSupernova; i++) {
                paint.setColor(Color.argb(
                        190 - i * 34,
                        255,
                        235,
                        115
                ));
                paint.setStrokeWidth(dp(3f - i * 0.45f));
                canvas.drawCircle(
                        supernovaX,
                        supernovaY,
                        raio + i * dp(12),
                        paint
                );
            }

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(230, 255, 250, 205));
            canvas.drawCircle(
                    supernovaX,
                    supernovaY,
                    dp(8) + progresso * dp(9),
                    paint
            );
        } else {
            float restante = limitar(
                    tempoSupernovaEfeito / 1.20f,
                    0f,
                    1f
            );
            float raio = dp(35)
                    + (1f - restante) * larguraTela * 0.82f;
            paint.setColor(Color.argb(
                    (int) (restante * 190),
                    255,
                    225,
                    105
            ));
            paint.setStrokeWidth(dp(4));
            canvas.drawCircle(
                    supernovaX,
                    supernovaY,
                    raio,
                    paint
            );
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);
    }

    private void desenharEfeitosDeAtracao(Canvas canvas) {
        paint.setStyle(Paint.Style.STROKE);

        for (Bola bola : bolas) {
            boolean critica = bola.tempoOrbitaCritica > 0;

            if (bola.tempoAtracao <= 0 && !critica) {
                continue;
            }

            int cor = obterCorBola(bola);
            float pulso = 0.5f + 0.5f * (float) Math.sin(
                    tempoRodada * (critica ? 22f : 12f) + bola.corHue
            );

            paint.setStrokeWidth(dp(critica ? 2.4f : 1.1f));

            int quantidadeAneis = modoRapido
                    ? (critica ? 4 : 2)
                    : (critica ? 7 : 4);

            for (int i = 0; i < quantidadeAneis; i++) {
                int alpha = critica
                        ? Math.max(30, 150 - i * 17)
                        : 74 - i * 13;
                paint.setColor(cor);
                paint.setAlpha(alpha);
                canvas.drawCircle(
                        bola.pontoAtracaoX,
                        bola.pontoAtracaoY,
                        dp(
                                (critica ? 12 : 18)
                                        + i * (critica ? 13 : 15)
                        ) + pulso * dp(critica ? 13 : 8),
                        paint
                );
            }

            paint.setStrokeWidth(dp(critica ? 1.8f : 0.8f));
            paint.setAlpha(critica ? 105 : 45);
            canvas.drawLine(
                    bola.x,
                    bola.y,
                    bola.pontoAtracaoX,
                    bola.pontoAtracaoY,
                    paint
            );

            paint.setStyle(Paint.Style.FILL);
            paint.setAlpha(critica ? 115 : 40);
            canvas.drawCircle(
                    bola.pontoAtracaoX,
                    bola.pontoAtracaoY,
                    dp(critica ? 11 : 7)
                            + pulso * dp(critica ? 6 : 3),
                    paint
            );

            paint.setStyle(Paint.Style.STROKE);
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);
    }

    private void desenharParticulas(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);

        for (Particula particula : particulas) {
            float vida = limitar(
                    particula.vida / particula.vidaMaxima,
                    0f,
                    1f
            );

            paint.setColor(particula.cor);
            paint.setAlpha((int) (vida * 220));
            canvas.drawCircle(
                    particula.x,
                    particula.y,
                    particula.raio * (0.45f + vida * 0.85f),
                    paint
            );
        }

        paint.setAlpha(255);
    }

    private void desenharFlashImpacto(Canvas canvas) {
        if (flashImpacto <= 0) {
            return;
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(
                (int) (limitar(flashImpacto, 0f, 1f) * 80),
                255,
                255,
                255
        ));
        canvas.drawRect(0, 0, larguraTela, alturaTela, paint);
    }

    private void desenharMolduraEnergia(Canvas canvas) {
        if (cacheArena == null || cacheArena.isRecycled()) {
            desenharMolduraCompleta(canvas);
            return;
        }

        if (!modoRapido) {
            desenharVarreduraLateral(canvas, obterDificuldade());
        }
    }

    private void desenharMolduraCompleta(Canvas canvas) {
        float meio = alturaTela / 2f;
        float dificuldade = obterDificuldade();
        float pulso = 0.5f + 0.5f * (float) Math.sin(
                tempoRodada * (3.5f + dificuldade * 4f)
        );

        desenharContornoTecnologico(
                canvas,
                meio,
                Color.rgb(35, 205, 255),
                true,
                pulso
        );
        desenharContornoTecnologico(
                canvas,
                meio,
                Color.rgb(255, 40, 180),
                false,
                pulso
        );

        desenharPainelLateral(canvas, true, true, dificuldade, pulso);
        desenharPainelLateral(canvas, false, true, dificuldade, pulso);
        desenharPainelLateral(canvas, true, false, dificuldade, pulso);
        desenharPainelLateral(canvas, false, false, dificuldade, pulso);

        desenharCantosTecnologicos(canvas);

        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);
    }

    private void desenharContornoTecnologico(
            Canvas canvas,
            float meio,
            int cor,
            boolean superior,
            float pulso
    ) {
        float margem = dp(4);
        float canto = dp(22);

        caminhoArena.reset();
        if (superior) {
            caminhoArena.moveTo(margem, meio);
            caminhoArena.lineTo(margem, canto);
            caminhoArena.lineTo(canto, margem);
            caminhoArena.lineTo(larguraTela - canto, margem);
            caminhoArena.lineTo(larguraTela - margem, canto);
            caminhoArena.lineTo(larguraTela - margem, meio);
        } else {
            caminhoArena.moveTo(margem, meio);
            caminhoArena.lineTo(margem, alturaTela - canto);
            caminhoArena.lineTo(canto, alturaTela - margem);
            caminhoArena.lineTo(larguraTela - canto, alturaTela - margem);
            caminhoArena.lineTo(larguraTela - margem, alturaTela - canto);
            caminhoArena.lineTo(larguraTela - margem, meio);
        }

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);

        paint.setColor(Color.argb(
                (int) (38 + pulso * 25),
                Color.red(cor),
                Color.green(cor),
                Color.blue(cor)
        ));
        paint.setStrokeWidth(dp(11));
        canvas.drawPath(caminhoArena, paint);

        paint.setColor(Color.argb(
                105,
                Color.red(cor),
                Color.green(cor),
                Color.blue(cor)
        ));
        paint.setStrokeWidth(dp(4.6f));
        canvas.drawPath(caminhoArena, paint);

        paint.setColor(Color.argb(
                235,
                Color.red(cor),
                Color.green(cor),
                Color.blue(cor)
        ));
        paint.setStrokeWidth(dp(1.35f));
        canvas.drawPath(caminhoArena, paint);

        paint.setStrokeJoin(Paint.Join.MITER);
        paint.setStrokeCap(Paint.Cap.BUTT);
    }

    private void desenharPainelLateral(
            Canvas canvas,
            boolean esquerda,
            boolean superior,
            float dificuldade,
            float pulso
    ) {
        int cor = superior
                ? Color.rgb(35, 210, 255)
                : Color.rgb(255, 45, 185);
        float painelLargura = dp(14);
        float xInicio = esquerda
                ? dp(7)
                : larguraTela - dp(7) - painelLargura;
        float yInicio = superior
                ? alturaTela * 0.18f
                : alturaTela * 0.68f;
        float yFim = superior
                ? alturaTela * 0.40f
                : alturaTela * 0.90f;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(225, 2, 7, 17));
        canvas.drawRoundRect(
                xInicio - dp(2),
                yInicio - dp(8),
                xInicio + painelLargura + dp(2),
                yFim + dp(8),
                dp(5),
                dp(5),
                paint
        );

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(4.5f));
        paint.setColor(Color.argb(
                35,
                Color.red(cor),
                Color.green(cor),
                Color.blue(cor)
        ));
        canvas.drawRoundRect(
                xInicio - dp(2),
                yInicio - dp(8),
                xInicio + painelLargura + dp(2),
                yFim + dp(8),
                dp(5),
                dp(5),
                paint
        );
        paint.setStrokeWidth(dp(1));
        paint.setColor(Color.argb(
                190,
                Color.red(cor),
                Color.green(cor),
                Color.blue(cor)
        ));
        canvas.drawRoundRect(
                xInicio - dp(2),
                yInicio - dp(8),
                xInicio + painelLargura + dp(2),
                yFim + dp(8),
                dp(5),
                dp(5),
                paint
        );

        int segmentos = 9;
        int ativos = 2 + (int) (dificuldade * 7f);
        float espaco = dp(2.6f);
        float alturaSegmento = (
                yFim - yInicio - espaco * (segmentos - 1)
        ) / segmentos;

        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < segmentos; i++) {
            boolean ativo = i < ativos;
            float onda = 0.5f + 0.5f * (float) Math.sin(
                    tempoRodada * 6f + i * 0.9f
            );
            int alpha = ativo
                    ? (int) (135 + onda * 100)
                    : 24;
            float y = superior
                    ? yFim - (i + 1) * alturaSegmento - i * espaco
                    : yInicio + i * (alturaSegmento + espaco);

            paint.setColor(Color.argb(
                    Math.min(255, alpha),
                    Color.red(cor),
                    Color.green(cor),
                    Color.blue(cor)
            ));
            canvas.drawRoundRect(
                    xInicio + dp(2.5f),
                    y,
                    xInicio + painelLargura - dp(2.5f),
                    y + alturaSegmento,
                    dp(1.4f),
                    dp(1.4f),
                    paint
            );
        }

        paint.setColor(Color.argb(
                (int) (130 + pulso * 100),
                Color.red(cor),
                Color.green(cor),
                Color.blue(cor)
        ));
        canvas.drawCircle(
                xInicio + painelLargura / 2f,
                (yInicio + yFim) / 2f,
                dp(2.2f),
                paint
        );
    }

    private void desenharCantosTecnologicos(Canvas canvas) {
        float margem = dp(8);
        float alcance = dp(34);
        float diagonal = dp(12);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));

        for (int i = 0; i < 4; i++) {
            boolean direita = i % 2 == 1;
            boolean baixo = i >= 2;
            int cor = baixo
                    ? Color.rgb(255, 55, 190)
                    : Color.rgb(45, 215, 255);
            float sx = direita ? -1f : 1f;
            float sy = baixo ? -1f : 1f;
            float x = direita ? larguraTela - margem : margem;
            float y = baixo ? alturaTela - margem : margem;

            paint.setColor(Color.argb(
                    205,
                    Color.red(cor),
                    Color.green(cor),
                    Color.blue(cor)
            ));
            canvas.drawLine(x, y + sy * diagonal, x + sx * diagonal, y, paint);
            canvas.drawLine(x + sx * diagonal, y, x + sx * alcance, y, paint);
            canvas.drawLine(x, y + sy * diagonal, x, y + sy * alcance, paint);

            paint.setStrokeWidth(dp(0.8f));
            canvas.drawLine(
                    x + sx * dp(6),
                    y + sy * dp(20),
                    x + sx * dp(20),
                    y + sy * dp(6),
                    paint
            );
            paint.setStrokeWidth(dp(2));
        }
    }

    private void desenharVarreduraLateral(
            Canvas canvas,
            float dificuldade
    ) {
        float faixa = (tempoRodada * (0.10f + dificuldade * 0.12f)) % 1f;
        float yCima = dp(28)
                + faixa * (alturaTela / 2f - dp(56));
        float yBaixo = alturaTela / 2f + dp(28)
                + faixa * (alturaTela / 2f - dp(56));

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(165, 115, 235, 255));
        canvas.drawRoundRect(
                dp(2), yCima, dp(8), yCima + dp(20), dp(3), dp(3), paint
        );
        canvas.drawRoundRect(
                larguraTela - dp(8), yCima,
                larguraTela - dp(2), yCima + dp(20),
                dp(3), dp(3), paint
        );

        paint.setColor(Color.argb(165, 255, 105, 220));
        canvas.drawRoundRect(
                dp(2), yBaixo, dp(8), yBaixo + dp(20), dp(3), dp(3), paint
        );
        canvas.drawRoundRect(
                larguraTela - dp(8), yBaixo,
                larguraTela - dp(2), yBaixo + dp(20),
                dp(3), dp(3), paint
        );
    }

    private int obterCorBola(Bola bola) {
        hsvTemporario[0] = bola.corHue;
        hsvTemporario[1] = 0.86f;
        hsvTemporario[2] = 1f;
        return Color.HSVToColor(hsvTemporario);
    }

    private void desenharPlacar(Canvas canvas) {
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(dp(48));

        paint.setColor(Color.rgb(90, 225, 255));
        canvas.drawText(
                String.valueOf(pontosCima),
                larguraTela / 2f,
                alturaTela / 2f - dp(38),
                paint
        );

        paint.setColor(Color.rgb(255, 105, 195));
        canvas.drawText(
                String.valueOf(pontosBaixo),
                larguraTela / 2f,
                alturaTela / 2f + dp(72),
                paint
        );

        paint.setTextSize(dp(12));

        paint.setColor(Color.argb(210, 90, 225, 255));
        canvas.drawText(
                modoUmJogador ? "CPU" : "JOGADOR DE CIMA",
                larguraTela / 2f,
                alturaTela / 2f - dp(94),
                paint
        );

        paint.setColor(Color.argb(210, 255, 105, 195));
        canvas.drawText(
                modoUmJogador ? "VOCÊ" : "JOGADOR DE BAIXO",
                larguraTela / 2f,
                alturaTela / 2f + dp(112),
                paint
        );
    }

    private void desenharInformacoes(Canvas canvas) {
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(dp(14));
        paint.setColor(Color.argb(190, 255, 255, 255));

        paint.setTextAlign(Paint.Align.LEFT);

        canvas.drawText(
                textoNivelHud,
                dp(14),
                alturaTela / 2f - dp(12),
                paint
        );

        paint.setTextAlign(Paint.Align.RIGHT);

        canvas.drawText(
                textoRitmoHud,
                larguraTela - dp(14),
                alturaTela / 2f - dp(12),
                paint
        );
    }

    private void desenharMensagemEvento(Canvas canvas) {
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(dp(
                mensagemEvento.length() > 18 ? 20f : 28f
        ));

        // Camada externa simulando brilho neon.
        paint.setColor(Color.argb(70, 255, 230, 40));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(5));

        canvas.drawText(
                mensagemEvento,
                larguraTela / 2f,
                alturaTela / 2f - dp(100),
                paint
        );

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(255, 240, 70));

        canvas.drawText(
                mensagemEvento,
                larguraTela / 2f,
                alturaTela / 2f - dp(100),
                paint
        );
    }

    private void desenharInicio(Canvas canvas) {
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(dp(23));
        paint.setColor(Color.WHITE);

        canvas.drawText(
                "TOQUE PARA COMEÇAR",
                larguraTela / 2f,
                alturaTela / 2f + dp(8),
                paint
        );

        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextSize(dp(15));
        paint.setColor(Color.LTGRAY);

        canvas.drawText(
                modoUmJogador
                        ? "Você controla a raquete de baixo"
                        : "Cada jogador controla uma raquete",
                larguraTela / 2f,
                alturaTela / 2f + dp(38),
                paint
        );

        paint.setTextSize(dp(12));
        paint.setColor(Color.argb(205, 125, 235, 255));
        canvas.drawText(
                "META: " + pontosParaVencer + " PONTOS"
                        + (modoRapido ? "  •  MODO RÁPIDO" : ""),
                larguraTela / 2f,
                alturaTela / 2f + dp(61),
                paint
        );
    }

    private void desenharFimDoJogo(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(225, 0, 0, 0));
        canvas.drawRect(
                0,
                alturaTela * 0.31f,
                larguraTela,
                alturaTela * 0.69f,
                paint
        );

        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(Color.YELLOW);
        paint.setTextSize(dp(30));
        canvas.drawText(
                "FIM DE JOGO",
                larguraTela / 2f,
                alturaTela / 2f - dp(58),
                paint
        );

        paint.setColor(Color.WHITE);
        paint.setTextSize(dp(19));
        canvas.drawText(
                vencedor,
                larguraTela / 2f,
                alturaTela / 2f - dp(12),
                paint
        );

        paint.setTextSize(dp(29));
        canvas.drawText(
                pontosCima + "  x  " + pontosBaixo,
                larguraTela / 2f,
                alturaTela / 2f + dp(38),
                paint
        );

        paint.setTypeface(Typeface.DEFAULT);
        paint.setColor(Color.LTGRAY);
        paint.setTextSize(dp(14));
        canvas.drawText(
                "TOQUE PARA JOGAR NOVAMENTE",
                larguraTela / 2f,
                alturaTela / 2f + dp(82),
                paint
        );
    }

    // =========================================================
    // CÁLCULOS AUXILIARES
    // =========================================================

    private float obterVelocidadeInicial() {
        return dp(335) * multiplicadorVelocidadeInicial;
    }

    private float obterVelocidadeBaseRodada() {
        float progresso = limitar(tempoRodada / 75f, 0f, 1f);

        // Começa controlado e fica claramente mais frenético com o
        // tempo. A curva exponencial evita um salto brusco no início.
        float multiplicador =
                1f + 1.15f * (float) Math.pow(progresso, 0.85f);

        return Math.min(
                obterVelocidadeInicial() * multiplicador,
                dp(900)
        );
    }

    private float obterVelocidadeDesejada(Bola bola) {
        return Math.min(
                obterVelocidadeBaseRodada()
                        * bola.fatorVelocidadeAlvo,
                dp(950)
        );
    }

    private float obterDificuldade() {
        return limitar(tempoRodada / 70f, 0f, 1f);
    }

    private float obterMultiplicadorMedio() {
        if (bolas.isEmpty()) {
            return 1f;
        }

        float soma = 0f;

        for (Bola bola : bolas) {
            soma += comprimento(bola.vx, bola.vy)
                    / obterVelocidadeInicial();
        }

        float media = soma / bolas.size();

        // Antes do primeiro toque as bolas ainda estão paradas. O HUD deve
        // mostrar o ritmo de largada, não "x0.00".
        if (media < 0.05f) {
            return obterVelocidadeBaseRodada()
                    / obterVelocidadeInicial();
        }

        return media;
    }

    private void garantirAnguloJogavel(Bola bola) {
        float velocidade =
                comprimento(bola.vx, bola.vy);

        if (velocidade < 1f) {
            return;
        }

        float minimoVertical = velocidade * 0.38f;

        if (Math.abs(bola.vy) < minimoVertical) {
            float sentidoY =
                    bola.vy == 0
                            ? (random.nextBoolean() ? 1f : -1f)
                            : Math.signum(bola.vy);

            bola.vy = sentidoY * minimoVertical;

            float novoVX =
                    (float) Math.sqrt(
                            Math.max(
                                    0,
                                    velocidade * velocidade
                                            - bola.vy * bola.vy
                            )
                    );

            float sentidoX =
                    bola.vx == 0
                            ? (random.nextBoolean() ? 1f : -1f)
                            : Math.signum(bola.vx);

            bola.vx = sentidoX * novoVX;
        }
    }

    private void limitarVelocidade(
            Bola bola,
            float maxima
    ) {
        float velocidade =
                comprimento(bola.vx, bola.vy);

        if (velocidade > maxima) {
            multiplicarVelocidade(
                    bola,
                    maxima / velocidade
            );
        }
    }

    private void multiplicarVelocidade(
            Bola bola,
            float multiplicador
    ) {
        bola.vx *= multiplicador;
        bola.vy *= multiplicador;
    }

    private void rotacionarVelocidade(
            Bola bola,
            float angulo
    ) {
        float cos = (float) Math.cos(angulo);
        float sin = (float) Math.sin(angulo);

        float novoVX =
                bola.vx * cos - bola.vy * sin;

        float novoVY =
                bola.vx * sin + bola.vy * cos;

        bola.vx = novoVX;
        bola.vy = novoVY;
    }

    private void mostrarMensagem(String mensagem) {
        mensagemEvento = mensagem;
        tempoMensagemEvento = 0.95f;
    }

    private float limitarRaquete(float x) {
        return limitar(
                x,
                0,
                larguraTela - raqueteLargura
        );
    }

    private float dp(float valor) {
        return valor * densidade;
    }

    private static float comprimento(float x, float y) {
        return (float) Math.sqrt(x * x + y * y);
    }

    private static float distanciaQuadrada(
            float x1,
            float y1,
            float x2,
            float y2
    ) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        return dx * dx + dy * dy;
    }

    private static float limitar(
            float valor,
            float minimo,
            float maximo
    ) {
        return Math.max(minimo, Math.min(valor, maximo));
    }

    private static float interpolar(
            float inicio,
            float fim,
            float proporcao
    ) {
        return inicio + (fim - inicio)
                * limitar(proporcao, 0f, 1f);
    }

    private static float aproximar(
            float atual,
            float alvo,
            float quantidade
    ) {
        if (atual < alvo) {
            return Math.min(atual + quantidade, alvo);
        }

        return Math.max(atual - quantidade, alvo);
    }

    private static float moverAte(
            float atual,
            float alvo,
            float distanciaMaxima
    ) {
        float diferenca = alvo - atual;

        if (Math.abs(diferenca) <= distanciaMaxima) {
            return alvo;
        }

        return atual
                + Math.signum(diferenca) * distanciaMaxima;
    }

    // =========================================================
    // OBJETOS VISUAIS E BOLA
    // =========================================================

    private static class Particula {
        float x;
        float y;
        float vx;
        float vy;
        float raio;
        float vida;
        float vidaMaxima;
        int cor;
    }

    private static class Bola {
        private static final int TAMANHO_RASTRO = 14;

        float x;
        float y;

        float vx;
        float vy;

        float raio;
        float corHue;

        float giro;
        float forcaCurva;
        float tempoCurva;
        float tempoCaos;

        float pontoAtracaoX;
        float pontoAtracaoY;
        float forcaAtracao;
        float forcaOrbital;
        float tempoAtracao;

        float tempoOrbitaCritica;
        float tempoOrbitaCriticaTotal;
        float sentidoOrbitaCritica;

        float tempoCongelada;
        float direcaoQuanticaX;
        float direcaoQuanticaY;
        float tempoTurboQuantico;
        float tempoTurboSupernova;

        float tempoFantasma;
        float tempoBloqueioDuplicacao;
        float tempoCooldownPortal;

        boolean fundida;
        boolean separacaoPendente;

        float fatorVelocidadeAlvo = 1f;
        float tempoMudancaVelocidade;

        final float[] rastroX =
                new float[TAMANHO_RASTRO];

        final float[] rastroY =
                new float[TAMANHO_RASTRO];

        void preencherRastro() {
            for (int i = 0; i < TAMANHO_RASTRO; i++) {
                rastroX[i] = x;
                rastroY[i] = y;
            }
        }

        void registrarRastro() {
            for (int i = TAMANHO_RASTRO - 1;
                 i > 0;
                 i--) {

                rastroX[i] = rastroX[i - 1];
                rastroY[i] = rastroY[i - 1];
            }

            rastroX[0] = x;
            rastroY[0] = y;
        }
    }
}
