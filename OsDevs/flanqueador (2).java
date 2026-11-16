package osdevs;

import robocode.*;
import robocode.util.Utils;
import java.awt.Color;

/**
 * Robô 2 - FLANQUEADOR (ofensivo, ataque pelos lados)
 *
 * Papel no time: contorna o inimigo em círculos (circle strafing) a média
 * distância, trocando de direção para desviar dos tiros. Atira forte e
 * prioriza o alvo que o Caçador avisou por mensagem.
 */
public class Flanqueador extends TeamRobot {

    private String alvo = null;          // inimigo travado
    private String preferido = null;     // alvo avisado pelo Caçador
    private int semScan = 0;
    private int direcao = 1;             // 1 = frente, -1 = ré
    private double energiaAnterior = 100;
    private int recargaParede = 0;       // evita inverter direção toda hora

    @Override
    public void run() {
        setBodyColor(new Color(255, 140, 0));
        setGunColor(new Color(60, 60, 60));
        setRadarColor(Color.WHITE);
        setBulletColor(Color.ORANGE);

        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        setAdjustRadarForRobotTurn(true);

        while (true) {
            semScan++;
            if (recargaParede > 0) {
                recargaParede--;
            }
            if (semScan > 10) {
                alvo = null;
            }
            if (alvo == null) {
                setTurnRadarRight(Double.POSITIVE_INFINITY);
            }
            evitarParedes();
            execute();
        }
    }

    /** Inverte a direção quando chega perto de uma parede. */
    private void evitarParedes() {
        double margem = 60;
        boolean perto = getX() < margem
                || getX() > getBattleFieldWidth() - margem
                || getY() < margem
                || getY() > getBattleFieldHeight() - margem;
        if (perto && recargaParede == 0) {
            direcao = -direcao;
            recargaParede = 25;
        }
    }

    @Override
    public void onScannedRobot(ScannedRobotEvent e) {
        if (isTeammate(e.getName())) {
            return;
        }

        // Prioriza o alvo do Caçador; senão, o primeiro visto
        boolean ehPreferido = e.getName().equals(preferido);
        boolean ehOAlvo = e.getName().equals(alvo);
        if (alvo == null || ehPreferido || ehOAlvo) {
            alvo = e.getName();
            semScan = 0;
        } else {
            return;
        }

        double absBearing = getHeadingRadians() + e.getBearingRadians();

        // Radar travado
        double giroRadar = Utils.normalRelativeAngle(absBearing - getRadarHeadingRadians());
        setTurnRadarRightRadians(giroRadar * 2);

        // Se o inimigo gastou energia, provavelmente atirou: inverte o movimento
        double queda = energiaAnterior - e.getEnergy();
        if (queda > 0.09 && queda <= 3.0) {
            direcao = -direcao;
        } else if (Math.random() < 0.02) {
            direcao = -direcao; // imprevisibilidade
        }
        energiaAnterior = e.getEnergy();

        // Circula o inimigo: anda de lado, corrigindo a distância
        double ajuste = 0;
        if (e.getDistance() > 250) {
            ajuste = 25;   // longe: fecha o círculo
        } else if (e.getDistance() < 150) {
            ajuste = -25;  // perto: abre o círculo
        }
        double giro = Utils.normalRelativeAngleDegrees(e.getBearing() + 90 - ajuste * direcao);
        setTurnRight(giro);
        setAhead(120 * direcao);

        // Mira e atira
        double potencia = escolherPotencia(e.getDistance());
        mirarEAtirar(e, absBearing, potencia);
    }

    private double escolherPotencia(double distancia) {
        double p;
        if (distancia < 200) {
            p = 3.0;
        } else if (distancia < 450) {
            p = 2.0;
        } else {
            p = 1.5;
        }
        if (getEnergy() < 15) {
            p = Math.min(p, 1.0);
        }
        return Math.min(p, getEnergy());
    }

    private void mirarEAtirar(ScannedRobotEvent e, double absBearing, double potencia) {
        double velocidadeTiro = 20 - 3 * potencia;
        double ex = getX() + e.getDistance() * Math.sin(absBearing);
        double ey = getY() + e.getDistance() * Math.cos(absBearing);
        double vel = e.getVelocity();
        double rumo = e.getHeadingRadians();

        double px = ex;
        double py = ey;
        for (int i = 0; i < 10; i++) {
            double dist = Math.hypot(px - getX(), py - getY());
            double tempo = dist / velocidadeTiro;
            px = ex + Math.sin(rumo) * vel * tempo;
            py = ey + Math.cos(rumo) * vel * tempo;
            px = Math.max(18, Math.min(getBattleFieldWidth() - 18, px));
            py = Math.max(18, Math.min(getBattleFieldHeight() - 18, py));
        }

        double anguloAlvo = Math.atan2(px - getX(), py - getY());
        double giroCanhao = Utils.normalRelativeAngle(anguloAlvo - getGunHeadingRadians());
        setTurnGunRightRadians(giroCanhao);

        if (getGunHeat() == 0 && Math.abs(getGunTurnRemaining()) < 10 && getEnergy() > potencia) {
            setFire(potencia);
        }
    }

    /** Recebe o alvo escolhido pelo Caçador. */
    @Override
    public void onMessageReceived(MessageEvent e) {
        Object msg = e.getMessage();
        if (msg instanceof String) {
            String texto = (String) msg;
            if (texto.startsWith("ALVO:")) {
                preferido = texto.substring(5);
                if (alvo != null && !alvo.equals(preferido)) {
                    alvo = null; // volta a varrer para achar o alvo do Caçador
                }
            }
        }
    }

    @Override
    public void onHitWall(HitWallEvent e) {
        direcao = -direcao;
        recargaParede = 25;
    }

    @Override
    public void onHitRobot(HitRobotEvent e) {
        direcao = -direcao; // sai de perto (ou do colega)
    }

    @Override
    public void onRobotDeath(RobotDeathEvent e) {
        if (e.getName().equals(alvo)) {
            alvo = null;
        }
        if (e.getName().equals(preferido)) {
            preferido = null;
        }
    }
}
