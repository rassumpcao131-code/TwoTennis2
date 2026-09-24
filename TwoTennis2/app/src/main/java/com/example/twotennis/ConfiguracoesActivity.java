package com.example.twotennis;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class ConfiguracoesActivity extends AppCompatActivity {

    private SeekBar barraPontos;
    private SeekBar barraVelocidade;
    private TextView textoPontos;
    private TextView textoVelocidade;
    private Switch switchModoRapido;
    private CheckBox checkCurvas;
    private CheckBox checkVariacoes;
    private CheckBox[] checksEventos;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_configuracoes);

        getWindow().setStatusBarColor(Color.rgb(1, 6, 18));
        getWindow().setNavigationBarColor(Color.rgb(18, 1, 20));

        barraPontos = findViewById(R.id.barraPontos);
        barraVelocidade = findViewById(R.id.barraVelocidade);
        textoPontos = findViewById(R.id.textoPontosSelecionados);
        textoVelocidade = findViewById(R.id.textoVelocidadeSelecionada);
        switchModoRapido = findViewById(R.id.switchModoRapido);
        checkCurvas = findViewById(R.id.checkCurvas);
        checkVariacoes = findViewById(R.id.checkVariacoes);

        checksEventos = new CheckBox[]{
                findViewById(R.id.checkDuplicacao),
                findViewById(R.id.checkReversao),
                findViewById(R.id.checkTurbo),
                findViewById(R.id.checkCongelamento),
                findViewById(R.id.checkFantasma),
                findViewById(R.id.checkPortais),
                findViewById(R.id.checkOrbita),
                findViewById(R.id.checkCadeia),
                findViewById(R.id.checkEletrico),
                findViewById(R.id.checkFusao),
                findViewById(R.id.checkSupernova)
        };

        carregarConfiguracoes();
        configurarBarras();
    }

    @Override
    protected void onResume() {
        super.onResume();
        GeradorDeMusica.tocar(this);
    }

    @Override
    protected void onPause() {
        GeradorDeMusica.pausar();
        super.onPause();
    }

    private void carregarConfiguracoes() {
        barraPontos.setProgress(
                ConfiguracoesJogo.obterPontosMaximos(this) - 5
        );
        barraVelocidade.setProgress(
                ConfiguracoesJogo.obterNivelVelocidade(this)
        );
        switchModoRapido.setChecked(
                ConfiguracoesJogo.obterModoRapido(this)
        );
        checkCurvas.setChecked(
                ConfiguracoesJogo.obterCurvasAtivas(this)
        );
        checkVariacoes.setChecked(
                ConfiguracoesJogo.obterVariacoesAtivas(this)
        );

        boolean[] eventos = ConfiguracoesJogo.obterEventosAtivos(this);
        for (int i = 0; i < checksEventos.length; i++) {
            checksEventos[i].setChecked(eventos[i]);
        }

        atualizarTextos();
    }

    private void configurarBarras() {
        SeekBar.OnSeekBarChangeListener ouvinte =
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(
                            SeekBar seekBar,
                            int progresso,
                            boolean peloUsuario
                    ) {
                        atualizarTextos();
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                    }
                };

        barraPontos.setOnSeekBarChangeListener(ouvinte);
        barraVelocidade.setOnSeekBarChangeListener(ouvinte);
    }

    private void atualizarTextos() {
        int pontos = barraPontos.getProgress() + 5;
        textoPontos.setText(pontos + " PONTOS");
        textoVelocidade.setText(
                ConfiguracoesJogo.obterNomeVelocidade(
                        barraVelocidade.getProgress()
                )
        );
    }

    public void selecionarTodosEventos(View view) {
        for (CheckBox check : checksEventos) {
            check.setChecked(true);
        }
        checkCurvas.setChecked(true);
        checkVariacoes.setChecked(true);
    }

    public void limparEventos(View view) {
        for (CheckBox check : checksEventos) {
            check.setChecked(false);
        }
        checkCurvas.setChecked(false);
        checkVariacoes.setChecked(false);
    }

    public void salvarConfiguracoes(View view) {
        boolean[] eventos = new boolean[checksEventos.length];

        for (int i = 0; i < checksEventos.length; i++) {
            eventos[i] = checksEventos[i].isChecked();
        }

        ConfiguracoesJogo.salvar(
                this,
                barraPontos.getProgress() + 5,
                barraVelocidade.getProgress(),
                switchModoRapido.isChecked(),
                checkCurvas.isChecked(),
                checkVariacoes.isChecked(),
                eventos
        );

        Toast.makeText(
                this,
                "CONFIGURAÇÕES SALVAS",
                Toast.LENGTH_SHORT
        ).show();
        finish();
    }
}
