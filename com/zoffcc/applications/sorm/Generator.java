package com.zoffcc.applications.sorm;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class Generator {
    private static final String TAG = "Generator";
    static final String Version = "0.99.0";
    static final String prefix = "_sorm_";
    static final String out_classdir = "com/zoffcc/applications/sorm/";
    static final String orma_global_in1 = "o1.txt";
    static final String orma_global_in2 = "o2.txt";
    static final String orma_global_t1 = "t1.txt";
    static final String orma_global_out = "OrmaDatabase.java";

    enum COLTYPE
    {
        INT(1), LONG(2), STRING(3), BOOLEAN(4), UNKNOWN(999);
        private int value;
        private String name;
        private COLTYPE(int value)
        {
            this.value = value;
            if (value == 1) { this.name = "INT"; };
            if (value == 2) { this.name = "LONG"; };
            if (value == 3) { this.name = "STRING"; };
            if (value == 4) { this.name = "BOOLEAN"; };
            if (value == 999) { this.name = "UNKNOWN"; };
        }
    }

    public static void main(String[] args) {
        System.out.println("Generator v" + Version);
        System.out.println("checking directory: " + args[0]);

        try
        {
            final String workdir = args[0];
            begin_orma(workdir, orma_global_out);

            for (final File fileEntry : new File(workdir).listFiles()) {
                if (!fileEntry.isDirectory()) {
                    if (fileEntry.getName().startsWith(prefix))
                    {
                        // System.out.println(fileEntry.getName());
                        generate_table(workdir, fileEntry.getName(), fileEntry.getName().substring(prefix.length()));
                    }
                }
            }

            finish_orma(workdir, orma_global_out);
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }

    static String read_text_file(final String filename_with_path)
    {
        Path filePath = Path.of(filename_with_path);
            StringBuilder contentBuilder = new StringBuilder();
        try (Stream<String> stream = Files.lines(filePath, StandardCharsets.UTF_8))
        {
            stream.forEach(s -> contentBuilder.append(s).append("\n"));
        } catch (Exception e)
        {
        }
        return contentBuilder.toString();
    }

    static void begin_orma(final String workdir, final String outfilename)
    {
        System.out.println("starting: " + workdir + File.separator + out_classdir + outfilename);
        try
        {
            File d = new File(workdir + File.separator + out_classdir);
            d.mkdirs();
            final String o1 = read_text_file(orma_global_in1);
            FileWriter fstream = new FileWriter(workdir + File.separator + out_classdir + outfilename,
                StandardCharsets.UTF_8);
            BufferedWriter out = new BufferedWriter(fstream);
            out.write(o1);
            out.newLine();
            out.close();
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }

    static void finish_orma(final String workdir, final String outfilename)
    {
        System.out.println("finishing: " + workdir + File.separator + out_classdir + outfilename);
        try
        {
            File d = new File(workdir + File.separator + out_classdir);
            d.mkdirs();
            final String o2 = read_text_file(orma_global_in2);
            FileWriter fstream = new FileWriter(workdir + File.separator + out_classdir + outfilename,
                StandardCharsets.UTF_8, true); // append!
            BufferedWriter out = new BufferedWriter(fstream);
            out.write(o2);
            out.newLine();
            out.close();
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }

    static void process_tablename(final String workdir, final String outfilename, final String tablename)
    {
        System.out.println("appending table: " + workdir + File.separator + out_classdir + outfilename);
        try
        {
            File d = new File(workdir + File.separator + out_classdir);
            d.mkdirs();
            final String t1 = read_text_file(orma_global_t1);
            FileWriter fstream = new FileWriter(workdir + File.separator + out_classdir + outfilename,
                StandardCharsets.UTF_8, true); // append!
            BufferedWriter out = new BufferedWriter(fstream);
            out.write(t1.replace("@@@TABLE@@@", tablename));
            out.newLine();
            out.close();
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }

    static void generate_table(final String workdir, final String infilename, final String outfilename)
    {
        System.out.println("generating: " + workdir + File.separator + out_classdir + outfilename);
        String table_name = "";
        BufferedReader reader;
		try {
			reader = new BufferedReader(new FileReader(workdir + File.separator + infilename));
			String line = reader.readLine();

			while (line != null) {
                if (line.contains("______@@SORMA_END@@______"))
                {
                    break;
                }
                else if (line.trim().contains("@Table"))
                {
                    line = reader.readLine();
                    while(line.trim().startsWith("@"))
                    {
                        line = reader.readLine();
                    }
                    table_name = line.trim().substring(line.trim().lastIndexOf(" ") + 1);
                    System.out.println("Table: " + table_name);
                    process_tablename(workdir, orma_global_out, table_name);
                }
                else if (line.trim().contains("@PrimaryKey("))
                {
                    line = reader.readLine();
                    while(line.trim().startsWith("@"))
                    {
                        line = reader.readLine();
                    }
                    // System.out.println("PrimaryKey: " + line.trim());
                    process_primary_key(workdir, infilename, outfilename,
                        line.trim());
                }
                else if (line.trim().contains("@Column("))
                {
                    line = reader.readLine();
                    while(line.trim().startsWith("@"))
                    {
                        line = reader.readLine();
                    }
                    // System.out.println("Column: " + line.trim());
                    process_column(workdir, infilename, outfilename,
                        line.trim());
                }
				// System.out.println(line);
				line = reader.readLine();
			}

			reader.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
    }

    static String remove_public(final String in)
    {
        if (in.trim().startsWith("public"))
        {
            return in.trim().substring("public".length()).trim();
        }
        else
        {
            return in;
        }
    }

    static String remove_type(final String in)
    {
        if (in.trim().toLowerCase().startsWith("int"))
        {
            return in.trim().substring("int".length()).trim();
        }
        else if (in.trim().toLowerCase().startsWith("long"))
        {
            return in.trim().substring("long".length()).trim();
        }
        else if (in.trim().toLowerCase().startsWith("string"))
        {
            return in.trim().substring("string".length()).trim();
        }
        else if (in.trim().toLowerCase().startsWith("boolean"))
        {
            return in.trim().substring("boolean".length()).trim();
        }
        else
        {
            return in;
        }
    }

    public static int min3(int a, int b, int c) {
        return Math.min(Math.min(a, b), c);
    }

    static String get_name(final String in)
    {
        String tmp = in.trim();
        int i1 = 999;
        int i2 = 999;
        int i3 = 999;
        try
        {
            i1 = tmp.indexOf(" ");
            if (i1<1){i1=999;}
        }
        catch(Exception e)
        {}
        try
        {
            i2 = tmp.indexOf("=");
            if (i2<1){i2=999;}
        }
        catch(Exception e)
        {}
        try
        {
            i3 = tmp.indexOf(";");
            if (i1<3){i3=999;}
        }
        catch(Exception e)
        {}

        // System.out.println(""+ i1 + " " + i2 + " " + i3 + " min=" + min3(i1, i2, i3));
        return tmp.substring(0, min3(i1, i2, i3)).trim();
    }

    static COLTYPE get_type(final String in)
    {
        if (in.trim().toLowerCase().startsWith("long"))
        {
            return COLTYPE.LONG;
        }
        else if (in.trim().toLowerCase().startsWith("int"))
        {
            return COLTYPE.INT;
        }
        else if (in.trim().toLowerCase().startsWith("string"))
        {
            return COLTYPE.STRING;
        }
        else if (in.trim().toLowerCase().startsWith("boolean"))
        {
            return COLTYPE.BOOLEAN;
        }
        return COLTYPE.UNKNOWN;
    }

    static void process_primary_key(final String workdir, final String infilename, final String outfilename, final String p)
    {
        final String p2 = remove_public(p);
        final String p3 = remove_type(p2);
        final String p4 = get_name(p3);
        System.out.println("P: " + p4 + " type: " + get_type(p2).name);
    }

    static void process_column(final String workdir, final String infilename, final String outfilename, final String c)
    {
        final String c2 = remove_public(c);
        final String c3 = remove_type(c2);
        final String c4 = get_name(c3);
        System.out.println("C: " + c4 + " type: " + get_type(c2).name);
    }
}
