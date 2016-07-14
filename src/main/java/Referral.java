package src.main.java;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

import com.sforce.soap.partner.Connector;
import com.sforce.soap.partner.DeleteResult;
import com.sforce.soap.partner.PartnerConnection;
import com.sforce.soap.partner.QueryResult;
import com.sforce.soap.partner.SaveResult;
import com.sforce.soap.partner.Error;
import com.sforce.soap.partner.sobject.SObject;
import com.sforce.ws.ConnectionException;
import com.sforce.ws.ConnectorConfig;

import org.apache.log4j.Logger;

public class Referral {
    private static final Logger log = Logger.getLogger(Referral.class.getName());
    private static final String USERNAME = "mtran@211sandiego.org";
    private static final String PASSWORD = "m1nh@211KsmlvVA4mvtI6YwzKZOLjbKF9";
    private static final String ACCOUNT_TYPE_ID = "001d000001wbCmZAAU"; // ALL CLIENTS.
    private static final String CONTACT_RECORD_TYPE_ID = "012d0000000hQPPAA2"; // Client.
    private static final String SHARP_RECORD_TYPE_ID = "012d0000000hQlHAAU";

    static void usage() {
        System.err.println("");
        System.err.println("usage: java -jar SharpReferral.jar <referral.pdf> <owner>");
        System.err.println("");
        System.exit(-1);
    }

    public static void main(String[] args) {
        if (args.length == 0 || args.length < 2) {
            usage();
        }

        try {
            // Establish connection to Salesforce.
        	ConnectorConfig config = new ConnectorConfig();
        	config.setUsername(USERNAME);
        	config.setPassword(PASSWORD);
        	//config.setTraceMessage(true);

            PartnerConnection connection = Connector.newConnection(config);
            // @debug.
    		log.info("Auth EndPoint: " + config.getAuthEndpoint() + "\n");
    		log.info("Service EndPoint: " + config.getServiceEndpoint() + "\n");
    		log.info("Username: " + config.getUsername() + "\n");
    		log.info("SessionId: " + config.getSessionId() + "\n");

            // Check to make sure referral document exists.
            String fileName = args[0];
            log.info("Reading Sharp referral document (" + fileName + ")...\n");
            File f = new File(fileName);
            if (!f.exists()) {
                log.error("Sharp referral document not found!\n");
                System.exit(-1);
            }

            // Check to make sure owner is valid.
            String owner = args[1];
            String ownerId = queryUser(connection, owner);
            if (ownerId == null) {
                log.error("Invalid owner name!");
                System.exit(-1);
            }

            // Parse referral info.
            ReferralData data = parseReferralInfo(fileName);

            // Check to see if contact has been added?
            String contactId = null;
            SObject contact = queryContact(connection, data);
            if (contact == null) {
                // Add new contact.
                contactId = createContact(connection, ownerId, data);
            }
            else {
                // Update existing contact.
                contactId = contact.getId();
                updateContact(connection, contactId, data);
            }

            // Populate referral info.
            if (contactId != null) {
                createReferral(connection, ownerId, contactId, data);
            }
        }
        catch (IOException ioe) {
            log.error(ioe.getMessage());
            ioe.printStackTrace();
            System.exit(-1);
        }
    	catch (ConnectionException ce) {
            log.error(ce.getMessage());
            ce.printStackTrace();
            System.exit(-1);
    	}
        catch (Exception e) {
            log.error(e.getMessage());
            e.printStackTrace();
            System.exit(-1);
        }
    }

    private static ReferralData parseReferralInfo(String fileName) throws Exception {
        ReferralData data = new ReferralData();
        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");
        String[] keys = {"Referral Information",
                         "Organization:",
                         "Referral Type:",
                         "Reason:",
                         "Referral Comments",
                         "Name:",
                         "Date of Birth:",
                         "Address:",
                         "Alt:",
                         "Marital Status:",
                         "Race:",
                         "Religion:",
                         "Contact 1:",
                         "Contact 2:",
                         "Admission Date:",
                         "Discharge Date:",
                         "Patient Class:",
                         "Diagnosis:",
                         "Admitting",
                         "Attending"};

        // Parse pdf document.
        PdfReader reader = new PdfReader(fileName);
        int pages = reader.getNumberOfPages();
        log.info("PDF has " + pages + " pages.\n");
        PdfTextExtractor extractor = new PdfTextExtractor(reader);
        for (int i = 1; i <= pages; i++) {
            String content = extractor.getTextFromPage(i);
            // Replace "weird" characters.  Order important.
            //content = content.replaceAll(" ", "");
            content = content.replaceAll("\\xA0", " ");
            content = content.replaceAll("\\xAD", "-");
            log.info("Page " + i + " content:\n" + content + "\n");

            // Parse content.
            log.info("Page " + i + " content:\n");
            String[] lines = content.split("\\r?\\n");
            for (int j = 0; j < lines.length; j++) {
                String line = lines[j];

                // Find matching key word.
                boolean keyFound = false;
                int k;
                for (k = 0; k < keys.length; k++) {
                    if (line.contains(keys[k])) {
                        keyFound = true;
                        break;
                    }
                }
                if (keyFound) {
                    // Parse key data.
                    StringBuilder sb = new StringBuilder();
                    String nextLine = "";
                    Pattern pattern = null;
                    Matcher matcher = null;
                    switch (k) {
                        case 0: // Referral #, MRN.
                            String[] info = line.split("-");
                            data.number = info[1].substring(11).trim();
                            data.mrn = info[2].substring(5).trim();
                            log.info("Referral #: " + data.number + "\n");
                            log.info("MRN: " + data.mrn + "\n");
                            break;
                        case 1: // Facility.
                            if (lines.length >= (j+1)) {
                                data.facility = lines[++j];
                                log.info("Sending Organization: " + data.facility + "\n");
                            }
                            break;
                        case 2: // Referral type.
                            int index = line.indexOf("Referral Type:");
                            data.referralType = line.substring(index+15).trim();
                            log.info("Referral Type: " + data.referralType + "\n");
                            break;
                        case 3: // Reason.
                            // Reason starts from next line to referral comments.
                            if (lines.length >= (j+1)) {
                                nextLine = lines[++j];
                                while (nextLine != null && !nextLine.contains("Referral Comments")) {
                                    if (nextLine.trim().length() > 0) {
                                        sb.append(nextLine + "\n");
                                    }
                                    if (lines.length >= (j+1)) {
                                        nextLine = null;
                                    }
                                    else {
                                        nextLine = lines[++j];
                                    }
                                }
                                data.reason = sb.toString();
                                log.info("Reason: " + data.reason + "\n");
                                j--; // Move back 1 line.
                            }
                            break;
                        case 4: // Comments.
                            // Comments starts from next line to sender's last activity.
                            if (lines.length >= (j+1)) {
                                nextLine = lines[++j];
                                while (nextLine != null && !nextLine.contains("Sender's last")) {
                                    if (nextLine.trim().length() > 0) {
                                        sb.append(nextLine + "\n");
                                    }
                                    if (lines.length >= (j+1)) {
                                        nextLine = null;
                                    }
                                    else {
                                        nextLine = lines[++j];
                                    }
                                }
                                data.comments = sb.toString();
                                log.info("Referral Comments: " + data.comments + "\n");
                                j--; // Move back 1 line.
                            }
                            break;
                        case 5: // Name.
                            int nameIndex = line.indexOf("Name:");
                            int mrnIndex = line.indexOf("MRN:");
                            String ptName = line.substring(nameIndex+6, mrnIndex);
                            String[] parts = ptName.trim().split(" ");
                            if (parts.length == 3) {
                                data.firstName = parts[0];
                                data.lastName = parts[2];
                            }
                            else {
                                data.firstName = parts[0];
                                data.lastName = parts[1];
                            }
                            log.info("Patient Name: " + data.firstName + " " + data.lastName + "\n");
                            break;
                        case 6: // DoB, gender.
                            int dobIndex = line.indexOf("Date of Birth:");
                            int genderIndex = line.indexOf("Gender:");
                            String sDob = line.substring(dobIndex+15, dobIndex+26);
                            data.dob = sdf.parse(sDob.trim());
                            data.gender = line.substring(genderIndex+8).trim();
                            log.info("Date of Birth: " + data.dob.toString() + "\n");
                            log.info("Gender: " + data.gender + "\n");
                            break;
                        case 7: // Address.
                            int addrIndex = line.indexOf("Address:");
                            data.address1 = line.substring(addrIndex+9).trim();
                            j++;
                            String[] part1s = lines[j].trim().split(",");
                            data.city = part1s[0].trim();
                            String[] part2s = part1s[1].trim().split(" ");
                            data.state = part2s[0].trim();
                            data.zip = part2s[1].trim();
                            log.info("Address: " + data.address1 + ", " + data.city +
                                     ", " + data.state + " " + data.zip + "\n");
                            break;
                        case 8: // Home, work, mobile phones.
                            int phone = 1;
                            if (lines.length >= (j+1)) {
                                nextLine = lines[++j];
                                while (nextLine != null && !nextLine.contains("Marital Status")) {
                                    if (phone == 1) {
                                        data.homePhone = nextLine.trim();
                                    }
                                    else if (phone == 2) {
                                        data.workPhone = nextLine.trim();
                                    }
                                    else {
                                        data.mobilePhone = nextLine.trim();
                                    }
                                    if (lines.length >= (j+1)) {
                                        nextLine = null;
                                    }
                                    else {
                                        nextLine = lines[++j];
                                    }
                                    phone++;
                                }
                                log.info("Home Phone: " + data.homePhone + "\n");
                                log.info("Work Phone: " + data.workPhone + "\n");
                                log.info("Mobile Phone: " + data.mobilePhone + "\n");
                                j--; // Move back 1 line.
                            }
                            break;
                        case 9: // Marital status, SSN.
                            int msIndex = line.indexOf("Marital Status:");
                            int ssnIndex = line.indexOf("SSN:");
                            data.maritalStatus  = line.substring(msIndex+16, ssnIndex).trim();
                            data.ssn = line.substring(ssnIndex+5).trim();
                            log.info("Marital Status: " + data.maritalStatus + "\n");
                            log.info("SSN: " + data.ssn + "\n");
                            break;
                        case 10: // Race.
                            int raceIndex = line.indexOf("Race:");
                            int race2Index = line.indexOf("Race 2:");
                            data.race = line.substring(raceIndex+6, race2Index);
                            log.info("Race: " + data.race.trim() + "\n");
                            break;
                        case 11: // Religion.
                            int relIndex = line.indexOf("Religion:");
                            data.religion = line.substring(relIndex+10);
                            log.info("Religion: " + data.religion.trim() + "\n");
                            break;
                        case 12: // Emergency contact 1.
                            // Contact 1 starts from next line to contact 2.
                            if (lines.length >= (j+1)) {
                                nextLine = lines[++j];
                                while (nextLine != null && !nextLine.contains("Emergency")) {
                                    if (nextLine.trim().length() > 0) {
                                        sb.append(nextLine + " ");
                                    }
                                    if (lines.length >= (j+1)) {
                                        nextLine = null;
                                    }
                                    else {
                                        nextLine = lines[++j];
                                    }
                                }
                                data.contact1 = sb.toString();
                                log.info("Emergency Contact 1: " + data.contact1 + "\n");
                                j--; // Move back 1 line.

                                pattern = Pattern.compile("\\(\\d{3}\\).*?\\d{3}.*?\\d{4}");
                                matcher = pattern.matcher(data.contact1);
                                if (matcher.find()) {
                                    log.info("Emergency Phone 1: " + matcher.group(0) + "\n");
                                }
                            }
                            break;
                        case 13: // Emergency contact 2.
                            // Contact 2 starts from next line to contact 2.
                            if (lines.length >= (j+1)) {
                                nextLine = lines[++j];
                                while (nextLine != null && !nextLine.contains("Allscripts")) {
                                    if (nextLine.trim().length() > 0) {
                                        sb.append(nextLine + " ");
                                    }

                                    // End of page 1?
                                    if (lines.length >= (j+1)) {
                                        nextLine = null;
                                    }
                                    else {
                                        nextLine = lines[++j];
                                    }
                                }
                                data.contact2 = sb.toString();
                                log.info("Emergency Contact 2: " + data.contact2 + "\n");
                                j--; // Move back 1 line.

                                pattern = Pattern.compile("\\(\\d{3}\\).*?\\d{3}.*?\\d{4}");
                                matcher = pattern.matcher(data.contact1);
                                if (matcher.find()) {
                                    log.info("Emergency Phone 2: " + matcher.group(0) + "\n");
                                }
                            }
                            break;
                        case 14: // Admission date.
                            int admisIndex = line.indexOf("Admission Date:");
                            String sAdmission = line.substring(admisIndex+16).trim();
                            data.admissionDate = sdf.parse(sAdmission);
                            log.info("Admission Date: " + data.admissionDate + "\n");
                            break;
                        case 15: // Discharge date.
                            if (lines.length >= (j+1)) {
                                nextLine = lines[++j];
                                int firstBlankIndex = nextLine.indexOf(" ");
                                String sDischarge = nextLine.substring(0, firstBlankIndex).trim();
                                data.dischargeDate = sdf.parse(sDischarge);
                                log.info("Discharge Date: " + data.dischargeDate + "\n");
                            }
                            break;
                        case 16: // Patient class.
                            int ptClassIndex = line.indexOf("Patient Class:");
                            int sourceIndex = line.indexOf("Admit Source:");
                            data.ptClass = line.substring(ptClassIndex+15, sourceIndex).trim();
                            log.info("Patient Class: " + data.ptClass + "\n");
                            break;
                        case 17: // Primary diagnosis.
                            // Diagnosis starts from next line to admitting physician.
                            if (lines.length >= (j+1)) {
                                nextLine = lines[++j];
                                while (nextLine != null && !nextLine.contains("Admitting")) {
                                    if (nextLine.trim().length() > 0) {
                                        sb.append(nextLine + "\n");
                                    }
                                    if (lines.length >= (j+1)) {
                                        nextLine = null;
                                    }
                                    else {
                                        nextLine = lines[++j];
                                    }
                                }
                                data.primaryDiagnosis = sb.toString();
                                log.info("Primary Diagnosis: " + data.primaryDiagnosis + "\n");
                                j--; // Move back 1 line.
                            }
                            break;
                        case 18: // Admitting physician.
                            if (lines.length >= (j+2)) {
                                j += 2;
                                data.admittingPhysician = lines[j].trim();
                                log.info("Admitting Physician: " + data.admittingPhysician + "\n");
                            }
                            break;
                        case 19: // Attending physician.
                            if (lines.length >= (j+2)) {
                                j += 2;
                                data.attendingPhysician = lines[j].trim();
                                log.info("Admitting Physician: " + data.attendingPhysician + "\n");
                            }
                            break;
                        default:
                            break;
                    }
                }
            }
        }
        reader.close();
        return data;
    }

    private static String queryUser(PartnerConnection conn, String name) {
        log.info("Querying user " + name + "...");
        String userId = null;
        if (name == null || name.trim().length() <= 0) {
            return userId;
        }

        // Parse user first and last name.
        String[] parts = name.split(" ");

    	try {
    		StringBuilder sb = new StringBuilder();
    		sb.append("SELECT Id, FirstName, LastName, Account.Name ");
    		sb.append("FROM User ");
    		sb.append("WHERE FirstName = '" + parts[0] + "' ");
    		sb.append("  AND LastName = '" + parts[1] + "'");

    		QueryResult queryResults = conn.query(sb.toString());
    		if (queryResults.getSize() > 0) {
    			for (SObject s: queryResults.getRecords()) {
                    userId = s.getId();
    			}
    		}
    	}
    	catch (Exception e) {
    		e.printStackTrace();
    	}
        return userId;
    }

    private static SObject queryContact(PartnerConnection conn, ReferralData data) {
        SObject result = null;
    	log.info("Querying contact " + data.firstName + " " + data.lastName + "...\n");

        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
    		StringBuilder sb = new StringBuilder();
    		sb.append("SELECT Id, FirstName, LastName, Birthdate, AccountId ");
    		sb.append("FROM Contact ");
    		sb.append("WHERE AccountId != NULL ");
    		sb.append("  AND FirstName = '" + SqlString.encode(data.firstName) + "' ");
    		sb.append("  AND LastName = '" + SqlString.encode(data.lastName) + "' ");
    		sb.append("  AND Birthdate = " + sdf.format(data.dob));

    		QueryResult queryResults = conn.query(sb.toString());
    		if (queryResults.getSize() > 0) {
                result = queryResults.getRecords()[0];
    		}
    	}
    	catch (Exception e) {
    		e.printStackTrace();
    	}
        return result;
    }

    private static String createContact(PartnerConnection conn, String ownerId,
                                        ReferralData data) {
        String contactId = null;
    	log.info("Creating new contact " + data.firstName + " " + data.lastName + "...\n");

    	SObject[] records = new SObject[1];
    	try {
			SObject so = copyContactInfo(data);
    		so.setField("AccountId", ACCOUNT_TYPE_ID);
    		so.setField("RecordTypeId", CONTACT_RECORD_TYPE_ID);
            so.setField("OwnerId", ownerId);
    		records[0] = so;

    		// Create the record in Salesforce.
    		SaveResult[] saveResults = conn.create(records);

    		// Check the returned results for any errors.
    		for (int i = 0; i < saveResults.length; i++) {
    			if (saveResults[i].isSuccess()) {
    				contactId = saveResults[i].getId();
    				log.info("Successfully created record - Id: " + contactId + "\n");
    			}
    			else {
    				Error[] errors = saveResults[i].getErrors();
    				for (int j = 0; j < errors.length; j++) {
    					log.error("Error creating record: " + errors[j].getMessage() + "\n");
    				}
    			}
    		}
    	}
    	catch (Exception e) {
    		e.printStackTrace();
    	}
        return contactId;
    }

    private static void updateContact(PartnerConnection conn, String contactId,
                                      ReferralData data) {
    	log.info("Updating contact Id: " + contactId + "...\n");

    	SObject[] records = new SObject[1];
    	try {
			SObject so = copyContactInfo(data);
			so.setId(contactId);
			records[0] = so;

    		// Update the record in Salesforce.
    		SaveResult[] saveResults = conn.update(records);

    		// Check the returned results for any errors.
    		for (int i = 0; i < saveResults.length; i++) {
    			if (saveResults[i].isSuccess()) {
    				log.info("Successfully updated record - Id: " + saveResults[i].getId() + "\n");
    			}
    			else {
    				Error[] errors = saveResults[i].getErrors();
    				for (int j = 0; j < errors.length; j++) {
    					log.error("Error updating record: " + errors[j].getMessage() + "\n");
    				}
    			}
    		}
    	}
    	catch (Exception e) {
    		e.printStackTrace();
    	}
    }

    private static SObject copyContactInfo(ReferralData data) {
        SObject so = new SObject();
		so.setType("Contact");
		so.setField("FirstName", data.firstName);
		so.setField("LastName", data.lastName);
		so.setField("SSN__c", data.ssn);
		so.setField("Birthdate", data.dob);
		so.setField("Home_Street__c", data.address1);
		so.setField("Home_Apt_Num__c", "");
		so.setField("Home_City__c", data.city);
		so.setField("County__c", "San Diego");
		so.setField("Home_State__c", data.state);
		so.setField("Home_Zip__c", data.zip);

        if (data.homePhone != null) {
            so.setField("Phone_1_Primary__c", data.homePhone);
        }
        else if (data.workPhone != null) {
            so.setField("Phone_1_Primary__c", data.workPhone);
        }
        if (data.mobilePhone != null) {
            so.setField("Phone_1_Primary__c", data.mobilePhone);
        }

        String race = data.race.toLowerCase();
        if (race.contains("african")) {
            so.setField("Race__c", "African");
        }
        else if (race.contains("black")) {
            so.setField("Race__c", "African American/ Black");
        }
        else if (race.contains("native")) {
            so.setField("Race__c", "Alaska Native");
        }
        else if (race.contains("indian")) {
            so.setField("Race__c", "American Indian");
        }
        else if (race.contains("asian")) {
            so.setField("Race__c", "Asian");
        }
        else if (race.contains("hispanic") || race.contains("latino")) {
            so.setField("Race__c", "Hispanic/Latino");
        }
        else if (race.contains("hawaiian")) {
            so.setField("Race__c", "Native Hawaiian");
        }
        else if (race.contains("pacific")) {
            so.setField("Race__c", "Pacific Islander");
        }
        else if (race.contains("white")) {
            so.setField("Race__c", "White/ Caucasian");
        }
        else if (race.contains("other")) {
            so.setField("Race__c", "Other");
        }

        if (data.gender.equalsIgnoreCase("male")) {
            so.setField("Gender_Identity__c", "Man");
        }
        else if (data.gender.equalsIgnoreCase("female")) {
            so.setField("Gender_Identity__c", "Women");
        }
		so.setField("What_Languages_do_you_Speak__c", "English");
		so.setField("Other_Languages__c", "");
        return so;
    }

    private static void createReferral(PartnerConnection conn, String ownerId,
                                       String contactId, ReferralData data) {
    	log.info("Creating Sharp referral program for contact Id: " + contactId + "...\n");

    	SObject[] records = new SObject[1];
    	try {
    		SObject so = new SObject();
    		so.setType("Program__c");
    		so.setField("RecordTypeId", SHARP_RECORD_TYPE_ID);
    		so.setField("OwnerId", ownerId);
    		so.setField("Client__c", contactId);

            String levelOfCare = data.ptClass.toLowerCase();
            if (levelOfCare.contains("inpatient")) {
                so.setField("Level_of_Care__c", "Inpatient");
            }
            else if (levelOfCare.contains("residential")) {
                so.setField("Level_of_Care__c", "Residential");
            }
            else if (levelOfCare.contains("partial")) {
                so.setField("Level_of_Care__c", "Partial Hospital");
            }
            else if (levelOfCare.contains("outpatient")) {
                so.setField("Level_of_Care__c", "Intensive Outpatient/Outpatient");
            }

    		so.setField("Marital_Status__c", data.maritalStatus);

            String emergencyContact = "";
            String emergencyPhone = null;
            Pattern pattern = Pattern.compile("\\(\\d{3}\\).*?\\d{3}.*?\\d{4}");

            // Try to get emergency contact phone from contact 1 first.
            if (data.contact1 != null && data.contact1.length() > 0) {
                Matcher matcher = pattern.matcher(data.contact1);
                if (matcher.find()) {
                    emergencyContact = data.contact1;
                    emergencyPhone = matcher.group(0);
                }
            }

            // Next, try to get emergency phone info from contact 2.
            if (emergencyPhone == null) {
                if (data.contact2 != null && data.contact2.length() > 0) {
                    Matcher matcher = pattern.matcher(data.contact2);
                    if (matcher.find()) {
                        emergencyContact = data.contact2;
                        emergencyPhone = matcher.group(0);
                    }
                }
            }

            // If all fails, display all info in both contact 1 & 2.
            if (emergencyPhone == null) {
                if (data.contact1 != null && data.contact1.length() > 0) {
                    emergencyContact += data.contact1;
                }
                if (data.contact2 != null && data.contact2.length() > 0) {
                    emergencyContact += " - " + data.contact2;
                }
            }
/*
            so.setField("Caller_Caregiver_Name__c", emergencyContact);

            String relationship = emergencyContact.toLowerCase();
            if (relationship.contains("daughter") || relationship.contains("son")) {
                so.setField("Caller_s_relationship_to_PWN__c", "Adult Child");
            }
            else if (relationship.contains("spouse") || relationship.contains("wife") ||
                     relationship.contains("husband")) {
                so.setField("Caller_s_relationship_to_PWN__c", "Spouse");
            }
            else if (relationship.contains("self")) {
                so.setField("Caller_s_relationship_to_PWN__c", "Self");
            }
            else if (relationship.contains("friend")) {
                so.setField("Caller_s_relationship_to_PWN__c", "Friend");
            }
            else if (relationship.contains("neighbor")) {
                so.setField("Caller_s_relationship_to_PWN__c", "Neighbor");
            }
            else if (relationship.contains("relative")) {
                so.setField("Caller_s_relationship_to_PWN__c", "Other Relative");
            }
            else if (relationship.contains("caseworker")) {
                so.setField("Caller_s_relationship_to_PWN__c", "Caseworker");
            }
            else if (relationship.contains("other")) {
                so.setField("Caller_s_relationship_to_PWN__c", "Other");
            }
*/

            if (emergencyPhone != null) {
                so.setField("Caller_Caregiver_Phone__c", emergencyPhone);
            }
            else {
                if (data.homePhone != null) {
                    so.setField("Caller_Caregiver_Phone__c", data.homePhone);
                }
                else if (data.workPhone != null) {
                    so.setField("Caller_Caregiver_Phone__c", data.workPhone);
                }
                if (data.mobilePhone != null) {
                    so.setField("Caller_Caregiver_Phone__c", data.mobilePhone);
                }
            }

    		so.setField("Notes__c", data.comments);
    		so.setField("Patient_Type__c", "Impatient Discharge");
    		so.setField("Primary_Diagnosis__c", data.primaryDiagnosis);
    		so.setField("Additional_Diagnosis_e_g_Psychiatric__c", data.additionalDiagnosis);
    		so.setField("Admission_Date__c", data.admissionDate);
    		so.setField("Projected_Discharge_Date__c", data.dischargeDate);

            String facility = data.facility.toLowerCase();
            if (facility.contains("grossmont")) {
                so.setField("Facility__c", "Grossmont");
            }
            else if (facility.contains("memorial")) {
                so.setField("Facility__c", "Memorial");
            }
            else if (facility.contains("chula vista")) {
                so.setField("Facility__c", "Chula Vista");
            }
            else if (facility.contains("coronado")) {
                so.setField("Facility__c", "Coronado");
            }
            else if (facility.contains("mesa vista")) {
                so.setField("Facility__c", "Mesa Vista");
            }

    		records[0] = so;

    		// create the records in Salesforce.com
    		SaveResult[] saveResults = conn.create(records);

    		// check the returned results for any errors
    		for (int i = 0; i < saveResults.length; i++) {
    			if (saveResults[i].isSuccess()) {
    				System.out.println(i+". Successfully created record - Id: " + saveResults[i].getId() + "\n");
    			}
    			else {
    				Error[] errors = saveResults[i].getErrors();
    				for (int j = 0; j < errors.length; j++) {
    					System.out.println("Error creating record: " + errors[j].getMessage() + "\n");
    				}
    			}
    		}
    	}
    	catch (Exception e) {
    		e.printStackTrace();
    	}
    }
}
