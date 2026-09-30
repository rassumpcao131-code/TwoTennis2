package com.example.twotennis;
import org.junit.Test;
import static org.junit.Assert.*;
public class HistoricoBolaTest {
 @Test public void rebobinaComBufferLimitadoEDoisSegundos() {
  HistoricoBola h=new HistoricoBola();
  assertFalse(h.iniciar());
  for(int i=0;i<240;i++) h.registrar(i,-i,1f/60f);
  assertTrue(h.iniciar());assertEquals(2f,h.duracao(),.01f);
  assertTrue(h.x(1)<h.x(0));assertEquals(-h.x(1),h.y(1),.01f);
  assertEquals(h.x(3),h.x(100),.01f);
  h.limpar();assertFalse(h.iniciar());
 }
}