package com.example.twotennis;
/** Buffer de dois segundos, sem listas ou novas alocacoes por quadro. */
final class HistoricoBola {
 private static final int CAPACIDADE = 64;
 private final float[] xs=new float[CAPACIDADE], ys=new float[CAPACIDADE];
 private int proximo, quantidade, ultimoRebobinar;
 private float acumulado;
 void registrar(float x,float y,float dt) {
  acumulado+=dt;
  if(acumulado<1f/30f) return;
  acumulado%=1f/30f;
  xs[proximo]=x;ys[proximo]=y;
  proximo=(proximo+1)%CAPACIDADE;quantidade=Math.min(CAPACIDADE,quantidade+1);
 }
 boolean iniciar() {
  if(quantidade<30) return false;
  ultimoRebobinar=(proximo-1+CAPACIDADE)%CAPACIDADE;
  return true;
 }
 float x(float tempo) { return xs[indice(tempo)]; }
 float y(float tempo) { return ys[indice(tempo)]; }
 private int indice(float tempo) {
  int idade=Math.min(quantidade-1,(int)(tempo*30));
  return (ultimoRebobinar-idade+CAPACIDADE)%CAPACIDADE;
 }
 float duracao() { return Math.min(2f,(quantidade-1)/30f); }
 void limpar() { quantidade=proximo=0;acumulado=0; }
}
