package src.main.java;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

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
        System.err.println("usage: java -jar SharpReferral.jar <referral.pdf>");
        System.err.println("");
        System.exit(-1);
    }

    public static void main(String[] args) {
        if (args.length == 0 || args.length < 1) {
            usage();
        }

        try {
            String fileName = args[0];
            log.info("Reading Sharp referral document (" + fileName + ")...\n");
            File f = new File(fileName);
            if (!f.exists()) {
                log.error("Sharp referral document not found!");
                System.exit(-1);
            }

            // Parse referral info.
            ReferralData data = parseReferralInfo(fileName);

/*
            // Establish connection to Salesforce.
        	ConnectorConfig config = new ConnectorConfig();
        	config.setUsername(USERNAME);
        	config.setPassword(PASSWORD);
        	//config.setTraceMessage(true);

            PartnerConnection connection = Connector.newConnection(config);
            // @debug.
    		log.info("Auth EndPoint: " + config.getAuthEndpoint());
    		log.info("Service EndPoint: " + config.getServiceEndpoint());
    		log.info("Username: " + config.getUsername());
    		log.info("SessionId: " + config.getSessionId());

            // Check to see if contact has been added?
            String contactId = null;
            SObject contact = queryContact(connection, data);
            if (contact == null) {
                // Add new contact.
                contactId = createContact(connection, data);
            }
            else {
                // Update existing contact.
                contactId = contact.getId();
                updateContact(connection, contactId, data);
            }

            // Populate referral info.
            createReferral(connection, contactId, data);
*/
        }
        catch (IOException ioe) {
            log.error(ioe.getMessage());
            ioe.printStackTrace();
        }
    	catch (ConnectionException ce) {
            log.error(ce.getMessage());
            ce.printStackTrace();
    	}
        catch (Exception e) {
            log.error(e.getMessage());
            e.printStackTrace();
        }
    }

    private static ReferralData parseReferralInfo(String fileName) throws Exception {
        ReferralData data = new ReferralData();
        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");
        String[] keys = {"Referral Information",
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
                         "Contact 2:"};

        // Parse pdf document.
        PdfReader reader = new PdfReader(fileName);
        int pages = reader.getNumberOfPages();
        log.info("PDF has " + pages + " pages.\n");
        PdfTextExtractor extractor = new PdfTextExtractor(reader);
        for (int i = 1; i <= pages; i++) {
            String content = extractor.getTextFromPage(i);
            // Replace "weird" characters.  Order important.
            content = content.replaceAll(" ", "");
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
                    switch (k) {
                        case 0: // Referral #, MRN.
                            String[] info = line.split("-");
                            data.number = info[1].substring(11).trim();
                            data.mrn = info[2].substring(5).trim();
                            log.info("Referral #: " + data.number + "\n");
                            log.info("MRN: " + data.mrn + "\n");
                            break;
                        case 1: // Referral type.
                            int index = line.indexOf("Referral Type:");
                            data.referralType = line.substring(index+15).trim();
                            log.info("Referral Type: " + data.referralType);
                            break;
                        case 2: // Reason.
                            // Reason starts from next line to referral comments.
                            nextLine = lines[++j];
                            while (!nextLine.contains("Referral Comments")) {
                                if (nextLine.trim().length() > 0) {
                                    sb.append(nextLine + "\n");
                                }
                                nextLine = lines[++j];
                            }
                            data.reason = sb.toString();
                            log.info("Reason: " + data.reason);
                            j--; // Move back 1 line.
                            break;
                        case 3: // Comments.
                            // Comments starts from next line to sender's last activity.
                            nextLine = lines[++j];
                            while (!nextLine.contains("Sender's last")) {
                                if (nextLine.trim().length() > 0) {
                                    sb.append(nextLine + "\n");
                                }
                                nextLine = lines[++j];
                            }
                            data.comments = sb.toString();
                            log.info("Referral Comments: " + data.comments);
                            j--; // Move back 1 line.
                            break;
                        case 4: // Name.
                            int nameIndex = line.indexOf("Name:");
                            int mrnIndex = line.indexOf("MRN:");
                            String ptName = line.substring(nameIndex+6, mrnIndex);
                            String[] parts = ptName.trim().split(" ");
                            data.lastName = parts[0];
                            data.firstName = parts[1];
                            log.info("Patient Name: " + data.firstName + " " + data.lastName);
                            break;
                        case 5: // DoB, gender.
                            int dobIndex = line.indexOf("Date of Birth:");
                            int genderIndex = line.indexOf("Gender:");
                            String sDob = line.substring(dobIndex+15, dobIndex+25);
                            data.dob = sdf.parse(sDob);
                            data.gender = line.substring(genderIndex+8).trim();
                            log.info("Date of Birth: " + data.dob.toString());
                            log.info("Gender: " + data.gender);
                            break;
                        case 6: // Address.
                            int addrIndex = line.indexOf("Address:");
                            data.address1 = line.substring(addrIndex+9).trim();
                            j++;
                            String[] part1s = lines[j].trim().split(",");
                            data.city = part1s[0].trim();
                            String[] part2s = part1s[1].trim().split(" ");
                            data.state = part2s[0].trim();
                            data.zip = part2s[1].trim();
                            log.info("Address: " + data.address1 + ", " + data.city +
                                     ", " + data.state + " " + data.zip);
                            break;
                        case 7: // Home, work, mobile phones.
                            int phone = 1;
                            nextLine = lines[++j];
                            while (!nextLine.contains("Marital Status")) {
                                if (phone == 1) {
                                    data.homePhone = nextLine.trim();
                                }
                                else if (phone == 2) {
                                    data.workPhone = nextLine.trim();
                                }
                                else {
                                    data.mobilePhone = nextLine.trim();
                                }
                                nextLine = lines[++j];
                                phone++;
                            }
                            log.info("Home Phone: " + data.homePhone);
                            log.info("Work Phone: " + data.workPhone);
                            log.info("Mobile Phone: " + data.mobilePhone);
                            j--; // Move back 1 line.
                            break;
                        case 8: // Marital status, SSN.
                            int msIndex = line.indexOf("Marital Status:");
                            int ssnIndex = line.indexOf("SSN:");
                            data.maritalStatus  = line.substring(msIndex+16, ssnIndex).trim();
                            data.ssn = line.substring(ssnIndex+5).trim();
                            log.info("Marital Status: " + data.maritalStatus);
                            log.info("SSN: " + data.ssn);
                            break;
                        case 9: // Race.
                            int raceIndex = line.indexOf("Race:");
                            int race2Index = line.indexOf("Race 2:");
                            data.race = line.substring(raceIndex+6, race2Index);
                            log.info("Race: " + data.race.trim());
                            break;
                        case 10: // Religion.
                            int relIndex = line.indexOf("Religion:");
                            data.religion = line.substring(relIndex+10);
                            log.info("Religion: " + data.religion.trim());
                            break;
                        case 11: // Emergency contact 1.
                            // Contact 1 starts from next line to contact 2.
                            nextLine = lines[++j];
                            while (!nextLine.contains("Emergency")) {
                                if (nextLine.trim().length() > 0) {
                                    sb.append(nextLine + "\n");
                                }
                                nextLine = lines[++j];
                            }
                            data.contact1 = sb.toString();
                            log.info("Emergency Contact 1: " + data.contact1);
                            j--; // Move back 1 line.
                            break;
                        case 12: // Emergency contact 2.
                            // Contact 2 starts from next line to contact 2.
                            nextLine = lines[++j];
                            while (nextLine != null && nextLine.trim().length() > 0) {
                                if (nextLine.trim().length() > 0) {
                                    sb.append(nextLine + "\n");
                                }
                                nextLine = lines[++j];
                            }
                            data.contact2 = sb.toString();
                            log.info("Emergency Contact 2: " + data.contact2);
                            j--; // Move back 1 line.
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

    private static SObject queryContact(PartnerConnection conn, ReferralData data) {
        SObject result = null;
    	log.info("Querying contact " + data.firstName + " " + data.lastName + "...");

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

    private static String createContact(PartnerConnection conn, ReferralData data) {
        String contactId = null;
    	log.info("Creating new contact " + data.firstName + " " + data.lastName + "...");

    	SObject[] records = new SObject[1];
    	try {
			SObject so = copyContactInfo(data);
    		so.setField("AccountId", ACCOUNT_TYPE_ID);
    		so.setField("RecordTypeId", CONTACT_RECORD_TYPE_ID);
    		records[0] = so;

    		// Create the record in Salesforce.
    		SaveResult[] saveResults = conn.create(records);

    		// Check the returned results for any errors.
    		for (int i = 0; i < saveResults.length; i++) {
    			if (saveResults[i].isSuccess()) {
    				contactId = saveResults[i].getId();
    				log.info("Successfully created record - Id: " + contactId);
    			}
    			else {
    				Error[] errors = saveResults[i].getErrors();
    				for (int j = 0; j < errors.length; j++) {
    					log.error("Error creating record: " + errors[j].getMessage());
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
    	log.info("Updating contact Id: " + contactId + "...");

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
    				log.info("Successfully updated record - Id: " + saveResults[i].getId());
    			}
    			else {
    				Error[] errors = saveResults[i].getErrors();
    				for (int j = 0; j < errors.length; j++) {
    					log.error("Error updating record: " + errors[j].getMessage());
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

    private static void createReferral(PartnerConnection conn, String contactId,
                                       ReferralData data) {
    	log.info("Creating Sharp referral program for contact Id: " + contactId + "...");

    	SObject[] records = new SObject[1];
    	try {
    		SObject so = new SObject();
    		so.setType("Program__c");
    		so.setField("RecordTypeId", SHARP_RECORD_TYPE_ID);
    		so.setField("Client__c", contactId);
    		so.setField("Marital_Status__c", data.maritalStatus);

            String levelOfCare = data.ptType.toLowerCase();
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

            boolean hasCareGiver = false;
            if (data.contact1 != null && data.contact1.length() > 0) {
                hasCareGiver = true;
                so.setField("Caller_Caregiver_Name__c", data.contact1);
            }
            else if (data.contact2 != null && data.contact2.length() > 0) {
                hasCareGiver = true;
                so.setField("Caller_Caregiver_Name__c", data.contact2);
            }

            if (hasCareGiver) {
                String relationship = "";
                if (data.contact1.length() > 0) {
                    relationship = data.contact1.toLowerCase();
                }
                else {
                    relationship = data.contact2.toLowerCase();
                }
                if (relationship.contains("daughter") || relationship.contains("son")) {
                    so.setField("Caller_s_relationship_to_PWN__c", "Adult Child");
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
                else if (relationship.contains("other")) {
                    so.setField("Caller_s_relationship_to_PWN__c", "Other");
                }
            }

            if (data.homePhone != null) {
                so.setField("Caller_Caregiver_Phone__c", data.homePhone);
            }
            else if (data.workPhone != null) {
                so.setField("Caller_Caregiver_Phone__c", data.workPhone);
            }
            if (data.mobilePhone != null) {
                so.setField("Caller_Caregiver_Phone__c", data.mobilePhone);
            }

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

    		so.setField("Notes__c", data.comments);
    		records[0] = so;

    		// create the records in Salesforce.com
    		SaveResult[] saveResults = conn.create(records);

    		// check the returned results for any errors
    		for (int i = 0; i < saveResults.length; i++) {
    			if (saveResults[i].isSuccess()) {
    				System.out.println(i+". Successfully created record - Id: " + saveResults[i].getId());
    			}
    			else {
    				Error[] errors = saveResults[i].getErrors();
    				for (int j = 0; j < errors.length; j++) {
    					System.out.println("Error creating record: " + errors[j].getMessage());
    				}
    			}
    		}
    	}
    	catch (Exception e) {
    		e.printStackTrace();
    	}
    }
}
