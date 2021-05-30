package org.matsim.velbert.analysis;

import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.opengis.feature.simple.SimpleFeature;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class ModalShareAnalysis {

    private static final CoordinateTransformation transformation = TransformationFactory.getCoordinateTransformation("EPSG:25832","EPSG:3857");

    /**Dateipfad individuell anpassen!*/
    private static final String populationFilePath = "C:\\Users\\ACER\\Desktop\\Uni\\MATSim\\Hausaufgabe_1\\Output\\it.300\\velbert-v1.0-1pct.300.plans.xml.gz";

    private static final String shapeFilePath = "C:\\Users\\ACER\\Desktop\\Uni\\MATSim\\Hausaufgabe_1\\Shapes\\OSM_PLZ_072019.shp";
    private static final String networkFilePath = "C:\\Users\\ACER\\Desktop\\Uni\\MATSim\\Hausaufgabe_1\\Output\\it.300\\velbert-v1.0-1pct.output_network.xml.gz";
    private static final ArrayList<String> plz = new ArrayList();

    private static Coord getCoord(Activity activity, Network network) {

        if (activity.getCoord() != null) {
            return activity.getCoord();
        }

        return network.getLinks().get(activity.getLinkId()).getCoord();
    }

    private static boolean isInGeometry(Coord coord, Geometry geometry) {

        var transformed = transformation.transform(coord);
        return geometry.covers(MGC.coord2Point(transformed));
    }

    private static Geometry getGeometry(String identifier, Collection<SimpleFeature> features) {
        return features.stream()
                .filter(feature -> feature.getAttribute("plz").equals(identifier))
                .map(feature -> (Geometry) feature.getDefaultGeometry())
                .findAny()
                .orElseThrow();
    }

    private static String getMainMode(Collection<Leg> legList){

        if (! (legList instanceof Collection)){
            return null;
        }

        String mainMode = new String();

        double longestDistance = 0;

        for (var leg: legList){

            double distance = leg.getRoute().getDistance();

            if (distance > longestDistance){

                mainMode = leg.getMode();

                longestDistance = distance;
            }
        }

        if (mainMode != "pt" && mainMode != "car" && mainMode != "ride" && mainMode != "walk" &&
                mainMode != "bike") {

            System.out.println(mainMode);
            return "unknown";
        }

        return mainMode;
    }

    private static int convertAndSumUp(HashMap<String,Integer> tripsPerMode){

        var tripsPerModeAsCollection = tripsPerMode.values();

        int sum = 0;

        for (int totalTripsPerMode: tripsPerModeAsCollection){
            sum += totalTripsPerMode;
        }

        return sum;
    }

    private static HashMap<String, Double> getModalShare(HashMap<String, Integer> tripsPerMode, int sumOfAllTrips){

        double temp = sumOfAllTrips; //geht irgendwie nicht anders mit dem Casten
        Double sum = temp;

        HashMap<String, Double> modalShare = new HashMap<>();

        for (Map.Entry<String, Integer> entry: tripsPerMode.entrySet()){
            modalShare.put(entry.getKey(), entry.getValue()/sum);
        }

        return modalShare;
    }


    public static void main(String[] args) {

        plz.add("42551");
        plz.add("42549");
        plz.add("42555");
        plz.add("42553");


        var features = ShapeFileReader.getAllFeatures(shapeFilePath);
        var network = NetworkUtils.readNetwork(networkFilePath);
        var population = PopulationUtils.readPopulation(populationFilePath);

        HashMap<String,Geometry> geometry = new HashMap<>();

        for (String plz: plz){

            var tempGeometry = features.stream().
                    filter(feature -> feature.getAttribute("plz").equals(plz)).
                    map(feature -> (Geometry) feature.getDefaultGeometry()).
                    findAny().
                    orElseThrow();

            geometry.put(plz, tempGeometry);
        }

        if(geometry.isEmpty()){
            System.out.println("Keine Geometriy ausgewählt!");
            return;
        } else {

            for (Map.Entry<String,Geometry> geometryEntry: geometry.entrySet()){

                System.out.println(geometryEntry.getKey());
            }
        }

        HashMap<String, Integer> tripsPerMode = new HashMap<>();

        for (var person: population.getPersons().values()){

            var plan = person.getSelectedPlan();
            var trips = TripStructureUtils.getTrips(plan);

            for (var trip: trips){

                for (String orgPlz:  plz){

                    /**check if shape covers origin activity*/
                    if (isInGeometry(trip.getOriginActivity().getCoord(),geometry.get(orgPlz))){

                        for (String destPlz: plz){

                            /**check if shape covers origin activity*/
                            if (isInGeometry(trip.getDestinationActivity().getCoord(),geometry.get(destPlz))){

                                /**using the legs to compute the main mode and increment the counter in the tripsPerMode
                                 * map*/

                                var legs = trip.getLegsOnly();
                                String mainMode = getMainMode(legs);

                                if (!tripsPerMode.containsKey(mainMode)){
                                    tripsPerMode.put(mainMode,1);
                                } else {
                                    tripsPerMode.put(mainMode,tripsPerMode.get(mainMode)+1);
                                }

                            }

                            break;
                        }

                        break;
                    }
                }
            }
        }

        int totalNumberOfTrips = convertAndSumUp(tripsPerMode);

        HashMap<String, Double> modalShare = getModalShare(tripsPerMode, totalNumberOfTrips);

        System.out.println("Trips per mode:");
        for (Map.Entry<String,Integer> entry: tripsPerMode.entrySet()){

            System.out.println(entry.getKey() + ": " + entry.getValue().toString());
        }

        System.out.println("Modal share in percent:");
        for (Map.Entry<String,Double> entry: modalShare.entrySet()){

            System.out.println(entry.getKey() + ": " + entry.getValue().toString());
        }

    }
}
