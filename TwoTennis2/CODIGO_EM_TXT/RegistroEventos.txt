package com.example.twotennis;
import java.util.Arrays;
final class RegistroEventos {
 private final boolean[] vistos;
 RegistroEventos(int quantidade) { vistos = new boolean[quantidade]; }
 void registrar(int evento) { if(evento >= 0 && evento < vistos.length) vistos[evento] = true; }
 void reiniciar() { Arrays.fill(vistos, false); }
 boolean todosSelecionadosOcorreram(boolean[] selecionados) {
  boolean algum = false;
  for(int i=0;i<selecionados.length;i++) if(selecionados[i]) {
   algum=true;
   if(i>=vistos.length || !vistos[i]) return false;
  }
  return algum;
 }
}
