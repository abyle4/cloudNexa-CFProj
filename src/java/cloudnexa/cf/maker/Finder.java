package cloudnexa.cf.maker;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.text.DateFormat;

/** The Finder class holds the main function and identifies which alarms
 * are missing from each instance
 *
 * @author Andrew Byle
 * @since 2017-03-20
 */
public class Finder {

    //setting the defaults to false for all alarms and
    //specifying default separators/quotes
    //
    private static final char DEFAULT_SEPARATOR = ',';
    private static final char DEFAULT_QUOTE = '"';

    static boolean CPU = false;
    static boolean STATUS = false;
    static boolean MEM = false;
    static boolean PAGE = false;
    static boolean SWAP = false;
    static boolean VOL = false;

    /**
     * The main function loops through the entire csv file and
     * identifies missing alarms, then calls the function to create them.
     *
     * @author Andrew Byle
     * @since 2017-03-20
     */
    public static void main(String[] args) throws Exception {

    //Scanner for command line input (to get account review CSV file path and company name)
        Scanner commandInput = new Scanner(System.in);
        System.out.print("Please enter the full path to the account review CSV file (including the name): ");
        String csvFilePath = commandInput.nextLine();

        //create file object using path input by user; if the file does not exist,
    //ask the user to re-enter the path to file
        File csvFile = new File(csvFilePath);
        if (!csvFile.exists()){
                while (!csvFile.exists()){
                        System.out.print("File could not be found. Please check the name and path to the file and enter again: ");
                        csvFilePath = commandInput.nextLine();
                        csvFile = new File(csvFilePath);
                }
        }

        //open account review CSV for reading
        Scanner fileReader = new Scanner(csvFile);

        //ask for path to output file, to be handed to CFBuilder as part of fileName (see fileNamer() in this class)
        System.out.print("Please enter the directory in which you'd like the output files to be saved: ");
        String pathToOutput = commandInput.nextLine();

        //verify that path is valid
        File outputDirectory = new File(pathToOutput);
        if (!outputDirectory.isDirectory()){
                while (!outputDirectory.isDirectory()){
                        System.out.print("Directory not found -- please verify that the directory exists as entered and resubmit: ");
                        pathToOutput = commandInput.nextLine();
                        outputDirectory = new File(pathToOutput);
                }
        }

        //add the trailing slash to the end of the path if it is not there
        if (!pathToOutput.endsWith("/")) pathToOutput = pathToOutput.concat("/");

        //get customer name for alarm names as well as output file name
        System.out.print("Please enter the customer name: ");
        String custName = commandInput.nextLine();

        //close the command line Scanner
    commandInput.close();

        //this boolean array keeps track of whether or not an alarm has been created already
    boolean[] boolarr = new boolean[6];

        //fileList keeps track of the output files being created -- one CF
    //template will be created per region per cloud account
    ArrayList<String> fileList = new ArrayList<String>();

        //line holds a parsed line of the account review csv
    ArrayList<ArrayList<String>> line = new ArrayList<ArrayList<String>>();

        //first line read from the account review CSV is the header line; not to be processed by CFBuilder
        boolean headerLine = true;

    //default values for which column holds data
    //[alarms,InstName,InstID,region,sysOS,AcctName]
    int[] columns = {0,1,2,3,4,5};

        //while there are lines to read from the CSV
        while (fileReader.hasNext()) {

            //parse the line
        line = parseLine(fileReader.nextLine());

            //reset alarm booleans
            for (int count = 0; count < 6; count++) boolarr[count] = false;

        //find correct columns if this is the header line
        if (headerLine && line.size() != 0) {
            /**
             *
             *
             *
             *
             *
             * THIS NEEDS TO GET WORKED ON
             *
             *
             *
             */
            columns = headerReader(line);
        }




            //if it's not an empty line
        if (line.size() != 0) {

            //first column in the account review CSV contains the list
            //of created alarms for an instance; check for our standard EC2 alarms
            for (String str : line.get(columns[0])) {
                if (str.contains("Status Check Failed")) {
                    STATUS = true;
                    boolarr[0] = STATUS;
                    System.out.println("Found Status Check alarm");
                }
                if (str.contains("MemoryUtilization")) {
                    MEM = true;
                    boolarr[1] = MEM;
                    System.out.println("Found MemoryUtilization alarm");
                }
                if (str.contains("VolumeUtilization")) {
                    VOL = true;
                    boolarr[2] = VOL;
                    System.out.println("Found VolumeUtilization alarm");
                }
                if (str.contains("Paging File Utilization")) {
                    PAGE = true;
                    boolarr[3] = PAGE;
                    System.out.println("Found Paging File Utilization alarm");
                }
                if (str.contains("Swap Utilization")) {
                    SWAP = true;
                    boolarr[4] = SWAP;
                    System.out.println("Found Swap Utilization alarm");
                }
                if (str.contains("High CPU")) {
                    CPU = true;
                    boolarr[5] = CPU;
                    System.out.println("Found High CPU alarm");
                }

            }

                    //if this is not the header line, check if a file has been
            //opened for this cloud account and region and then send to
            //CFBuilder to add alarms to CF template
                if (!headerLine) {
                    String fileName = pathToOutput + fileNamer(line,custName,columns);
                    boolean inList = false;
                    for (String s : fileList) {
                        if (s.equals(fileName)) {
                            inList = true;
                            break;
                        }
                    }
                    if (!inList) {
                        fileList.add(fileName);
                    }
                    cloudnexa.cf.maker.CFmaker.CFBuilder(line,boolarr,fileName,custName,columns);
                }

                        //after first line is processed, remaining lines are not the header line
                headerLine = false;

            }
            System.out.println("\n");
        }

            //close the input file after all of the lines have been processed
        fileReader.close();

            //insert the footer into every CF template file that's been created
        for (String s : fileList) {
            cloudnexa.cf.maker.CFmaker.footerInsert(s);
        }
    }

    public static ArrayList<ArrayList<String>> parseLine(String csvLine) {
        return parseLine(csvLine, DEFAULT_SEPARATOR, DEFAULT_QUOTE);
    }

    public static ArrayList<ArrayList<String>> parseLine(String csvLine,char separator) {
        return parseLine(csvLine,separator,DEFAULT_QUOTE);
    }

    /**
     * The parseLine function takes in a line from the CSV file and,
     * using the separator and quotes passed to it (default or otherwise),
     * maps each 'spot' in the line to an ArrayList<String>.
     *
     * @author Andrew Byle
     * @since 2017-03-20
     */
    public static ArrayList<ArrayList<String>> parseLine(String csvLine,char separator,char quote) {
        //result is the parsed line to be returned
        ArrayList<ArrayList<String>> result = new ArrayList<ArrayList<String>>();

        //temp contains a value in the line, to be added to result
        ArrayList<String> temp = new ArrayList<String>();

        if (csvLine == null && csvLine.isEmpty()) {
            return result;
        }

        if (quote == ' ') {
            quote = DEFAULT_QUOTE;
        }

        if (separator == ' ') {
            separator = DEFAULT_SEPARATOR;
        }

        //curVal contains the current value, to be added to temp
        StringBuffer curVal = new StringBuffer();
        boolean inQuotes = false;
        boolean startCollectChar = false;
        boolean doubleQuotesInColumn = false;
        boolean inList = false;

        char[] chars = csvLine.toCharArray();

        //for each character in the line
        for (char ch : chars) {

            //I added this logic to be able to deal with an array of
            //alarms associated with an instance
            //
            if (ch == '[' && !inQuotes) {
                inList = true;
            }
            else if (ch == ']') {
                inList = false;
            }
            else if (inQuotes) {
                startCollectChar = true;
                if (ch == quote) {
                    inQuotes = false;
                    doubleQuotesInColumn = false;
                }
                else if (ch == '\"') {
                    if (!doubleQuotesInColumn) {
                        curVal.append(ch);
                        doubleQuotesInColumn = true;
                    }
                }
                else {
                    curVal.append(ch);
                }
            }
            else if (ch == quote) {
                inQuotes = true;

                /*if (chars[0] != '"' && quote == '\"') {
                    curVal.append('"');
                }*/

                if (startCollectChar) {
                    curVal.append('"');
                }

            }
            else if (ch == separator) {
                if (inList) {
                    temp.add(curVal.toString());
                }
                else {
                    temp.add(curVal.toString());
                    result.add(temp);
                    temp = new ArrayList<String>();
                }

                curVal = new StringBuffer();
                startCollectChar = false;

            }

            //ignore carriage returns
            else if (ch == '\r') {
                continue;
            }
            else if (ch == '\n') {
                temp = new ArrayList<String>();
                curVal = new StringBuffer();
                startCollectChar = false;
                break;
            }

            //if it's not a special character, add to current value
            else {
                curVal.append(ch);
            }
        }

        //flush current value to temp
        temp.add(curVal.toString());

        //flush temp to result
        result.add(temp);

        return result;
    }

    /**
     * The fileNamer method takes in a line from the CSV and the name of the company
     * and outputs the properly named file.
     *
     * @author Andrew Byle
     * @since 2017-03-22
     */
    public static String fileNamer(ArrayList<ArrayList<String>> line,String compName,int headers[]) {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        String d = df.format(new Date());
        String region = line.get(headers[3]).get(0);
        String fileName = "";
        String temp = "";
        for (char c : d.toString().toCharArray()) {
            if (c == ' ') fileName += '-';
            else if (c == ':') continue;
            else fileName += c;
        }
        fileName += "-";
        for (char c : region.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                temp += c;
            }
        }
        String parsedComp = "";
        for (char c : compName.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
            parsedComp += c;
            }
        }
        String cloudAccount = line.get(headers[5]).get(0);
        fileName += parsedComp;
        fileName += "-";
        for (char c : cloudAccount.toString().toCharArray()) {
            if (c == ' ') fileName += '-';
            else if (c == '(' || c == ')') continue;
            else fileName += c;
        }
        fileName += "-";
        fileName += temp;
        fileName += "-";
        for (char c : d.toString().toCharArray()) {
            if (c == ' ') fileName += '-';
        else if (c == ':') continue;
            else fileName += c;
        }
        fileName += ".cf";

        return fileName;
        }

    /**
     * The headerReader method takes in a (the first) line from the CSV
     * and outputs a list of integers that tell the program where to
     * look for data.
     *
     * @author Andrew Byle
     * @since 2017-03-27
     */
    public static int[] headerReader(ArrayList<ArrayList<String>> input) {
        int[] output = new int[6];
        int loopCount = 0;
        int spotCount = 0;
        boolean missingHeader = true;
        String compare = "";

        //Loop once for each desired column header
        while (loopCount < 6) {

            //set the column title
            switch (loopCount) {
                case 0: compare = "service";
                        break;
                case 1: compare = "flagged_resource";
                        break;
                case 2: compare = "instanceID";
                        break;
                case 3: compare = "region";
                        break;
                case 4: compare = "systemOS";
                        break;
                case 5: compare = "cloud_account";
                        break;
                default: break;
            }

            //Ensure that the compare string was initialized properly
            if (compare.equals("")) {
                System.err.println("headerReader() in Finder.class failed.");
                System.exit(1);
            }

            //check the title against the input and add it to results
            //if it exists
            for (ArrayList aL : input) {
                if (compare.equals(aL.get(0))) {
                    output[loopCount] = spotCount;
                    missingHeader = false;
                    break;
                }
                spotCount ++;
            }

            //If the header cannot be found, the program will return an error.
            if (missingHeader) {
                System.err.println("Column header not found! Please ensure CSV is properly formatted and try again...");
                System.exit(1);
            }

            //Cleaning up for the next loop
            missingHeader = true;
            loopCount ++;
            spotCount = 0;
        }

        return output;
    }

}
