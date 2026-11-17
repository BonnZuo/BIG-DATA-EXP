import java.io.*;
import java.util.*;

final class Graph {
    static final int ROUNDS = 10;
    static final double DAMPING = 0.85;

    static SortedMap<String, List<String>> read(BufferedReader input) throws IOException {
        SortedMap<String, List<String>> graph = new TreeMap<>();
        Set<String> defined = new HashSet<>();
        String line;
        while ((line = input.readLine()) != null) {
            String[] fields = line.split("\t", -1);
            if (fields.length != 2 || !defined.add(page(fields[0])))
                throw new IOException("Invalid or duplicate source: " + line);
            Set<String> links = new LinkedHashSet<>();
            if (!fields[1].isEmpty()) {
                for (String link : fields[1].split(",", -1)) {
                    links.add(page(link));
                    graph.putIfAbsent(link, Collections.emptyList());
                }
            }
            graph.put(fields[0], new ArrayList<>(links));
        }
        if (graph.isEmpty()) throw new IOException("Empty graph");
        return graph;
    }

    private static String page(String value) throws IOException {
        if (value.isEmpty() || !value.equals(value.trim()) || value.matches(".*[\\s,()].*"))
            throw new IOException("Invalid page name: " + value);
        return value;
    }

    static String output(String page, double rank) {
        return String.format(Locale.ROOT, "(%s,%.10f)", page, rank);
    }
}
