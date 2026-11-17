import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.*;
import org.apache.hadoop.io.*;
import org.apache.hadoop.mapreduce.*;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

public class MapReducePageRank {
    private static final long SCALE = 1_000_000_000_000_000L;
    enum Mass { DANGLING }

    public static class Contributions extends Mapper<LongWritable,Text,Text,Text> {
        @Override protected void map(LongWritable key, Text line, Context ctx)
                throws IOException, InterruptedException {
            String[] fields = line.toString().split("\t", -1);
            double rank = Double.parseDouble(fields[1]);
            String links = fields[2];
            ctx.write(new Text(fields[0]), new Text("L" + links));
            if (!links.isEmpty()) {
                String[] targets = links.split(",");
                Text share = new Text(Double.toString(rank / targets.length));
                for (String target : targets) ctx.write(new Text(target), share);
            }
        }
    }

    public static class Update extends Reducer<Text,Text,Text,Text> {
        @Override protected void reduce(Text page, Iterable<Text> values, Context ctx)
                throws IOException, InterruptedException {
            String links = null;
            double sum = 0;
            for (Text value : values) {
                String s = value.toString();
                if (s.startsWith("L")) links = s.substring(1);
                else sum += Double.parseDouble(s);
            }
            if (links == null) throw new IOException("Missing adjacency: " + page);
            Configuration conf = ctx.getConfiguration();
            int n = conf.getInt("lab3.nodes", 0);
            double dangling = conf.getDouble("lab3.dangling", 0);
            double rank = (1 - Graph.DAMPING) / n + Graph.DAMPING * (sum + dangling / n);
            if (links.isEmpty()) ctx.getCounter(Mass.DANGLING).increment(Math.round(rank * SCALE));
            ctx.write(page, new Text(rank + "\t" + links));
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: <graph file> <new HDFS output root>");
        Configuration conf = new Configuration();
        Path source = new Path(args[0]);
        Path root = new Path(args[1]);
        FileSystem fs = root.getFileSystem(conf);
        if (fs.exists(root)) throw new IOException("Output root already exists: " + root);
        SortedMap<String,List<String>> graph;
        try (BufferedReader input = new BufferedReader(new InputStreamReader(
                source.getFileSystem(conf).open(source), StandardCharsets.UTF_8))) {
            graph = Graph.read(input);
        }
        int n = graph.size();
        long sinks = graph.values().stream().filter(List::isEmpty).count();
        double dangling = (double) sinks / n;
        Path current = new Path(root, "initial.tsv");
        try (PrintWriter out = new PrintWriter(new OutputStreamWriter(fs.create(current, false), StandardCharsets.UTF_8))) {
            graph.forEach((page, links) -> out.println(page + "\t" + (1.0 / n) + "\t" + String.join(",", links)));
            if (out.checkError()) throw new IOException("Could not write initial ranks");
        }
        for (int round = 1; round <= Graph.ROUNDS; round++) {
            conf.setInt("lab3.nodes", n);
            conf.setDouble("lab3.dangling", dangling);
            Job job = Job.getInstance(conf, "lab3-pagerank-round-" + round);
            job.setJarByClass(MapReducePageRank.class);
            job.setMapperClass(Contributions.class);
            job.setReducerClass(Update.class);
            job.setOutputKeyClass(Text.class);
            job.setOutputValueClass(Text.class);
            job.setNumReduceTasks(1);
            Path next = new Path(root, String.format("round-%02d", round));
            FileInputFormat.addInputPath(job, current);
            FileOutputFormat.setOutputPath(job, next);
            if (!job.waitForCompletion(true)) throw new IOException("Failed iteration " + round);
            if (job.getCounters().findCounter(TaskCounter.REDUCE_OUTPUT_RECORDS).getValue() != n)
                throw new IOException("Incorrect output page count at iteration " + round);
            dangling = job.getCounters().findCounter(Mass.DANGLING).getValue() / (double) SCALE;
            System.out.printf(Locale.ROOT, "MR round=%02d job=%s pages=%d nextDangling=%.12f%n",
                              round, job.getJobID(), n, dangling);
            current = next;
        }
        try (BufferedReader input = new BufferedReader(new InputStreamReader(
                 fs.open(new Path(current, "part-r-00000")), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(new OutputStreamWriter(
                 fs.create(new Path(root, "result.txt"), false), StandardCharsets.UTF_8))) {
            String line;
            while ((line = input.readLine()) != null) {
                String[] fields = line.split("\t", -1);
                out.println(Graph.output(fields[0], Double.parseDouble(fields[1])));
            }
            if (out.checkError()) throw new IOException("Could not write result");
        }
    }
}
