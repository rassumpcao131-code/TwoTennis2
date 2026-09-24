package com.example.twotennis;

import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

public class telamenu extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Liga a música quando o Menu aparece ou o app volta do segundo plano
        GeradorDeMusica.tocar(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Pausa a música temporariamente se o usuário sair do app (botão Home)
        GeradorDeMusica.pausar();
    }

    public void btn_iniciar(View v){
        tocarBipe();

        // Abre a próxima tela
        startActivity(new Intent(this, telaplayer.class));
    }

    public void btn_sair(View v){
        tocarBipe();
        finishAffinity();
    }

    public void btn_configuracoes(View v) {
        tocarBipe();
        startActivity(new Intent(this, ConfiguracoesActivity.class));
    }

    private void tocarBipe() {
        MediaPlayer mp = MediaPlayer.create(this, R.raw.beep);

        if (mp == null) {
            return;
        }

        mp.setVolume(1f, 1f);
        mp.setOnCompletionListener(MediaPlayer::release);
        mp.start();
    }
}
