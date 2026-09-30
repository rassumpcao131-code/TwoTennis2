package com.example.twotennis;
import org.junit.Test;
import java.util.Random;
import static org.junit.Assert.*;

public class MovimentoVivoTest {
 @Test public void duracaoELimitesIndependemDoFps() {
  for(int fps:new int[]{30,60,120}) for(int seed=0;seed<100;seed++) {
   MovimentoVivo m=new MovimentoVivo();m.iniciar(200,400);
   float tempo=0;Random r=new Random(seed);
   while(m.ativo() && tempo<11f) {
    m.atualizar(1f/fps,1.75f,m.vx,m.vy,r);tempo+=1f/fps;
    float speed=(float)Math.hypot(m.vx,m.vy);
    assertTrue(Float.isFinite(speed));assertTrue(speed<=1100*1.75f+.1f);
   }
   assertFalse(m.ativo());assertEquals(10f,tempo,.04f);
  }
 }
 @Test public void incluiRetasHorizontaisEQuatroDirecoes() {
  MovimentoVivo m=new MovimentoVivo();m.iniciar(0,400);Random r=new Random(31);
  boolean e=false,d=false,c=false,b=false,h=false;
  for(int n=0;n<600;n++) {
   m.atualizar(1f/60,1,m.vx,m.vy,r);
   e|=m.vx < -50;d|=m.vx > 50;c|=m.vy < -50;b|=m.vy > 50;
   h|=Math.abs(m.vy)<.01 && Math.abs(m.vx)>50;
  }
  assertTrue(e&&d&&c&&b&&h);
 }
 @Test public void permiteCancelarAoDesativarEvento() {
  MovimentoVivo m=new MovimentoVivo();m.iniciar(30,40);m.cancelar();assertFalse(m.ativo());
 }
}
