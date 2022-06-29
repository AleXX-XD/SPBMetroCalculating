import core.Line;
import core.Station;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main
{
    private static final Logger LOGGER = LogManager.getLogger(Main.class);
    private static final Marker INPUT_HISTORY_MARKER = MarkerManager.getMarker("INPUT_HISTORY");
    private static final Marker INVALID_STATIONS_MARKER = MarkerManager.getMarker("INVALID_STATIONS");
    private static final Marker EXCEPTION_MARKER = MarkerManager.getMarker("EXCEPTION");

    private static final String dataFile = "map.json";
    private static Scanner scanner;

    private static StationIndex stationIndex;

    public static void main(String[] args)
    {
        RouteCalculator calculator = getRouteCalculator();

        System.out.println("Программа расчёта маршрутов метрополитена Санкт-Петербурга\n");
        scanner = new Scanner(System.in);
        boolean exit = false;
        try {

        while(!exit) {
            String menu = "Выберите номер команды: \n" +
                    "0. Выйти из программы \n" +
                    "1. Рассчитать маршрут между станциями";
            System.out.println(menu);
            String command = scanner.nextLine();
            switch (command.trim()) {
                case "0":
                    System.out.println("*** Программа завершена ***");
                    exit = true;
                    break;
                case "1":
                    Station from = takeStation("Введите станцию отправления:");
                    Station to = takeStation("Введите станцию назначения:");

                    List<Station> route = calculator.getShortestRoute(from, to);
                    System.out.println("Маршрут:");
                    printRoute(route);

                    System.out.println("Длительность: " +
                            RouteCalculator.calculateDuration(route) + " минут \n");
                    break;
                default:
                    System.out.println("*** Введена не верная команда! ***\n" + menu);
            }
        }
        } catch (IllegalArgumentException ex) {
            LOGGER.info(EXCEPTION_MARKER, "Ошибка: ", ex);
        }
    }

    private static RouteCalculator getRouteCalculator()
    {
        createStationIndex();
        return new RouteCalculator(stationIndex);
    }

    private static void printRoute(List<Station> route)
    {
        Station previousStation = null;
        for(Station station : route)
        {
            if(previousStation != null)
            {
                Line prevLine = previousStation.getLine();
                Line nextLine = station.getLine();
                if(!prevLine.equals(nextLine))
                {
                    System.out.println("\tПереход на станцию " +
                            station.getName() + " (" + nextLine.getName() + " линия)");
                }
            }
            System.out.println("\t" + station.getName());
            previousStation = station;
        }
    }

    private static Station takeStation(String message)
    {
        for(;;)
        {
            System.out.println(message);
            String line = scanner.nextLine().trim();
            Station station = stationIndex.getStation(line);
            if(station != null) {
                LOGGER.info(INPUT_HISTORY_MARKER, "Пользователь ввел станцию: {}", station);
                return station;
            }
            LOGGER.info(INVALID_STATIONS_MARKER, "Несуществующая станция: {}", line);
            System.out.println("Станция не найдена :(");
        }
    }

    private static void createStationIndex()
    {
        stationIndex = new StationIndex();
        try
        {
            JSONParser parser = new JSONParser();
            JSONObject jsonData = (JSONObject) parser.parse(getJsonFile());

            JSONArray linesArray = (JSONArray) jsonData.get("lines");
            parseLines(linesArray);

            JSONObject stationsObject = (JSONObject) jsonData.get("stations");
            parseStations(stationsObject);

            JSONArray connectionsArray = (JSONArray) jsonData.get("connections");
            parseConnections(connectionsArray);
        }
        catch(Exception ex) {
            ex.printStackTrace();
        }
    }

    private static void parseConnections(JSONArray connectionsArray)
    {
        connectionsArray.forEach(connectionObject ->
        {
            JSONArray connection = (JSONArray) connectionObject;
            List<Station> connectionStations = new ArrayList<>();
            connection.forEach(item ->
            {
                JSONObject itemObject = (JSONObject) item;
                int lineNumber = ((Long) itemObject.get("line")).intValue();
                String stationName = (String) itemObject.get("station");

                Station station = stationIndex.getStation(stationName, lineNumber);
                if(station == null)
                {
                    throw new IllegalArgumentException("core.Station " +
                            stationName + " on line " + lineNumber + " not found");
                }
                connectionStations.add(station);
            });
            stationIndex.addConnection(connectionStations);
        });
    }

    private static void parseStations(JSONObject stationsObject)
    {
        stationsObject.keySet().forEach(lineNumberObject ->
        {
            int lineNumber = Integer.parseInt((String) lineNumberObject);
            Line line = stationIndex.getLine(lineNumber);
            JSONArray stationsArray = (JSONArray) stationsObject.get(lineNumberObject);
            stationsArray.forEach(stationObject ->
            {
                Station station = new Station((String) stationObject, line);
                stationIndex.addStation(station);
                line.addStation(station);
            });
        });
    }

    private static void parseLines(JSONArray linesArray)
    {
        linesArray.forEach(lineObject -> {
            JSONObject lineJsonObject = (JSONObject) lineObject;
            Line line = new Line(
                    ((Long) lineJsonObject.get("number")).intValue(),
                    (String) lineJsonObject.get("name")
            );
            stationIndex.addLine(line);
        });
    }

    private static String getJsonFile()
    {
        StringBuilder builder = new StringBuilder();
        try {
            InputStream stream = Main.class.getResourceAsStream(dataFile);
            int bufferSize = 1024;
            char[] buffer = new char[bufferSize];
            assert stream != null;
            Reader in = new InputStreamReader(stream, StandardCharsets.UTF_8);
            for (int numRead; (numRead = in.read(buffer, 0, buffer.length)) > 0; ) {
                builder.append(buffer, 0, numRead);
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        return builder.toString();
    }
}
