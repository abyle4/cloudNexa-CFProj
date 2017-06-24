package cloudnexa.cf.maker;

import java.text.NumberFormat;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class CFmaker {
	
    /**
     * The CFBuilder function takes in a line from the CSV, the desired path to the
     * output files, the customer name, and the indices of key data fields in the CSV
     *
     * @author Andrew Byle and Lucas Vitalos
     * @since 2017-04-08
     */
    public static void CFBuilder(ArrayList<ArrayList<String>> input, String fileName, String custName, int headers[]){
    	boolean isEC2 = false;
        boolean isRDS = false;
        boolean isELB = false;
        String service = input.get(headers[0]).get(0);
        service = service.toLowerCase();
            
        if(service.equals("instance")){
            isEC2 = true;
        }
        else if(service.equals("rds")){
            isRDS = true;
        }
        else if(service.equals("elb")){
            isELB = true;
        }
        else{
            return;
        }
        
        //default assumption is Linux for system OS
        boolean isWindows = false;
        String sysOS = input.get(headers[3]).get(0);
        sysOS = sysOS.toLowerCase();
            
        if(sysOS.contains("windows")){
            isWindows = true;
        }
            
        try {
            BufferedWriter bw = null;
            FileWriter  fw = null;
                
            Scanner scanner = new Scanner (new File("src/cloudnexa/cf/maker/header"));
                
            //Ensures that the header at the top of the output file is only inserted once
            //	(at the time of file creation)
            //
            boolean insertHead = false;
            ArrayList<String> temp = new ArrayList<String>();
                
            //output file
            File myfile = new File(fileName);
                
            if(!myfile.exists()){
                insertHead = true;
                myfile.createNewFile();
            }
                
            fw = new FileWriter(myfile.getAbsoluteFile(),true);
            bw = new BufferedWriter(fw);
                
            if (insertHead){
                while (scanner.hasNext()){
                    temp.add(scanner.next());
                }
                for (String str : temp){
                    bw.write(str);
                }
            }
                
            String addme = "";
            if(isEC2){
                for (int i = 0; i < 6; i++){
                    //i == 4 is SwapUtilization; skip if Windows
                    if(i == 4 && isWindows){
                        continue;
                    }
                    //i == 3 is PagingFileUtilization; skip if Linux
                    else if (i == 3 && !isWindows){
                        continue;
                    }
                            
                    //If the file has not been previously created, no comma is needed
                    //because this alarm is the first
                    //
                    if(insertHead){
                        addme = addme.concat(EC2AlarmMkr(input,i,isWindows,custName,headers));
                        insertHead = false;
                    }
                    //Otherwise, there is at least one preceding alarm in the file, so a comma is inserted
                    //
                    else{
                        addme += ",";
                        addme = addme.concat(EC2AlarmMkr(input,i,isWindows,custName,headers));
                    }
                }
            }
            else if(isRDS){
                for (int i = 0; i < 4; i++){
                    if(insertHead){
                        addme = addme.concat(RDSAlarmMkr(input,i,custName,headers,RDSInstanceMemory(input.get(headers[8]).get(0))));
                        insertHead = false;
                    }
                    else{
                        addme += ",";
                        addme = addme.concat(RDSAlarmMkr(input,i,custName,headers,RDSInstanceMemory(input.get(headers[8]).get(0))));
                    }
                }
            }
            else{
                //is ELB
                for (int i = 0; i < 2; i++){
                    if(insertHead){
                        addme = addme.concat(ELBAlarmMkr(input,i,custName,headers));
                        insertHead = false;
                    }
                    else{
                        addme += ",";
                        addme = addme.concat(ELBAlarmMkr(input,i,custName,headers));
                    }
                }
            }
                
            bw.write(addme);
                
            bw.flush();
            fw.flush();
            bw.close();
            fw.close();
        }
        catch (IOException io){
            System.out.println(io.toString());
        }
    }
	
    /**
     * The footerInsert method takes in a string with the name of the file
     * and adds the footer and any CloudFormation wrap up
     *
     * @author Andrew Byle
     * @since 2017-03-20
     *
     */
    public static void footerInsert(String fName) {

        try {
            BufferedWriter bw = null;
            FileWriter fw = null;
            File myfile = new File(fName);

            if (!myfile.exists()) {
                return;
            }

            fw = new FileWriter(myfile.getAbsoluteFile(),true);
            bw = new BufferedWriter(fw);

            //first right brace closes the "Resources" list, second closes the entire document
            String footer = "}}";

            bw.write(footer);

            bw.flush();
            fw.flush();
            bw.close();
            fw.close();
        }

        catch (IOException io) {
            System.out.println(io.toString());
        }
        return;
    }

    /**
     * The RDSInstanceMemory method takes in the RDS instance type and returns its memory in bytes.
     * For use with RDSAlarmMkr, to create Low Memory and High DB Connections alarms that have
     * thresholds appropriate to the instance type.
     *
     * @author Lucas Vitalos
     * @since 2017-04-15
     */
    public static double RDSInstanceMemory(String type){
        switch (type){
            case "db.m4.large": return 8000000000.0;

            case "db.m4.xlarge": return 16000000000.0;
            
            case "db.m4.2xlarge": return 32000000000.0;
            
            case "db.m4.4xlarge": return 64000000000.0;
            
            case "db.m4.10xlarge": return 160000000000.0;
            
            case "db.m3.medium": return 3750000000.0;
            
            case "db.m3.large": return 7500000000.0;
            
            case "db.m3.xlarge": return 15000000000.0;
            
            case "db.m3.2xlarge": return 30000000000.0;
            
            case "db.r3.large": return 15000000000.0;
            
            case "db.r3.xlarge": return 30500000000.0;
            
            case "db.r3.2xlarge": return 61000000000.0;
            
            case "db.r3.4xlarge": return 122000000000.0;
            
            case "db.r3.8xlarge": return 244000000000.0;
            
            case "db.t2.micro": return 1000000000.0;
            
            case "db.t2.small": return 2000000000.0;
            
            case "db.t2.medium": return 4000000000.0;
            
            case "db.t2.large": return 8000000000.0;
            
            default: return 0;
        }
    }
	
    /**
     * The EC2AlarmMkr method takes a line from the CSV and 4 other inputs, indicating the type
     * of alarm to make, the OS of the instance, the customer name and the indices of key data 
     * fields in the CSV
     *
     * Previously named IndAlarmMkr.
     *
     * @author Andrew Byle
     * @since 2017-04-08
     */
    public static String EC2AlarmMkr(ArrayList<ArrayList<String>> input, int casenum, boolean isWindows, String custName, int headers[]){
	String out = "";
	//instance ID stripped of non-alphanumeric characters
	//and inserted before resource key to make unique
	String instanceID = "";
	for (char c : input.get(headers[1]).get(0).toCharArray()){
	    if (c == '-') continue;
	    else instanceID += c;
	}

        String resourceName = input.get(headers[2]).get(0);
        if(resourceName == null || "".equals(resourceName)){
            resourceName = input.get(headers[1]).get(0);
        }
		
        //input.get(headers[2]).get(0) is the instance name
	switch (casenum){
	    case 0: out += "\"" + instanceID + "EC2HealthStatusCheckAlarm\"";
                    System.out.println("Adding Status Check alarm for " + resourceName);
                    break;
            case 1: out += "\"" + instanceID + "EC2MEMAlarmHigh\"";
                    System.out.println("Adding MemoryUtilization alarm for " + resourceName);
                    break;
            case 2: if (isWindows) out += "\"" + instanceID + "EC2WinVOLAlarmHigh\"";
                    else out += "\"" + instanceID + "EC2LinuxVOLAlarmHigh\"";
                    System.out.println("Adding VolumeUtilization alarm for " + resourceName);
                    break;
            case 3: out += "\"" + instanceID + "EC2PageFileAlarmHigh\"";
                    System.out.println("Adding Paging File Utilization alarm for " + resourceName);
                    break;
            case 4: out += "\"" + instanceID + "EC2SwapAlarmHigh\"";
                    System.out.println("Adding Swap Utilization alarm for " + resourceName);
                    break;
            case 5: out += "\"" + instanceID + "EC2CPUAlarmHigh\"";
                    System.out.println("Adding High CPU alarm for " + resourceName);
                    break;
            default: return out;
        }

        out += ": {\"Type\": \"AWS::CloudWatch::Alarm\",\"DeletionPolicy\":\"Retain\",\"Properties\": {";
        out += "\"AlarmName\" : \"" + custName + " - ";
        switch (casenum) {
            case 0: out += "EC2: Status Check Failed";
                    break;
            case 1: out += "EC2: MemoryUtilization";
                    break;
            case 2: out += "EC2: VolumeUtilization";
                    break;
            case 3: out += "EC2: Paging File Utilization";
                    break;
            case 4: out += "EC2: Swap Utilization";
                    break;
            case 5: out += "EC2: High CPU Utilization";
                    break;
            default: return out;
        }
        out += " - " + input.get(headers[1]).get(0) + "\",";
        out += "\"MetricName\":\"";
        switch (casenum) {
            case 0: out += "StatusCheckFailed\"";
                    break;
            case 1: out += "MemoryUtilization\"";
                    break;
            //for Windows the metric for volume utilization is VolumeUtilization; for Linux, DiskSpaceUtilization
            case 2: if (isWindows) out += "VolumeUtilization\"";
                    else out += "DiskSpaceUtilization\"";
                    break;
            case 3: out += "pagefileUtilization(c:\\\\pagefile.sys)\"";
                    break;
            case 4: out += "SwapUtilization\"";
                    break;
            case 5: out += "CPUUtilization\"";
                    break;
            default: return out;
        }
        out += ",\"Namespace\": \"";
        switch (casenum) {
            case 0: out += "AWS/EC2\"";
                    break;
            case 1: if (isWindows) out += "System/Windows\"";
	            else out += "System/Linux\"";
                    break;
            case 2: if (isWindows) out += "System/Windows\"";
                    else out += "System/Linux\"";
                    break;
            case 3: out += "System/Windows\"";
                    break;
            case 4: out += "System/Linux\"";
                    break;
            case 5: out += "AWS/EC2\"";
                    break;
            default: break;
        }
        out += ",\"Statistic\": \"Average\",\"Period\": \"";
        if (casenum == 0) out += "60\",";
        else out += "300\",";
        out += "\"EvaluationPeriods\": \"";
        if (casenum == 0) out += "5\",";
        else out += "6\",";
        out += "\"Threshold\": \"";
        switch (casenum) {
            case 0: out += "0";
                    break;
            case 1: out += "90";
                    break;
            case 2: out += "90";
                    break;
            case 3: out += "80";
                    break;
            case 4: out += "80";
                    break;
            case 5: out += "90";
                    break;
            default: break;
        }
        out += "\",\"ActionsEnabled\": true, \"AlarmActions\": [{\"Ref\": \"SNSTopicARN\"}]";
        out += ",\"OKActions\": [{\"Ref\": \"SNSTopicARN\"}]";
        out += ",\"Dimensions\": [{\"Name\": \"InstanceId\",\"Value\": \"";
	//input.get(headers[1]).get(0) is the instance ID
        out += input.get(headers[1]).get(0) + "\"";

        //adding extra dimensions for windows or linux volume utilization alarms
        if (casenum == 2){
            if (isWindows){
                //input.get(headers[6]) is the list of drive letters in use for windows instances
                for (String d : input.get(headers[6])){
                out += "},{\"Name\": \"Drive-Letter\",\"Value\": \"" + d + "\"";
                }
            }
            else{
                //input.get(headers[6]) is the list of mounted filesystems for linux instances
                //input.get(headers[7]) is the corresponding list of mount points
                //
                if (input.get(headers[6]).size() != input.get(headers[7]).size()) {
                    System.err.println("The number of mounted filesystems does not match the number of mount points for instance: " + input.get(headers[1]).get(0))
                }
                for (int i = 0; i < input.get(headers[6]).size(); i++){
                    out += "},{\"Name\": \"MountPath\",\"Value\": \"" + input.get(headers[7]).get(i) + "\"},{\"Name\": \"Filesystem\",\"Value\": \"" + input.get(headers[6]).get(i) + "\"";
                }
            }
        }
        //last two brackets close Properties and then Type, completing the Resource
        out += "}],\"ComparisonOperator\": \"GreaterThanThreshold\"}}";

        return out;
    }
	
    /**
     * The RDSAlarmMkr method takes in a line from the CSV and 3 other inputs, indicating the
     * type of alarm to make, the customer name, and the indices of key data fields in the CSV
     *
     * @author Lucas Vitalos
     * @since 2017-04-08
     */
    public static String RDSAlarmMkr(ArrayList<ArrayList<String>> input, int casenum, String custName, int headers[], double instanceMemory){
        String out = "";
    	
        //resource ID to be stripped of any non-alphanumeric characters
        //and inserted in front of the Resource key to make it unique
        String resourceID = "";
        for (char c : input.get(headers[1]).get(0).toCharArray()){
            if (!Character.isLetterOrDigit(c)) continue;
            else resourceID += c;
        }

        String resourceName = input.get(headers[2]).get(0);
        if(resourceName == null || "".equals(resourceName)){
            resourceName = input.get(headers[1]).get(0);
        }
		
        //input.get(headers[2]).get(0) is the resource name
        switch(casenum){
            case 0: out += "\"" + resourceID + "RDSHighCPUAlarm\"";
                    System.out.println("Adding High CPU alarm for " + resourceName);
                    break;
            case 1: out += "\"" + resourceID + "RDSHighDBConnectionsAlarm\"";
                    System.out.println("Adding High DB connections alarm for " + resourceName);
                    break;
            case 2: out += "\"" + resourceID + "RDSLowMemoryAlarm\"";
    	        System.out.println("Adding Low memory alarm for " + resourceName);
    	        break;
            case 3: out += "\"" + resourceID + "RDSLowStorageAlarm\"";
    	        System.out.println("Adding Low storage alarm for " + resourceName);
    	        break;
            default: return out;
        }
        out += ": {\"Type\" : \"AWS::CloudWatch::Alarm\",\"DeletionPolicy\" : \"Retain\",\"Properties\" : {";
        out += "\"ActionsEnabled\" : true, \"AlarmActions\" : [{\"Ref\": \"SNSTopicARN\"}],";
        out += "\"AlarmName\": \"" + custName + " - RDS: ";
	
        switch(casenum){
            case 0: out += "High CPU";
                    break;
            case 1: out += "High Amount of DB Connections";
        	        break;
            case 2: out += "Low Memory";
	            break;
	    case 3: out += "Low Disk Space";
	            break;
            default: return out;
        }
        out += " - " + input.get(headers[1]).get(0) + "\",";
        out += "\"ComparisonOperator\" : \"";
	    
        switch(casenum){
            case 0: out += "GreaterThanThreshold";
                    break;
            case 1: out += "GreaterThanThreshold";
                    break;
            case 2: out += "LessThanThreshold";
                    break;
            case 3: out += "LessThanThreshold";
                    break;
            default: return out;
        }
        //input.get(headers[1]).get(0) is the resource ID
        out += "\", \"Dimensions\" : [{\"Name\" : \"DBInstanceIdentifier\", \"Value\" : \"" + input.get(headers[1]).get(0) + "\"}],";
        out += "\"EvaluationPeriods\" : ";
    	
        switch(casenum){
            case 0: out += "5"; //generic quantity; number of periods
                    break;
            case 1: out += "2";
                    break;
            case 2: out += "5";
                    break;
            case 3: out += "5";
                    break;
            default: return out;
        }
        out += ", \"MetricName\" : \"";
	    
        switch(casenum){
            case 0: out += "CPUUtilization";
                    break;
            case 1: out += "DatabaseConnections";
                    break;
            case 2: out += "FreeableMemory";
                    break;
            //input.get(headers[3]).get(0) is the DB engine, for RDS instances;
            //metric name for free storage is different for Aurora
            case 3: if(input.get(headers[3]).get(0).toLowerCase().equals("aurora")) out += "FreeLocalStorage";
                    else out += "FreeStorageSpace";
                    break;
            default: return out;
        }
        out += "\", \"Namespace\" : \"AWS/RDS\", \"OKActions\" : [{\"Ref\": \"SNSTopicARN\"}],";
        out += "\"Period\" : ";
		
        switch(casenum){
            case 0: out += "300"; //seconds
                    break;
            case 1: out += "60";
                    break;
            case 2: out += "300";
                    break;
            case 3: out += "300";
                    break;
            default: return out;
        }
        out += ", \"Statistic\" : \"Average\",";
        out += "\"Threshold\" : ";
        NumberFormat thresholdFormat = NumberFormat.getInstance();
        thresholdFormat.setGroupingUsed(false);
        switch(casenum){
            case 0: out += "90.0";
                    break;
            case 1: out += thresholdFormat.format(((instanceMemory / 12582880.0) * 0.75));
                    break;
            case 2: out += thresholdFormat.format((instanceMemory / 4.0)); //512,000,000 bytes
                    break;
            case 3: out += "1000000000.0"; //1,000,000,000 bytes
                    break;
            default: return out;
        }
        out += "}}"; //close "Properties", then "Type"; this completes the Resource
        return out;
    }
	
    /**
     * The ELBAlarmMkr method takes in a line from the CSV and 3 other inputs, indicating the
     * type of alarm to make, the customer name, and the indices of key data fields in the CSV
     *
     * @author Lucas Vitalos
     * @since 2017-04-08
     */
    public static String ELBAlarmMkr(ArrayList<ArrayList<String>> input, int casenum, String custName, int headers[]){
    	String out = "";
		
        //resource ID to be stripped of any non-alphanumeric characters
        //and inserted in front of the Resource key to make it unique
        String resourceID = "";
        for (char c : input.get(headers[1]).get(0).toCharArray()){
                if (!Character.isLetterOrDigit(c)) continue;
            else resourceID += c;
        }

        String resourceName = input.get(headers[2]).get(0);
        if(resourceName == null || "".equals(resourceName)){
            resourceName = input.get(headers[1]).get(0);
        }
            
        //input.get(headers[2]).get(0) is the resource name
        switch(casenum){
            case 0: out += "\"" + resourceID + "ELBHighUnhealthyHostsAlarm\"";
                    System.out.println("Adding High Unhealthy Hosts alarm for " + resourceName);
                break;
            case 1: out += "\"" + resourceID + "ELBNoHealthyHostsAlarm\"";
                    System.out.println("Adding No Healthy Hosts alarm for " + resourceName);
                break;
            default: return out;
        }
        out += " : { \"Type\" : \"AWS::CloudWatch::Alarm\", \"DeletionPolicy\" : \"Retain\", \"Properties\" : {";
        out += "\"ActionsEnabled\" : true, \"AlarmActions\" : [{\"Ref\" : \"SNSTopicARN\"}],";
        out += "\"AlarmName\" : \"" + custName + " - ELB: ";
            
        switch(casenum){
            case 0: out += "High Unhealthy Hosts";
                    break;
            case 1: out += "No Healthy Hosts";
                    break;
            default: return out;
        }
        out += " - " + input.get(headers[1]).get(0) + "\",";
        out += "\"ComparisonOperator\" : \"";
            
        switch(casenum){
            case 0: out += "GreaterThanOrEqualToThreshold";
                    break;
            case 1: out += "LessThanThreshold";
                    break;
            default: return out;
        }
        //input.get(headers[1]).get(0) is the resource ID
        out += "\", \"Dimensions\" : [{\"Name\" : \"LoadBalancerName\", \"Value\" : \"" + input.get(headers[1]).get(0) + "\"}],";
        out += "\"EvaluationPeriods\" : 5,";
        out += "\"MetricName\" : \"";
            
        switch(casenum){
            case 0: out += "UnHealthyHostCount";
                    break;
            case 1: out += "HealthyHostCount";
                    break;
            default: return out;
        }
        out += "\", \"Namespace\" : \"AWS/ELB\", \"OKActions\" : [{\"Ref\" : \"SNSTopicARN\"}], \"Period\" : 60, \"Statistic\" : \"Average\",";
        out += "\"Threshold\" : ";
            
        switch(casenum){
            case 0: out += "1.0";
                    break;
            case 1: out += "1.0";
                    break;
            default: return out;
        }
        out += "}}"; //close "Properties", then "Type"; this completes the Resource
        return out;
    }
}
