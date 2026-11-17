import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.*;
import scala.Tuple2;

public class SparkPageRank {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: <graph file> <new output directory>");
        SparkConf conf = new SparkConf().setAppName("Lab3 PageRank RDD");
        try (JavaSparkContext sc = new JavaSparkContext(conf)) {
            sc.setLogLevel("WARN");
            // This lab graph is small. Normalize it once before distributing it.
            SortedMap<String,List<String>> graph = Graph.read(new BufferedReader(
                new StringReader(String.join("\n", sc.textFile(args[0]).collect()))));
            int n = graph.size();
            List<Tuple2<String,List<String>>> entries = new ArrayList<>();
            graph.forEach((page, links) -> entries.add(new Tuple2<>(page, links)));
            JavaPairRDD<String,List<String>> links = sc.parallelizePairs(entries, 2).cache();
            JavaPairRDD<String,Double> ranks = links.mapValues(x -> 1.0 / n).cache();
            for (int round = 1; round <= Graph.ROUNDS; round++) {
                JavaPairRDD<String,Tuple2<List<String>,Double>> joined = links.join(ranks).cache();
                double dangling = joined.values().filter(x -> x._1().isEmpty())
                    .map(x -> x._2()).fold(0.0, Double::sum);
                JavaPairRDD<String,Double> shares = joined.values().flatMapToPair(node -> {
                    List<Tuple2<String,Double>> output = new ArrayList<>();
                    if (!node._1().isEmpty()) {
                        double share = node._2() / node._1().size();
                        for (String target : node._1()) output.add(new Tuple2<>(target, share));
                    }
                    return output.iterator();
                }).reduceByKey(Double::sum);
                double base = (1 - Graph.DAMPING) / n + Graph.DAMPING * dangling / n;
                JavaPairRDD<String,Double> next = links.leftOuterJoin(shares)
                    .mapValues(pair -> base + Graph.DAMPING * pair._2().orElse(0.0)).cache();
                double mass = next.values().fold(0.0, Double::sum);
                System.out.printf(Locale.ROOT, "Spark round=%02d mass=%.12f%n", round, mass);
                ranks.unpersist();
                joined.unpersist();
                ranks = next;
            }
            ranks.sortByKey(true, 1).map(row -> Graph.output(row._1(), row._2())).saveAsTextFile(args[1]);
        }
    }
}
