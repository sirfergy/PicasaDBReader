import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.csv.CSVPrinter;

public class Indexes {
	private static final String PARAM_OUTPUT_FOLDER = "output";

	private static final String PARAM_PICASA_DB_FOLDER = "folder";

	//will store the name of the folders or the name of the image file (the index in the list will be the correct index of the image file)
    ArrayList<String> names;  
    
    //will store 0xFFFFFFFF for folder, the index of the folder for image files
    ArrayList<Long> indexes;
    ArrayList<Long> originalIndexes ;
    private final File folder;
    Long folderIndex = new Long(4294967295L);
    long entries;
    
    public Indexes(File folder) {
    	names = new ArrayList<String>();  
        indexes = new ArrayList<Long>();
        originalIndexes = new ArrayList<Long>();
        this.folder = folder;
	}
    
    public void Populate() throws Exception{
    	DataInputStream din = new DataInputStream(new BufferedInputStream(new FileInputStream(new File(folder, "thumbindex.db"))));
        @SuppressWarnings("unused")
		long magic = ReadFunctions.readUnsignedInt(din); //file start with a magic sequence
        entries = ReadFunctions.readUnsignedInt(din); // then number of entries

        System.out.println("nb entries: "+entries);
        
        String path;
        long index;
        Long folderIndex = new Long(4294967295L); //0xFFFFFFFF
        
       for(int i=0;i<entries;i++){
           
            path = ReadFunctions.getString(din);  // null terminated string
            ReadFunctions.read26(din);            // followed by 26 garbaged bytes
            index = ReadFunctions.readUnsignedInt(din); // followed by the index (0xFFFFFFFF for folder)
            
            
            names.add(path);
            indexes.add((long)i);
            originalIndexes.add(index);
            
            
            if(path.equals("")){   //empty file name (deleted), change index to 0xFFFFFFFF
                indexes.set(i, folderIndex);
                continue;
            }

        
       }
        din.close();
    }
    
    public void writeCSV(File output) throws IOException{
        FileWriter fw = new FileWriter(new File(output, "indexes.csv"));
        BufferedWriter bw = new BufferedWriter(fw);
        CSVPrinter csv = new CSVPrinter(bw, PMPDB.CSV_FORMAT.withHeader("Index", "Original Indexes", "type", "Image Path"));

        for(int i=0; i<entries; i++){
            Long originalIndex = originalIndexes.get(i);
            String name = names.get(i);
            final int type;
            if(indexes.get(i).compareTo(folderIndex)!=0){ // not a folder
                type = 0;
                String folderName = names.get(indexes.get(i).intValue());
                name = folderName + name;
            }else{ // folder
                type = name.equals("") ? 2 : 1;
            }
            csv.printRecord(i, originalIndex, type, name);
        }
        csv.close();
    }
    
    @SuppressWarnings("static-access")
	public static void main(String []args) throws Exception{
    	Options options = new Options();
    	options.addOption("h","help", false, "prints the help content");
    	options.addOption(OptionBuilder.withArgName("srcFolder").hasArg().withDescription("Picasa DB folder. Default is " + EnvironmentVariables.DEFAULT_PICASA_DB_PATH).create(PARAM_PICASA_DB_FOLDER));
    	options.addOption(OptionBuilder.withArgName("outputFolder").hasArg().isRequired().withDescription("output folder").create(PARAM_OUTPUT_FOLDER));
    	
    	CommandLineParser parser = new GnuParser();
    	File folder=null;
    	File output=null;
        try {
            // parse the command line arguments
            CommandLine line = parser.parse( options, args );
            if(line.hasOption("h")){
            	HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp( "ReadThumbs" , options );
                System.exit(1);
            }
            
            folder = EnvironmentVariables.getPicasaDBFolder(line, PARAM_PICASA_DB_FOLDER);

            output = new File(EnvironmentVariables.expandEnvVars(line.getOptionValue(PARAM_OUTPUT_FOLDER)));
            if (!output.mkdirs() && !output.isDirectory()) {
                throw new Exception("couldn't create output folder:"+output);
            }
        }
        catch( ParseException exp ) {
            // oops, something went wrong
        	
            System.err.println( "Parsing failed.  Reason: " + exp.getMessage() );
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( "ReadThumbs" , options );
            System.exit(1);
        }
        
        Indexes indexes = new Indexes(folder);
        indexes.Populate();
        indexes.writeCSV(output);
    }
}
