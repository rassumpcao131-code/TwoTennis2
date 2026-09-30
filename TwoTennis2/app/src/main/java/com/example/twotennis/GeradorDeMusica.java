package com.example.twotennis;

import android.content.Context;
import android.media.MediaPlayer;

public class GeradorDeMusica {
    private static MediaPlayer player;

    public static void tocar(Context context) {
        if (player == null) {
            player = MediaPlayer.create(context, R.raw.menu);
            player.setLooping(true);

            player.setVolume(0.1f, 0.1f);
        }
        if (!player.isPlaying()) {
            player.start();
        }
    }

    public static void pausar() {
        if (player != null && player.isPlaying()) {
            player.pause();
        }
    }

    public static void parar() {
        if (player != null) {
            player.stop();
            player.release();
            player = null;
        }
    }
}