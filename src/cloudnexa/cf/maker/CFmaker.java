package cloudnexa.cf.maker;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class CFmaker {

    /**
     * The CFBuilder function takes in a line from the CSV and an array of
     * boolean values that indicate which alarms are missing
     *
     * @author Andrew Byle
     * @since 2017-03-20
     */
    public static void CFBuilder(ArrayList<ArrayList<String>> input,boolean[] arr,String fileName,String custName,int headers[]) {

        //made the sysOS variable default to Linux
        //
        boolean windowsVar = false;

        String sysOS = input.get(headers[4]).get(0);
        sysOS = sysOS.toLowerCase();

        if (sysOS.equals("windows")) {
            windowsVar = true;
        }

        try {
            BufferedWriter bw = null;
            FileWriter fw = null;

            //open the header file to read and copy to the beginning of new CF templates
            Scanner scanner = new Scanner(new File("/home/ec2-user/andrew/cf/maker/header"));

        //Making sure to not return outputs or create a file if all the alarms are there
        //
        if (arr[0] && arr[1] && arr[2] && arr[3] && arr[4] && arr[5]) {
            return;
        }

        //Ensures that the header at the top of the output file is only inserted once
        //  (at the time of file creation)
        //
        boolean insertHead = false;
        ArrayList<String> temp = new ArrayList<String>();

        //create new File object using path to output file
        File myfile = new File(fileName);

        //if the output file doesn't exist, create the file and mark it for header insertion
        if (!myfile.exists()) {
            insertHead = true;
            myfile.createNewFile();
        }

        //open the output file for writing
        fw = new FileWriter(myfile.getAbsoluteFile(),true);
        bw = new BufferedWriter(fw);

        //insert the header if needed
        if (insertHead) {
            while (scanner.hasNext()) {
                temp.add(scanner.next());
            }

            for (String str : temp) {
                bw.write(str);
            }
        }

        //addme will contain the bulk of the CF template (excluding the header)
        String addme = "";

            //check for 6 alarms and create whichever are needed
            //(keeping in mind Windows and Linux variants of certain alarms -- a
        //maximum of five alarms will be created for any individual instance)
            for (int count = 0;count < 6;count ++) {

            //if the alarm has not been created
            if (!arr[count]) {

                //count 4 is SwapUtilization; if this is a Windows instance, this alarm will not be created
                if (count == 4 && windowsVar) {
                    continue;
                }

                //count 3 is PagingFileUtilization; if this is a Linux instance, this alarm will not be created
                else if (count == 3 && !windowsVar) {
                    continue;
                }

                //If the file has not been previously created, no comma is needed
                //because this alarm is the first
                //
                if (insertHead) {
                    addme = addme.concat(IndAlarmMkr(input,count,windowsVar,custName,headers));
                    insertHead = false;
                }

                //Otherwise, there is at least one preceeding alarm in the file, so a comma is inserted
                //
                else {
                    addme += ",";
                    addme = addme.concat(IndAlarmMkr(input,count,windowsVar,custName,headers));
                }
            }
        }

            //write CF template to buffer, flush buffer to FileWriter and FileWriter to file
            //then close buffer and filewriter
            bw.write(addme);

            bw.flush();
            fw.flush();
            bw.close();
            fw.close();
        }

        catch (IOException io) {
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

                //if the file does not exist, don't try to open for writing
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
     * The IndAlarmMkr method takes in a line from the CSV and two inputs, detailing what
     * type of alarm is to be created and the OS of the instance
     *
     * @author Andrew Byle
     * @since 2017-03-20
     */
    public static String IndAlarmMkr(ArrayList<ArrayList<String>> input,int casenum,boolean isWindows,String custName,int headers[]) {
        String out = "";

            //instance ID inserted before the resource key to make unique
        String instanceID = "";
        for (char c : input.get(headers[2]).get(0).toCharArray()){
            if (c == '-') continue;
            else instanceID += c;
        }

            //input.get(1).get(0) is the instance name
        switch (casenum) {
            case 0: out += "\"" + instanceID + "HealthStatusCheckAlarm\"";
                            System.out.println("Adding Status Check alarm for " + input.get(headers[1]).get(0));
                    break;
            case 1: out += "\"" + instanceID + "MEMAlarmHigh\"";
                            System.out.println("Adding MemoryUtilization alarm for " + input.get(headers[1]).get(0));
                    break;
            case 2: if (isWindows) out += "\"" + instanceID + "WinVOLAlarmHigh\"";
                            else out += "\"" + instanceID + "LinuxVOLAlarmHigh\"";
                            System.out.println("Adding VolumeUtilization alarm for " + input.get(headers[1]).get(0));
                    break;
                case 3: out += "\"" + instanceID + "PageFileAlarmHigh\"";
                            System.out.println("Adding Paging File Utilization alarm for " + input.get(headers[1]).get(0));
                    break;
            case 4: out += "\"" + instanceID + "SwapAlarmHigh\"";
                            System.out.println("Adding Swap Utilization alarm for " + input.get(headers[1]).get(0));
                    break;
                case 5: out += "\"" + instanceID + "CPUAlarmHigh\"";
                            System.out.println("Adding High CPU alarm for " + input.get(headers[1]).get(0));
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
        out += input.get(headers[2]).get(0) + "\"";

        //adding extra dimensions for windows or linux volume utilizatoin alarms
        if (casenum == 2){
            if (isWindows){
            out += "},{\"Name\": \"Drive-Letter\",\"Value\": \"C:\"";
            }
            else{
            out += "},{\"Name\": \"MountPath\",\"Value\": \"/\"},{\"Name\": \"Filesystem\",\"Value\": \"/dev/xvda1\"";
            }
        }

        out += "}],\"ComparisonOperator\": \"GreaterThanThreshold\"}}";

        return out;
    }
}
