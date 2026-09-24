package com.example.twotennis;

import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

public class telaplayer extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_telaplayer);
    }
    @Override
    protected void onResume() {
        super.onResume();
        // Garante que a música continue tocando direto quando você entrar aqui
        GeradorDeMusica.tocar(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Pausa se o usuário minimizar o app estando nesta tela
        GeradorDeMusica.pausar();
    }

    public void btn1Player(View v){
        tocarBipe();

        // Abre a próxima tela
        // Botão de 1 jogador:
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_UM_JOGADOR, true);
        startActivity(intent);
    }
    public void btn2Player(View v){
        tocarBipe();

        // Abre a próxima tela
        // Botão de 2 jogadores:
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_UM_JOGADOR, false);
        startActivity(intent);
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
