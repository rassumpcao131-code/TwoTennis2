package com.example.twotennis;

import android.app.Activity;
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
        setContentView(jogo);

        ativarTelaImersiva();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (jogo != null) {
            jogo.iniciar();
        }

        ativarTelaImersiva();
    }

    @Override
    protected void onPause() {
        if (jogo != null) {
            jogo.parar();
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