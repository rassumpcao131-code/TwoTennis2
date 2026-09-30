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

    private SeekBar barraDificuldade;
    private TextView textoDificuldade;
    private final int[] indicesEventos = {0, 1, 2, 3, 5, 6, 7, 8, 9, 10, 11};
    private SeekBar barraPontos;
    private SeekBar barraVelocidade;
    private TextView textoPontos;
    private TextView textoVelocidade;
    private Switch switchModoRapido;
    private Switch switchSons;
    private CheckBox checkCurvas;
    private CheckBox checkVariacoes;
    private CheckBox[] checksEventos;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_configuracoes);

        getWindow().setStatusBarColor(Color.rgb(1, 6, 18));
        getWindow().setNavigationBarColor(Color.rgb(18, 1, 20));

        barraDificuldade = findViewById(R.id.barraDificuldade);
        textoDificuldade = findViewById(R.id.textoDificuldade);
        barraDificuldade.setProgress(ConfiguracoesJogo.obterDificuldadeIA(this));
        barraPontos = findViewById(R.id.barraPontos);
        barraVelocidade = findViewById(R.id.barraVelocidade);
        textoPontos = findViewById(R.id.textoPontosSelecionados);
        textoVelocidade = findViewById(R.id.textoVelocidadeSelecionada);
        switchSons = findViewById(R.id.switchSons);
        switchSons.setChecked(ConfiguracoesJogo.obterSonsAtivos(this));
        switchModoRapido = findViewById(R.id.switchModoRapido);
        checkCurvas = findViewById(R.id.checkCurvas);
        checkVariacoes = findViewById(R.id.checkVariacoes);

        checksEventos = new CheckBox[]{
                findViewById(R.id.checkDuplicacao),
                findViewById(R.id.checkReversao),
                findViewById(R.id.checkTurbo),
                findViewById(R.id.checkCongelamento),
                findViewById(R.id.checkPortais),
                findViewById(R.id.checkOrbita),
                findViewById(R.id.checkCadeia),
                findViewById(R.id.checkEletrico),
                findViewById(R.id.checkFusao),
                findViewById(R.id.checkSupernova),
                findViewById(R.id.checkBolaViva)
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
            checksEventos[i].setChecked(eventos[indicesEventos[i]]);
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

        barraDificuldade.setOnSeekBarChangeListener(ouvinte);
        barraPontos.setOnSeekBarChangeListener(ouvinte);
        barraVelocidade.setOnSeekBarChangeListener(ouvinte);
    }

    private void atualizarTextos() {
        textoDificuldade.setText(ConfiguracoesJogo.nomeDificuldadeIA(barraDificuldade.getProgress()));
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
        boolean[] eventos = new boolean[ConfiguracoesJogo.TOTAL_EVENTOS];

        for (int i = 0; i < checksEventos.length; i++) {
            eventos[indicesEventos[i]] = checksEventos[i].isChecked();
        }

        ConfiguracoesJogo.salvar(
                this,
                barraPontos.getProgress() + 5,
                barraVelocidade.getProgress(),
                barraDificuldade.getProgress(),
                switchModoRapido.isChecked(),
                switchSons.isChecked(),
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
