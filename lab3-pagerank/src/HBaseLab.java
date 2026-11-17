import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import org.apache.hadoop.hbase.*;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.util.Bytes;

public final class HBaseLab implements AutoCloseable {
    private final Connection connection = ConnectionFactory.createConnection(HBaseConfiguration.create());

    public HBaseLab() throws IOException {}

    public void createTable(String tableName, String[] fields) throws IOException {
        TableName name = TableName.valueOf(tableName);
        TableDescriptorBuilder descriptor = TableDescriptorBuilder.newBuilder(name);
        Set<String> families = new LinkedHashSet<>();
        for (String field : fields) families.add(split(field)[0]);
        for (String family : families) descriptor.setColumnFamily(ColumnFamilyDescriptorBuilder.of(family));
        try (Admin admin = connection.getAdmin()) {
            if (admin.tableExists(name)) {
                if (admin.isTableEnabled(name)) admin.disableTable(name);
                admin.deleteTable(name);
            }
            admin.createTable(descriptor.build());
        }
    }

    private static String[] split(String field) {
        return field.contains(":") ? field.split(":", 2) : new String[]{field, "value"};
    }

    private Table table(String name) throws IOException {
        return connection.getTable(TableName.valueOf(name));
    }

    public void addRecord(String tableName, String row, String[] fields, String[] values) throws IOException {
        if (fields.length == 0 || fields.length != values.length)
            throw new IllegalArgumentException("Fields and values must be nonempty and have the same length");
        Put put = new Put(Bytes.toBytes(row));
        for (int i = 0; i < fields.length; i++) {
            String[] col = split(fields[i]);
            put.addColumn(Bytes.toBytes(col[0]), Bytes.toBytes(col[1]), Bytes.toBytes(values[i]));
        }
        try (Table table = table(tableName)) { table.put(put); }
    }

    public void scanColumn(String tableName, String column) throws IOException {
        String[] col = split(column);
        boolean entireFamily = !column.contains(":");
        // Unrestricted scan preserves rows that have no cell in the selected family.
        try (Table table = table(tableName); ResultScanner rows = table.getScanner(new Scan())) {
            for (Result row : rows) {
                List<String> matches = new ArrayList<>();
                for (Cell cell : row.rawCells()) {
                    String family = Bytes.toString(CellUtil.cloneFamily(cell));
                    String qualifier = Bytes.toString(CellUtil.cloneQualifier(cell));
                    if (family.equals(col[0]) && (entireFamily || qualifier.equals(col[1])))
                        matches.add(qualifier + "=" + Bytes.toString(CellUtil.cloneValue(cell)));
                }
                System.out.println(Bytes.toString(row.getRow()) + "\t"
                    + (matches.isEmpty() ? "null" : String.join(", ", matches)));
            }
        }
    }

    public void modifyData(String tableName, String row, String column) throws IOException {
        java.io.Console console = System.console();
        if (console == null) throw new IllegalStateException("Use the overload with a replacement value");
        String value = console.readLine("New value for %s: ", column);
        if (value == null) throw new java.io.EOFException("No replacement value entered");
        modifyData(tableName, row, column, value);
    }

    public void modifyData(String tableName, String row, String column, String value) throws IOException {
        addRecord(tableName, row, new String[]{column}, new String[]{value});
    }

    public void deleteRow(String tableName, String row) throws IOException {
        try (Table table = table(tableName)) { table.delete(new Delete(Bytes.toBytes(row))); }
    }

    public void deleteColumn(String tableName, String row, String column) throws IOException {
        String[] col = split(column);
        try (Table table = table(tableName)) {
            table.delete(new Delete(Bytes.toBytes(row)).addColumns(Bytes.toBytes(col[0]), Bytes.toBytes(col[1])));
        }
    }

    public long count(String name) throws IOException {
        long count = 0;
        try (Table table = table(name); ResultScanner rows = table.getScanner(new Scan().setFilter(new org.apache.hadoop.hbase.filter.FirstKeyOnlyFilter()))) {
            for (Result row : rows) count++;
        }
        return count;
    }

    public void scanAll(String name) throws IOException {
        try (Table table = table(name); ResultScanner rows = table.getScanner(new Scan())) {
            for (Result row : rows)
                for (Cell cell : row.rawCells())
                    System.out.printf("%s\t%s:%s=%s%n", Bytes.toString(row.getRow()),
                        Bytes.toString(CellUtil.cloneFamily(cell)), Bytes.toString(CellUtil.cloneQualifier(cell)),
                        Bytes.toString(CellUtil.cloneValue(cell)));
        }
    }

    public void alterFamily(String name, String family, boolean add) throws IOException {
        TableName table = TableName.valueOf(name);
        try (Admin admin = connection.getAdmin()) {
            if (add) admin.addColumnFamily(table, ColumnFamilyDescriptorBuilder.of(family));
            else admin.deleteColumnFamily(table, Bytes.toBytes(family));
            if (admin.getDescriptor(table).hasColumnFamily(Bytes.toBytes(family)) != add)
                throw new IllegalStateException("Unexpected column family state");
        }
    }

    public void truncate(String name) throws IOException {
        TableName table = TableName.valueOf(name);
        try (Admin admin = connection.getAdmin()) {
            admin.disableTable(table);
            admin.truncateTable(table, true);
        }
    }

    private String get(String name, String row, String field) throws IOException {
        String[] col = split(field);
        try (Table table = table(name)) {
            byte[] value = table.get(new Get(Bytes.toBytes(row))).getValue(Bytes.toBytes(col[0]), Bytes.toBytes(col[1]));
            return value == null ? null : Bytes.toString(value);
        }
    }

    private static void check(boolean ok, String operation) {
        if (!ok) throw new IllegalStateException(operation);
        System.out.println("PASS " + operation);
    }

    private void run() throws IOException {
        String student="lab3_java_Student", course="lab3_java_Course", sc="lab3_java_SC";
        createTable(student, new String[]{"Info:S_No", "Info:S_Sex", "Info:S_Age", "Score:Math"});
        addRecord(student, "stale", new String[]{"Info:S_No"}, new String[]{"stale"});
        createTable(student, new String[]{"Info:S_No", "Info:S_Sex", "Info:S_Age", "Score:Math"});
        check(count(student)==0, "recreate replaces an existing table");
        createTable(course, new String[]{"Info:C_Name", "Info:C_Credit"});
        createTable(sc, new String[]{"Info:SC_Sno", "Info:SC_Cno", "Info:SC_Score"});
        for (String line : Files.readAllLines(Paths.get("data/student.tsv"))) {
            String[] f=line.split("\t");
            addRecord(student, f[0], new String[]{"Info:S_No","Info:S_Sex","Info:S_Age"}, new String[]{f[1],f[2],f[3]});
        }
        for (String line : Files.readAllLines(Paths.get("data/course.tsv"))) {
            String[] f=line.split("\t");
            addRecord(course, f[0], new String[]{"Info:C_Name","Info:C_Credit"}, new String[]{f[1],f[2]});
        }
        for (String line : Files.readAllLines(Paths.get("data/sc.tsv"))) {
            String[] f=line.split("\t");
            addRecord(sc, f[0]+"#"+f[1], new String[]{"Info:SC_Sno","Info:SC_Cno","Info:SC_Score"}, f);
        }
        try (Admin admin=connection.getAdmin()) {
            System.out.println("TABLES " + Arrays.toString(admin.listTableNames()));
        }
        for (String name : new String[]{student,course,sc}) {
            System.out.println("SCAN " + name);
            scanAll(name);
        }
        check(count(student)==3 && count(course)==3 && count(sc)==6, "import row counts Student=3 Course=3 SC=6");
        addRecord(student, "Zhangsan", new String[]{"Score:Math","Score:English"}, new String[]{"86","69"});
        System.out.println("SCAN family Score"); scanColumn(student, "Score");
        System.out.println("SCAN column Score:Math"); scanColumn(student, "Score:Math");
        check(get(student,"Mary","Score:Math")==null, "missing Score:Math is null");
        modifyData(student,"Zhangsan","Score:Math","90");
        check("90".equals(get(student,"Zhangsan","Score:Math")), "modify Score:Math 86 -> 90");
        deleteColumn(student,"Zhangsan","Score:English");
        check(get(student,"Zhangsan","Score:English")==null, "delete qualifier Score:English");
        alterFamily(student,"Extra",true);
        modifyData(student,"Mary","Extra:note","lab");
        check("lab".equals(get(student,"Mary","Extra:note")), "add family Extra and qualifier note");
        alterFamily(student,"Extra",false);
        System.out.println("PASS delete family Extra");
        deleteRow(student,"Lisi");
        check(count(student)==2 && get(student,"Lisi","Info:S_No")==null, "delete row Lisi, remaining=2");
        truncate(student);
        check(count(student)==0, "truncate Student, remaining=0");
        check(count(course)==3 && count(sc)==6, "Course and SC preserved");
    }

    public void close() throws IOException { connection.close(); }
    public static void main(String[] args) throws IOException {
        try (HBaseLab lab = new HBaseLab()) { lab.run(); }
    }
}
