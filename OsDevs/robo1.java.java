package osdevs;

import robocode.*;
import robocode.util.Utils;
import java.awt.Color;
import java.io.IOException;

/**
 * Robô 1 - CAÇADOR (ofensivo, ataque frontal)
 *
 * Papel no time: vai direto para cima do inimigo mais próximo, atira forte
 * e parte para a batida (ramming) quando o alvo está fraco.
 * Avisa o colega (Flanqueador) qual é o alvo escolhido.
 */
public class Cacador extends TeamRobot {

    private String alvo = null;          // nome do inimigo travado
    private double distanciaAlvo = Double.MAX_VALUE;
    private int semScan = 0;             // ticks sem ver o alvo

    @Override
    public void run() {
        setBodyColor(new Color(200, 30, 30));
        setGunColor(new Color(60, 60, 60));
        setRadarColor(Color.YELLOW);
        setBulletColor(Color.RED);

        // Corpo, canhão e radar giram de forma independente
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        setAdjustRadarForRobotTurn(true);

        while (true) {
            semScan++;
            if (semScan > 10) {
                // perdeu o alvo: volta a varrer o campo inteiro
                alvo = null;
                distanciaAlvo = Double.MAX_VALUE;
            }
            if (alvo == null) {
                setTurnRadarRight(Double.POSITIVE_INFINITY);
            }
            execute();
        }
    }

    @Override
    public void onScannedRobot(ScannedRobotEvent e) {
        // Nunca atirar no colega
        if (isTeammate(e.getName())) {
            return;
        }

        // Escolhe o alvo: o primeiro visto, ou um que esteja bem mais perto
        boolean ehOAlvo = e.getName().equals(alvo);
        if (alvo == null || ehOAlvo || e.getDistance() < distanciaAlvo - 80) {
            if (!ehOAlvo) {
                alvo = e.getName();
                avisarColega(alvo);
            }
            distanciaAlvo = e.getDistance();
            semScan = 0;
        } else {
            return; // ignora inimigos que não são o alvo
        }

        double absBearing = getHeadingRadians() + e.getBearingRadians();

        // Radar travado no alvo
        double giroRadar = Utils.normalRelativeAngle(absBearing - getRadarHeadingRadians());
        setTurnRadarRightRadians(giroRadar * 2);

        // Mira com previsão de movimento
        double potencia = escolherPotencia(e.getDistance());
        mirarEAtirar(e, absBearing, potencia);

        // Movimento ofensivo: vira para o inimigo e avança
        setTurnRightRadians(Utils.normalRelativeAngle(absBearing - getHeadingRadians()));
        if (e.getEnergy() < 20) {
            setAhead(e.getDistance() + 50);          // alvo fraco: bate nele
        } else {
            setAhead(Math.max(e.getDistance() - 100, 30)); // aproxima e mantém pressão
        }
    }

    /** Tiro forte de perto, mais fraco de longe. */
    private double escolherPotencia(double distancia) {
        double p;
        if (distancia < 150) {
            p = 3.0;
        } else if (distancia < 400) {
            p = 2.0;
        } else {
            p = 1.0;
        }
        // Se a energia está baixa, economiza
        if (getEnergy() < 15) {
            p = Math.min(p, 1.0);
        }
        return Math.min(p, getEnergy());
    }

    /** Mira linear: prevê onde o inimigo estará quando o tiro chegar. */
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
            // não prever fora do campo de batalha
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

    /** Manda o nome do alvo para o colega de time. */
    private void avisarColega(String nome) {
        try {
            broadcastMessage("ALVO:" + nome);
        } catch (IOException ex) {
            // se a mensagem falhar, o robô segue sozinho
        }
    }

    @Override
    public void onHitRobot(HitRobotEvent e) {
        if (isTeammate(e.getName())) {
            setBack(80); // saiu do caminho do colega
            return;
        }
        // Encostou no inimigo: vira o canhão e dá tiro máximo à queima-roupa
        double angulo = Utils.normalRelativeAngleDegrees(
                getHeading() + e.getBearing() - getGunHeading());
        setTurnGunRight(angulo);
        if (getEnergy() > 3) {
            setFire(3);
        }
        alvo = e.getName();
        semScan = 0;
    }

    @Override
    public void onHitWall(HitWallEvent e) {
        setBack(100);
        setTurnRight(90);
    }

    @Override
    public void onRobotDeath(RobotDeathEvent e) {
        if (e.getName().equals(alvo)) {
            alvo = null;
            distanciaAlvo = Double.MAX_VALUE;
        }
    }
}
