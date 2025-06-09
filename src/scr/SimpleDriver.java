package scr;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;


public class SimpleDriver extends Controller {

	/* Costanti di cambio marcia */
	final int[] gearUp = { 5000, 6000, 6000, 6500, 7000, 0 };
	final int[] gearDown = { 0, 2500, 3000, 3000, 3500, 3500 };

	/* Constanti */
	final int stuckTime = 25;
	final float stuckAngle = (float) 0.523598775; // PI/6

	/* Costanti di accelerazione e di frenata */
	final float maxSpeedDist = 70;
	final float maxSpeed = 150;
	final float sin5 = (float) 0.08716;
	final float cos5 = (float) 0.99619;

	/* Costanti di sterzata */
	final float steerLock = (float) 0.785398;
	final float steerSensitivityOffset = (float) 80.0;
	final float wheelSensitivityCoeff = 1;

	/* Costanti del filtro ABS */
	final float wheelRadius[] = { (float) 0.3179, (float) 0.3179, (float) 0.3276, (float) 0.3276 };
	final float absSlip = (float) 2.0;
	final float absRange = (float) 3.0;
	final float absMinSpeed = (float) 3.0;

	/* Costanti da stringere */
	final float clutchMax = (float) 0.5;
	final float clutchDelta = (float) 0.05;
	final float clutchRange = (float) 0.82;
	final float clutchDeltaTime = (float) 0.02;
	final float clutchDeltaRaced = 10;
	final float clutchDec = (float) 0.01;
	final float clutchMaxModifier = (float) 1.3;
	final float clutchMaxTime = (float) 1.5;



	//private int stuck = 0;

	// current clutch
	//private float clutch = 0;

	/*AGGIUNTE*/
	private Action action;
    private static char ch = ' ';
    //private static boolean lettura = false;
    private double[] features = new double[8];
    private double angolo;
    private int classe = -1;
    private File file;
    private NearestNeighbor nn;
    private boolean guidaAutonoma;



	public SimpleDriver(boolean guidaAutonoma) {
        this.action = new Action();//VEDI SE ELIMINARE????????????????????
        this.guidaAutonoma = guidaAutonoma;//inizializzo guidaAutonoma

        if (!guidaAutonoma) {//se mi trovo nella fase di addestramento creo il file che rappresenta il dataset
            file = new File("dataset.csv");
            
			ContinuousCharReaderUI.main(null);//lancio la UI

        } else {//se sono nella fase di esecuzione istanzio il classificatore passandogli il dataset
            nn = new NearestNeighbor("dataset.csv");
        }
    }

	public static char getCh() {
        return ch;
    }

    public static void setCh(char chParam) {
        ch = chParam;
    }
/* 
    public static boolean isLettura() {
        return lettura;
    }

    public static void setLettura(boolean letturaParam) {
        lettura = letturaParam;
    }
*/

	public void reset() {
		System.out.println("Restarting the race!");

	}

	public void shutdown() {
		System.out.println("Bye bye!");
	}

	//metodo per normalizzare
	private double normalize(double value, double min, double max) {
        return (value - min) / (max - min);
    }

	private int getGear(SensorModel sensors) {
		int gear = sensors.getGear();
		double rpm = sensors.getRPM();

		// Se la marcia è 0 (N) o -1 (R) restituisce semplicemente 1
		if (gear < 1)
			return 1;

		// Se il valore di RPM dell'auto è maggiore di quello suggerito
		// sale di marcia rispetto a quella attuale
		if (gear < 6 && rpm >= gearUp[gear - 1])
			return gear + 1;
		else

		// Se il valore di RPM dell'auto è inferiore a quello suggerito
		// scala la marcia rispetto a quella attuale
		if (gear > 1 && rpm <= gearDown[gear - 1])
			return gear - 1;
		else // Altrimenti mantenere l'attuale
			return gear;
	}

	private float getSteer(SensorModel sensors) {
		/** L'angolo di sterzata viene calcolato correggendo l'angolo effettivo della vettura
		 * rispetto all'asse della pista [sensors.getAngle()] e regolando la posizione della vettura
		 * rispetto al centro della pista [sensors.getTrackPos()*0,5].
		 */
		float targetAngle = (float) (sensors.getAngleToTrackAxis() - sensors.getTrackPosition() * 0.5);
		// ad alta velocità ridurre il comando di sterzata per evitare di perdere il controllo
		if (sensors.getSpeed() > steerSensitivityOffset)
			return (float) (targetAngle
					/ (steerLock * (sensors.getSpeed() - steerSensitivityOffset) * wheelSensitivityCoeff));
		else
			return (targetAngle) / steerLock;
	}

	private float getAccel(SensorModel sensors) {
		// controlla se l'auto è fuori dalla carreggiata
		if (sensors.getTrackPosition() > -1 && sensors.getTrackPosition() < 1) {
			// lettura del sensore a +5 gradi rispetto all'asse dell'automobile
			float rxSensor = (float) sensors.getTrackEdgeSensors()[10];
			// lettura del sensore parallelo all'asse della vettura
			float sensorsensor = (float) sensors.getTrackEdgeSensors()[9];
			// lettura del sensore a -5 gradi rispetto all'asse dell'automobile
			float sxSensor = (float) sensors.getTrackEdgeSensors()[8];

			float targetSpeed;

			// Se la pista è rettilinea e abbastanza lontana da una curva, quindi va alla massima velocità
			if (sensorsensor > maxSpeedDist || (sensorsensor >= rxSensor && sensorsensor >= sxSensor))
				targetSpeed = maxSpeed;
			else {
				// In prossimità di una curva a destra
				if (rxSensor > sxSensor) {

					// Calcolo dell'"angolo" di sterzata
					float h = sensorsensor * sin5;
					float b = rxSensor - sensorsensor * cos5;
					float sinAngle = b * b / (h * h + b * b);

					// Set della velocità in base alla curva
					targetSpeed = maxSpeed * (sensorsensor * sinAngle / maxSpeedDist);
				}
				// In prossimità di una curva a sinistra
				else {
					// Calcolo dell'"angolo" di sterzata
					float h = sensorsensor * sin5;
					float b = sxSensor - sensorsensor * cos5;
					float sinAngle = b * b / (h * h + b * b);

					// eSet della velocità in base alla curva
					targetSpeed = maxSpeed * (sensorsensor * sinAngle / maxSpeedDist);
				}
			}

			/**
			 * Il comando di accelerazione/frenata viene scalato in modo esponenziale rispetto
			 * alla differenza tra velocità target e quella attuale
			 */
			return (float) (2 / (1 + Math.exp(sensors.getSpeed() - targetSpeed)) - 1);
		} else
			// Quando si esce dalla carreggiata restituisce un comando di accelerazione moderata
			return (float) 0.3;
	}

	public Action control(SensorModel sensors) {
		/*normalizzo gli input*/
		features[0] = normalize(sensors.getSpeed(), 0.0, 250);
        features[1] = normalize(sensors.getTrackPosition(), -2.0, 2.0);
        features[2] = normalize(sensors.getTrackEdgeSensors()[3], -1.0, 200);
        features[3] = normalize(sensors.getTrackEdgeSensors()[6], -1.0, 200);
        features[4] = normalize(sensors.getTrackEdgeSensors()[9], -1.0, 200);
        features[5] = normalize(sensors.getTrackEdgeSensors()[12], -1.0, 200);
        features[6] = normalize(sensors.getTrackEdgeSensors()[15], -1.0, 200);
        features[7] = normalize(sensors.getAngleToTrackAxis(), -Math.PI, Math.PI);

		angolo = sensors.getAngleToTrackAxis();


		if (guidaAutonoma) {
            classe = nn.classify(new Sample(features));
        } else {
            switch (ch) {
                case 'w': classe = 0; break;
                case 'a': classe = (features[4] < 0.2 && features[0] > 0.5) ? 1 : (features[4] < 0.5 ? 2 : 3); break;
                case 'd': classe = (features[4] < 0.2 && features[0] > 0.5) ? 4 : (features[4] < 0.5 ? 5 : 6); break;
                case 's': classe = 7; break;
                case 'r': classe = 8; break;
                case 'q': classe = 11; break; // Avanti + destra
                case 'e': classe = 10; break; // Avanti + sinistra
                case 'z': classe = 12; break; // retromarcia + sinistra
                case 'x': classe = 13; break; // retromarcia + destra
                default: classe = 9; break;
            }

          /*  if (lettura) {*/
                try (BufferedWriter bw = new BufferedWriter(new FileWriter(file, true))) {
					bw.write("Speed;DistanzaLineaCentrale;SensoreSX1;SensoreSX2;SensoreCentrale;SensoreDX1;SensoreDX2;Angolo;Classe\n");//scrivo la prima riga del file
                    for (double f : features) bw.append(f + ";");
                    bw.append(classe + "\n");
                } catch (IOException e) {
                    System.err.println("Errore nel salvataggio del CSV");
                }
            //}
        }

		handlerClass(sensors);
        if (classe == 8 || classe == 12 || classe == 13) {
            action.gear = -1;
        } else {
            action.gear = getGear(sensors);
        }
        action.brake = filterABS(sensors,(float) action.brake);
        return action;

	}

	/*Metodo gestisce l'azione che deve compiere l'auto*/
	private void handlerClass(SensorModel sensors) {
        switch (classe) {
            case 0: accelera(sensors); break;
            case 1: gira(getSteer(sensors), getAccel(sensors), 1); break;
            case 2: gira(getSteer(sensors), getAccel(sensors), 0); break;
            case 3: gira(getSteer(sensors), getAccel(sensors), 0); break;
            case 4: gira(getSteer(sensors), getAccel(sensors), 1); break;
            case 5: gira(getSteer(sensors), getAccel(sensors), 0); break;
            case 6: gira(getSteer(sensors), getAccel(sensors), 0); break;
            case 7: frena(); break;
            case 8: retromarcia(); break;
            case 9: decelera(); break;
            case 10: gira(getSteer(sensors), getAccel(sensors), 0); break; // avanti + sinistra
            case 11: gira(getSteer(sensors), getAccel(sensors), 0); break;  // avanti + destra
            case 12: action.gear = -1; gira(getSteer(sensors), getAccel(sensors), 0); break; // curva sinistra retro
            case 13: action.gear = -1; gira(getSteer(sensors), getAccel(sensors), 0); break;  // curva destra retro
        }
    }

	private void accelera(SensorModel sensors) {
        if (action.gear == -1) action.gear = 1;
        action.steering = 0;
        action.brake = 0;
        action.accelerate = getAccel(sensors);
    }

    private void gira(double sterzo, float accel, double freno) {
        action.steering = sterzo;
        action.accelerate = accel;
        action.brake = freno;
    }

    private void frena() {
        action.steering = 0;
        action.accelerate = 0;
        action.brake = 1;
    }

    private void retromarcia() {
        action.gear = -1;
        action.accelerate = 0.15;
        action.brake = 0;
        action.steering = (float) (-angolo / 0.785398);
    }

    private void decelera() {
        if (action.gear == -1) action.gear = 1;
        action.accelerate = 0;
        action.brake = 0;
        action.steering = 0;
    }


	private float filterABS(SensorModel sensors, float brake) {
		// Converte la velocità in m/s
		float speed = (float) (sensors.getSpeed() / 3.6);

		// Quando la velocità è inferiore alla velocità minima per l'abs non interviene in caso di frenata
		if (speed < absMinSpeed)
			return brake;

		// Calcola la velocità delle ruote in m/s
		float slip = 0.0f;
		for (int i = 0; i < 4; i++) {
			slip += sensors.getWheelSpinVelocity()[i] * wheelRadius[i];
		}

		// Lo slittamento è la differenza tra la velocità effettiva dell'auto e la velocità media delle ruote
		slip = speed - slip / 4.0f;

		// Quando lo slittamento è troppo elevato, si applica l'ABS
		if (slip > absSlip) {
			brake = brake - (slip - absSlip) / absRange;
		}

		// Controlla che il freno non sia negativo, altrimenti lo imposta a zero
		if (brake < 0)
			return 0;
		else
			return brake;
	}

	float clutching(SensorModel sensors, float clutch) {

		float maxClutch = clutchMax;

		// Controlla se la situazione attuale è l'inizio della gara
		if (sensors.getCurrentLapTime() < clutchDeltaTime && getStage() == Stage.RACE
				&& sensors.getDistanceRaced() < clutchDeltaRaced)
			clutch = maxClutch;

		// Regolare il valore attuale della frizione
		if (clutch > 0) {
			double delta = clutchDelta;
			if (sensors.getGear() < 2) {

				// Applicare un'uscita più forte della frizione quando la marcia è una e la corsa è appena iniziata.
				delta /= 2;
				maxClutch *= clutchMaxModifier;
				if (sensors.getCurrentLapTime() < clutchMaxTime)
					clutch = maxClutch;
			}

			// Controllare che la frizione non sia più grande dei valori massimi
			clutch = Math.min(maxClutch, clutch);

			// Se la frizione non è al massimo valore, diminuisce abbastanza rapidamente
			if (clutch != maxClutch) {
				clutch -= delta;
				clutch = Math.max((float) 0.0, clutch);
			}
			// Se la frizione è al valore massimo, diminuirla molto lentamente.
			else
				clutch -= clutchDec;
		}
		return clutch;
	}





	@Override
	public float[] initAngles() {

		float[] angles = new float[19];

		/*
		 * set angles as
		 * {-90,-75,-60,-45,-30,-20,-15,-10,-5,0,5,10,15,20,30,45,60,75,90}
		 */
		for (int i = 0; i < 5; i++) {
			angles[i] = -90 + i * 15;
			angles[18 - i] = 90 - i * 15;
		}

		for (int i = 5; i < 9; i++) {
			angles[i] = -20 + (i - 5) * 5;
			angles[18 - i] = 20 - (i - 5) * 5;
		}
		angles[9] = 0;
		return angles;
	}
}
