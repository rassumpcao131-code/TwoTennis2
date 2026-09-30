package com.example.twotennis;
import org.junit.Test;
import static org.junit.Assert.*;
public class RegistroEventosTest {
 @Test public void exigeTodosSelecionadosSemContarRepeticoes() {
  RegistroEventos r=new RegistroEventos(23);
  boolean[] ativos=new boolean[23];ativos[1]=ativos[12]=ativos[22]=true;
  r.registrar(1);r.registrar(1);r.registrar(22);
  assertFalse(r.todosSelecionadosOcorreram(ativos));
  r.registrar(12);assertTrue(r.todosSelecionadosOcorreram(ativos));
  r.reiniciar();assertFalse(r.todosSelecionadosOcorreram(ativos));
 }
 @Test public void apenasUmSelecionadoJaBastaSemLimiteDeDoze() {
  RegistroEventos r=new RegistroEventos(23);boolean[] a=new boolean[23];a[20]=true;
  assertFalse(r.todosSelecionadosOcorreram(a));r.registrar(20);assertTrue(r.todosSelecionadosOcorreram(a));
 }
 @Test public void nenhumSelecionadoNaoDispara() {
  RegistroEventos r=new RegistroEventos(23);assertFalse(r.todosSelecionadosOcorreram(new boolean[23]));
 }
 @Test public void alteracaoDasConfiguracoesConsideraSelecaoAtual() {
  RegistroEventos r=new RegistroEventos(23);boolean[] a=new boolean[23];a[5]=a[8]=true;
  r.registrar(5);assertFalse(r.todosSelecionadosOcorreram(a));
  a[8]=false;assertTrue(r.todosSelecionadosOcorreram(a));
  a[15]=true;assertFalse(r.todosSelecionadosOcorreram(a));
 }
}