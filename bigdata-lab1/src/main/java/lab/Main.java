package lab;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.*;
import org.apache.hadoop.io.*;
import org.apache.hadoop.mapreduce.*;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

public class Main {
  static void require(boolean ok,String msg) throws IOException {if(!ok)throw new IOException(msg);}
  static void parent(FileSystem fs,Path p)throws IOException {if(p.getParent()!=null)require(fs.mkdirs(p.getParent())||fs.isDirectory(p.getParent()),"mkdir failed");}
  static void copy(InputStream in,OutputStream out)throws IOException {byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
  static void file(FileSystem fs,Path p)throws IOException {require(fs.getFileStatus(p).isFile(),"Not a file: "+p);}
  static void info(FileStatus s){System.out.printf("%s\t%d\tmtime=%s\tatime=%s\t%s%n",s.getPermission(),s.getLen(),Instant.ofEpochMilli(s.getModificationTime()),Instant.ofEpochMilli(s.getAccessTime()),s.getPath());}
  // HDFS has no creation timestamp in FileStatus. Do not label mtime as creation time.
  static void prepend(FileSystem fs,Path p,byte[] bytes)throws IOException {
    file(fs,p);FileStatus st=fs.getFileStatus(p);
    Path tmp=new Path(p.getParent(),"."+p.getName()+".new-"+UUID.randomUUID());
    Path bak=new Path(p.getParent(),"."+p.getName()+".bak-"+UUID.randomUUID());
    try {
      try(OutputStream out=fs.create(tmp,false);InputStream in=fs.open(p)){out.write(bytes);copy(in,out);}
      fs.setPermission(tmp,st.getPermission());fs.setReplication(tmp,st.getReplication());
      require(fs.rename(p,bak),"Backup failed");
      if(!fs.rename(tmp,p)){require(fs.rename(bak,p),"Rollback failed; recover "+bak);throw new IOException("Replace failed");}
      require(fs.delete(bak,false),"Replacement succeeded but backup remains: "+bak);
    } finally {if(fs.exists(tmp))fs.delete(tmp,false);}
  }
  static void hdfs(String[] a,Configuration c)throws Exception {
    if(a.length<2)throw new IllegalArgumentException("Missing arguments; see README");
    try(FileSystem fs=FileSystem.get(c)){
      String op=a[1];Path p;
      switch(op){
      case "upload":
        p=new Path(a[3]);parent(fs,p);String mode=a.length>4?a[4]:"";
        if(fs.exists(p)){file(fs,p);if(mode.isEmpty()){System.out.print("File exists: append or overwrite? ");mode=new Scanner(System.in).nextLine().trim();}require(mode.equals("append")||mode.equals("overwrite"),"Choose append or overwrite");}
        try(InputStream in=new FileInputStream(a[2]);OutputStream out=fs.exists(p)&&mode.equals("append")?fs.append(p):fs.create(p,true)){copy(in,out);}break;
      case "download":
        p=new Path(a[2]);file(fs,p);java.nio.file.Path dir=java.nio.file.Paths.get(a[3]);java.nio.file.Files.createDirectories(dir);
        String name=p.getName();int i=0;
        while(true){java.nio.file.Path dst=dir.resolve(name+(i==0?"":"."+i));try(OutputStream out=java.nio.file.Files.newOutputStream(dst,java.nio.file.StandardOpenOption.CREATE_NEW);InputStream in=fs.open(p)){copy(in,out);System.out.println(dst);break;}catch(java.nio.file.FileAlreadyExistsException e){i++;}}break;
      case "cat":p=new Path(a[2]);file(fs,p);try(InputStream in=fs.open(p)){copy(in,System.out);}break;
      case "stat":info(fs.getFileStatus(new Path(a[2])));break;
      case "list":p=new Path(a[2]);require(fs.isDirectory(p),"Not a directory");RemoteIterator<LocatedFileStatus> it=fs.listFiles(p,true);while(it.hasNext())info(it.next());break;
      case "create-file":p=new Path(a[2]);parent(fs,p);try(OutputStream out=fs.create(p,false)){}break;
      case "mkdir":p=new Path(a[2]);require(fs.mkdirs(p)||fs.isDirectory(p),"mkdir failed");break;
      case "rmdir":p=new Path(a[2]);require(fs.isDirectory(p),"Not a directory");require(fs.listStatus(p).length==0,"Directory is not empty; preserved");require(fs.delete(p,false),"Delete failed");break;
      case "append":p=new Path(a[2]);file(fs,p);byte[] b=a[4].getBytes(StandardCharsets.UTF_8);if(a[3].equals("head"))prepend(fs,p,b);else {require(a[3].equals("tail"),"Use head or tail");try(OutputStream out=fs.append(p)){out.write(b);}}break;
      case "delete-file":p=new Path(a[2]);file(fs,p);require(fs.delete(p,false),"Delete failed");break;
      case "move":p=new Path(a[2]);Path dst=new Path(a[3]);file(fs,p);require(!fs.exists(dst),"Destination exists");parent(fs,dst);require(fs.rename(p,dst),"Move failed");break;
      default:throw new IllegalArgumentException("Unknown HDFS command "+op);
      }
    }
  }
  public static class DedupMap extends Mapper<LongWritable,Text,Text,NullWritable>{
    protected void map(LongWritable k,Text v,Context c)throws IOException,InterruptedException {String s=v.toString().trim();if(!s.isEmpty())c.write(new Text(s.replaceAll("\\s+","\t")),NullWritable.get());}
  }
  public static class DedupReduce extends Reducer<Text,NullWritable,Text,NullWritable>{
    protected void reduce(Text k,Iterable<NullWritable> v,Context c)throws IOException,InterruptedException{c.write(k,NullWritable.get());}
  }
  public static class SortMap extends Mapper<LongWritable,Text,LongWritable,NullWritable>{
    protected void map(LongWritable k,Text v,Context c)throws IOException,InterruptedException{String s=v.toString().trim();if(!s.isEmpty())c.write(new LongWritable(Long.parseLong(s)),NullWritable.get());}
  }
  public static class SortReduce extends Reducer<LongWritable,NullWritable,LongWritable,LongWritable>{
    long rank=0;
    protected void reduce(LongWritable k,Iterable<NullWritable> vs,Context c)throws IOException,InterruptedException{for(NullWritable v:vs)c.write(new LongWritable(++rank),k);}
  }
  public static class FamilyMap extends Mapper<LongWritable,Text,Text,Text>{
    protected void map(LongWritable k,Text v,Context c)throws IOException,InterruptedException{String s=v.toString().trim();if(s.isEmpty())return;String[] a=s.split("\\s+");if(a.length!=2)throw new IOException("Bad relation: "+s);if(a[0].equalsIgnoreCase("child")&&a[1].equalsIgnoreCase("parent"))return;c.write(new Text(a[1]),new Text("C\t"+a[0]));c.write(new Text(a[0]),new Text("P\t"+a[1]));}
  }
  public static class FamilyReduce extends Reducer<Text,Text,Text,Text>{
    protected void reduce(Text k,Iterable<Text> vs,Context c)throws IOException,InterruptedException{Set<String> children=new TreeSet<>(),parents=new TreeSet<>();for(Text t:vs){String s=t.toString();if(s.startsWith("C\t"))children.add(s.substring(2));else parents.add(s.substring(2));}for(String ch:children)for(String pa:parents)c.write(new Text(ch),new Text(pa));}
  }
  static void mr(String[] a,Configuration conf)throws Exception{
    require(a.length==4,"Usage: dedup|sort|family input output");Job j=Job.getInstance(conf,"lab1-"+a[1]);j.setJarByClass(Main.class);j.setNumReduceTasks(1);
    switch(a[1]){
    case "dedup":j.setMapperClass(DedupMap.class);j.setReducerClass(DedupReduce.class);j.setMapOutputKeyClass(Text.class);j.setMapOutputValueClass(NullWritable.class);j.setOutputKeyClass(Text.class);j.setOutputValueClass(NullWritable.class);break;
    case "sort":j.setMapperClass(SortMap.class);j.setReducerClass(SortReduce.class);j.setMapOutputKeyClass(LongWritable.class);j.setMapOutputValueClass(NullWritable.class);j.setOutputKeyClass(LongWritable.class);j.setOutputValueClass(LongWritable.class);break;
    case "family":j.setMapperClass(FamilyMap.class);j.setReducerClass(FamilyReduce.class);j.setMapOutputKeyClass(Text.class);j.setMapOutputValueClass(Text.class);j.setOutputKeyClass(Text.class);j.setOutputValueClass(Text.class);break;
    default:throw new IllegalArgumentException("Unknown job");}
    FileInputFormat.addInputPath(j,new Path(a[2]));FileOutputFormat.setOutputPath(j,new Path(a[3]));require(j.waitForCompletion(true),"Job failed");
  }
  public static void main(String[] a)throws Exception{if(a.length==0)throw new IllegalArgumentException("See README");Configuration c=new Configuration();if(a[0].equals("hdfs"))hdfs(a,c);else if(a[0].equals("mr"))mr(a,c);else throw new IllegalArgumentException("Use hdfs or mr");}
}
