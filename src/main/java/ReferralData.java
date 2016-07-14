package src.main.java;

import java.util.Date;

public class ReferralData {
    public String facility;
    public Date admissionDate;
    public Date dischargeDate;
    public String ptClass;
    public String primaryDiagnosis;
    public String additionalDiagnosis;
    public String admittingPhysician;
    public String attendingPhysician;
    public String number;
    public String mrn;
    public String referralType;
    public String comments;
    public String reason;
    public String firstName;
    public String lastName;
    public Date dob;
    public String ssn;
    public String gender;
    public String address1;
    public String address2;
    public String city;
    public String state;
    public String zip;
    public String homePhone;
    public String workPhone;
    public String mobilePhone;
    public String maritalStatus;
    public String race;
    public String religion;
    public String contact1;
    public String contact2;
}

/*
<apex:page standardController="Contact" extensions="ClientInfoExt">
    <apex:pageBlock title="Client Information">
        <apex:pageBlockTable value="{!Records}" var="Record">
            <apex:column>
                <apex:facet name="header">Name</apex:facet>
                <apex:outputText value="{!Record.Name}"/>
            </apex:column>
            <apex:column>
                <apex:facet name="header">Primary Phone</apex:facet>
                <apex:outputText value="{!Record.Phone_1_Primary__c}" />
            </apex:column>
            <apex:column>
                <apex:facet name="header">Phone 2</apex:facet>
                <apex:outputText value="{!Record.Phone_2__c}" />
            </apex:column>
            <apex:column>
                <apex:facet name="header">Mailing Address</apex:facet>
                <apex:outputText value="{!Record.Mailing_Street__c}" />
            </apex:column>
            <apex:column>
                <apex:facet name="header">Mailing City</apex:facet>
                <apex:outputText value="{!Record.Mailing_City__c}" />
            </apex:column>
            <apex:column>
                <apex:facet name="header">Mailing State</apex:facet>
                <apex:outputText value="{!Record.Mailing_State__c}" />
            </apex:column>
            <apex:column>
                <apex:facet name="header">Mailing Zip</apex:facet>
                <apex:outputText value="{!Record.Mailing_Zip__c}" />
            </apex:column>
        </apex:pageBlockTable>
    </apex:pageBlock>
</apex:page>

public class ClientInfoExt {
    public Contact currentContact {get; set;}
	public List<Contact> Records {
        get {
            try {
                Records = new List<Contact>();
                Records = [SELECT Id, Name, Phone_1_Primary__c, Phone_2__c, Mailing_Street__c, Mailing_City__c, Mailing_State__c, Mailing_Zip__c FROM Contact WHERE Id = :currentContact.Id];
            }
            catch (Exception ex) {
                Records = null;
            }
            return Records;
        }
        private set;
	}

    public ClientInfoExt(ApexPages.StandardController sc) {
        sc.AddFields(new List<String>{'Id'});
        currentContact = (Contact)sc.getRecord();
        System.debug('***** ' + currentContact);
	}
}
*/
