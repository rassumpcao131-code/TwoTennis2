package com.example.twotennis;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

public class MainActivity extends Activity {

    public static final String EXTRA_UM_JOGADOR =
            "com.example.twotennis.UM_JOGADOR";

    private gameView jogo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);

        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        setRequestedOrientation(
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        );

        boolean modoUmJogador =
                getIntent().getBooleanExtra(
                        EXTRA_UM_JOGADOR,
                        false
                );

        jogo = new gameView(this, modoUmJogador);
        FrameLayout arena = new FrameLayout(this);
        arena.addView(jogo, new FrameLayout.LayoutParams(-1, -1));
        ImageButton configuracoes = new ImageButton(this);
        configuracoes.setImageResource(R.drawable.ic_menu_arena);
        configuracoes.setBackgroundResource(R.drawable.botao_neon_ciano);
        configuracoes.setContentDescription("Pausar e abrir configurações");
        int tamanho = Math.round(48 * getResources().getDisplayMetrics().density);
        FrameLayout.LayoutParams posicao = new FrameLayout.LayoutParams(tamanho, tamanho,
                Gravity.TOP | Gravity.END);
        posicao.setMargins(0, tamanho / 6, tamanho / 6, 0);
        arena.addView(configuracoes, posicao);
        configuracoes.setOnClickListener(v -> {
            jogo.suspender();
            startActivity(new Intent(this, ConfiguracoesActivity.class));
        });
        setContentView(arena);

        ativarTelaImersiva();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (jogo != null) {
            jogo.recarregarConfiguracoes();
            jogo.retomar();
        }

        ativarTelaImersiva();
    }

    @Override
    protected void onPause() {
        if (jogo != null) {
            jogo.suspender();
        }

        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean temFoco) {
        super.onWindowFocusChanged(temFoco);

        if (temFoco) {
            ativarTelaImersiva();
        }
    }

    private void ativarTelaImersiva() {
        getWindow()
                .getDecorView()
                .setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                );
    }
}