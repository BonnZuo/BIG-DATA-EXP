import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.*;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.*;
import org.apache.hadoop.mapreduce.*;
import org.apache.hadoop.mapreduce.lib.input.*;
import org.apache.hadoop.mapreduce.lib.output.*;

public class InvertedIndex {
    public static class IndexMapper extends Mapper<LongWritable, Text, Text, Text> {
        private final Set<String> stopwords = new HashSet<>();
        private final Pattern token = Pattern.compile("[a-z]+");
        private final Text word = new Text();
        private Text document;
        protected void setup(Context context) throws IOException {
            for (String line : Files.readAllLines(Paths.get("stopwords.txt"), StandardCharsets.UTF_8)) {
                String value = line.trim().toLowerCase(Locale.ROOT);
                if (!value.isEmpty()) stopwords.add(value);
            }
            document = new Text(((FileSplit) context.getInputSplit()).getPath().getName());
        }
        protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            Matcher matcher = token.matcher(value.toString().toLowerCase(Locale.ROOT));
            while (matcher.find()) {
                String current = matcher.group();
                if (stopwords.contains(current)) {
                    context.getCounter("Index", "Stopwords filtered").increment(1);
                } else {
                    word.set(current);
                    context.write(word, document);
                    context.getCounter("Index", "Tokens indexed").increment(1);
                }
            }
        }
    }
    public static class IndexReducer extends Reducer<Text, Text, Text, Text> {
        protected void reduce(Text word, Iterable<Text> documents, Context context) throws IOException, InterruptedException {
            Map<String, Long> counts = new TreeMap<>();
            long total = 0;
            for (Text document : documents) {
                String name = document.toString();
                counts.put(name, counts.getOrDefault(name, 0L) + 1);
                total++;
            }
            StringBuilder result = new StringBuilder();
            for (Map.Entry<String, Long> entry : counts.entrySet())
                result.append('<').append(entry.getKey()).append(',').append(entry.getValue()).append(">;");
            result.append("<total,").append(total).append(">.");
            context.write(word, new Text(result.toString()));
        }
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("Usage: InvertedIndex <input> <output> <stopwords-HDFS-path>");
            System.exit(2);
        }
        Job job = Job.getInstance(new Configuration(), "lab2-inverted-index");
        job.setJarByClass(InvertedIndex.class);
        job.setMapperClass(IndexMapper.class);
        job.setReducerClass(IndexReducer.class);
        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(Text.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);
        job.setInputFormatClass(TextInputFormat.class);
        job.setOutputFormatClass(TextOutputFormat.class);
        job.setNumReduceTasks(1);
        job.addCacheFile(new URI(args[2] + "#stopwords.txt"));
        FileInputFormat.addInputPath(job, new Path(args[0]));
        FileOutputFormat.setOutputPath(job, new Path(args[1]));
        boolean ok = job.waitForCompletion(true);
        System.out.println("JOB_ID=" + job.getJobID());
        System.out.println("JOB_SUCCESS=" + ok);
        System.exit(ok ? 0 : 1);
    }
}
