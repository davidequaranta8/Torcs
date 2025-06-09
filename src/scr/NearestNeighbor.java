package scr;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class NearestNeighbor {
    
    private List<Sample> trainingData;

    public NearestNeighbor(String filename) {
        this.trainingData = new ArrayList<>();
        readPointsFromCSV(filename);
    }

    private void readPointsFromCSV(String filename) {
    try (BufferedReader reader = new BufferedReader(new FileReader(filename))){        
        // Ignora esplicitamente la prima riga (intestazione)
        reader.readLine();
        
        String line;
        while ((line = reader.readLine()) != null) {
            // Crea il Sample con la riga corrente
            trainingData.add(new Sample(line));
        }
        
    } catch (IOException e) {
        e.printStackTrace();
    }
}


    public int classify(Sample targetPoint){
        if (trainingData.isEmpty()) {
            System.out.println("training set vuoto");

           return -1; 
       }

       Sample nearestNeighbor = trainingData.get(0); 
       double minDistance = targetPoint.distance(nearestNeighbor); 

       // Cerca il punto più vicino
       for (Sample point : trainingData) {
           double distance = targetPoint.distance(point);
           if (distance < minDistance) {
               minDistance = distance;
               nearestNeighbor = point;
           }
       }

       return nearestNeighbor.cls;
    }

    public List<Sample> getTrainingData() {
        return trainingData;
    }
}
