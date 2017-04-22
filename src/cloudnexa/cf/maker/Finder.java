package cloudnexa.cf.maker;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.text.DateFormat;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.*;
import com.amazonaws.services.s3.transfer.*;
import com.amazonaws.services.s3.model.*;

/** 
 * The Finder class holds the main function and parses lines from the CSV to send to
 * the CFmaker.CFBuilder() method
 *
 * @author Andrew Byle
 * @since 2017-04-08
 */
public class Finder {

    //specifying default separators/quotes
    private static final char DEFAULT_SEPARATOR = ',';
    private static final char DEFAULT_QUOTE = '"';

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
        System.out.println("Please enter the full path to the input CSV file (including the name): ");
        String csvFilePath = commandInput.nextLine();

        //create file object using path input by user; if the file does not exist,
        //ask the user to re-enter the path to file
        File csvFile = new File(csvFilePath);
        if (!csvFile.exists()){
            while (!csvFile.exists()){
                System.out.println("File could not be found. Please check the name and path to the file and enter again: ");
                csvFilePath = commandInput.nextLine();
                csvFile = new File(csvFilePath);
            }
        }

        //open CSV for reading
        Scanner fileReader = new Scanner(csvFile);

        /*
         * ///ask for path to output file, to be handed to CFBuilder as part of fileName (see fileNamer() in this class)
        System.out.println("Please enter the directory in which you'd like the output files to be saved: ");
        String pathToOutput = commandInput.nextLine();

        //verify that path is valid
        File outputDirectory = new File(pathToOutput);
        if (!outputDirectory.isDirectory()){
            while (!outputDirectory.isDirectory()){
                System.out.println("Directory not found -- please verify that the directory exists as entered and resubmit: ");
                pathToOutput = commandInput.nextLine();
                outputDirectory = new File(pathToOutput);
            }
        }

        //add the trailing slash to the end of the path if it is not there
        if (!pathToOutput.endsWith("/")) pathToOutput = pathToOutput.concat("/");
        */

        String pathToOutput = "./outputs/";

        //get customer name for alarm names as well as output file name
        System.out.println("Please enter the customer name: ");
        String custName = commandInput.nextLine();

        //close the command line Scanner
        commandInput.close();

        //fileList keeps track of the output files being created -- one CF
        //template will be created per region per cloud account
        ArrayList<String> fileList = new ArrayList<String>();

        //line holds a parsed line of the account review csv
        ArrayList<ArrayList<String>> line = new ArrayList<ArrayList<String>>();

        //first line read from the account review CSV is the header line; not to be processed by CFBuilder
        boolean headerLine = true;

        //default values for which column holds data
        //[resource_type,resource_id,name,platform,cloud_account,region,mounted_filesystems,mount_points]
        int[] columns = {0,1,2,3,4,5,6,7};

        //while there are lines to read from the CSV
        while (fileReader.hasNext()) {

            //parse the line
            line = parseLine(fileReader.nextLine());

            //find correct columns if this is the header line
            if (headerLine && line.size() != 0) {
                columns = headerReader(line);
            }

            //if it's not an empty line
            if (line.size() != 0) {

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
                    cloudnexa.cf.maker.CFmaker.CFBuilder(line,fileName,custName,columns);
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

        s3Uploader(fileList);
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
     * @since 2017-04-08
     */
    public static String fileNamer(ArrayList<ArrayList<String>> line,String compName,int headers[]) {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        String d = df.format(new Date());
        String region = line.get(headers[5]).get(0);
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
        String cloudAccount = line.get(headers[4]).get(0);
        fileName += parsedComp;
        fileName += "-";
        for (char c : cloudAccount.toString().toCharArray()) {
            if (c == ' ') fileName += '-';
            else if (c == '(' || c == ')') continue;
            else fileName += c;
        }
        fileName += "-";
        fileName += temp;
        fileName += ".cf";

        return fileName;
    }
	
    /**
     * The headerReader method takes in a (the first) line from the CSV
     * and outputs a list of integers that tell the program where to
     * look for data.
     *
     * @author Andrew Byle
     * @since 2017-04-08
     */
    public static int[] headerReader(ArrayList<ArrayList<String>> input) {
        int[] output = new int[9];
        int loopCount = 0;
        int spotCount = 0;
        boolean missingHeader = true;
        String compare = "";

        //Loop once for each desired column header
        while (loopCount < 9) {

            //set the column title
            switch (loopCount) {
                case 0: compare = "resource_type";
                        break;
                case 1: compare = "resource_id";
                        break;
                case 2: compare = "name";
                        break;
                case 3: compare = "platform";
                        break;
                case 4: compare = "cloud_account";
                        break;
                case 5: compare = "region";
                        break;
        		case 6: compare = "mounted_filesystems";
		            	break;
                case 7: compare = "mount_points";
                        break;
                case 8: compare = "instance_type";
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

    public static void s3Uploader(ArrayList<String> fileList) {
        String bucketName = "cnexa-cf-scripts";
        String keyName = "";
        String uploadFileName = "";

        AmazonS3 s3client = new AmazonS3Client(new ProfileCredentialsProvider());
        try {
            System.out.println("Beginning S3 file upload...\n");
            for (String s : fileList) {
                uploadFileName = s;
                keyName = s.replace("./outputs/", "");
                File file = new File(uploadFileName);
                s3client.putObject(bucketName,keyName,file);
                /*
                 * ObjectListing ol = s3client.listObjects(bucketName);
                List<S3ObjectSummary> objects = ol.getObjectSummaries();
                for (S3ObjectSummary os: objects) {
                        System.out.println("* " + os.getKey());
                } */
                System.out.println("https://s3.amazonaws.com/cnexa-cf-scripts/" + keyName);
            }
            System.out.println("Finished S3 file upload");
        }
        catch (AmazonServiceException ase) {
            System.out.println("Caught an AmazonServiceException, which " +
            		"means your request made it " +
                    "to Amazon S3, but was rejected with an error response" +
                    " for some reason.");
            System.out.println("Error Message:    " + ase.getMessage());
            System.out.println("HTTP Status Code: " + ase.getStatusCode());
            System.out.println("AWS Error Code:   " + ase.getErrorCode());
            System.out.println("Error Type:       " + ase.getErrorType());
            System.out.println("Request ID:       " + ase.getRequestId());
        }
	catch (AmazonClientException ace) {
            System.out.println("Caught an AmazonClientException, which " +
            		"means the client encountered " +
                    "an internal error while trying to " +
                    "communicate with S3, " +
                    "such as not being able to access the network.");
            System.out.println("Error Message: " + ace.getMessage());
        }
    }
}
