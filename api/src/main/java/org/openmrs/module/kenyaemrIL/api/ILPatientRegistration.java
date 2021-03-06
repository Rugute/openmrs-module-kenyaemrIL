package org.openmrs.module.kenyaemrIL.api;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.*;
import org.openmrs.api.ConceptService;
import org.openmrs.api.context.Context;
import org.openmrs.module.kenyaemrIL.il.*;
import org.openmrs.module.kenyaemrIL.util.ILUtils;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by codehub on 10/30/15.
 * A fragment controller for an IL Registration
 */
public class ILPatientRegistration {

    private final Log log = LogFactory.getLog(this.getClass());
    public static ConceptService conceptService = Context.getConceptService();


    public static ILMessage iLPatientWrapper(Patient patient) {
        ILMessage ilMessage = new ILMessage();
        List<INTERNAL_PATIENT_ID> internalPatientIds = new ArrayList<INTERNAL_PATIENT_ID>();

        PATIENT_IDENTIFICATION patientIdentification = new PATIENT_IDENTIFICATION();
        //Set date of birth
        String iLDob = null;
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd");
        iLDob = formatter.format(patient.getBirthdate());
        patientIdentification.setDate_of_birth(iLDob);
        //set dob precision
        patientIdentification.setDate_of_birth_precision(patient.getBirthdateEstimated() == true ? "ESTIMATED" : "EXACT");
        //set death date and indicator
        if (patient.isDead()) {
            patientIdentification.setDeath_date(String.valueOf(patient.getDeathDate()));
            patientIdentification.setDeath_indicator(String.valueOf(patient.isDead()));
        } else {
            patientIdentification.setDeath_date("");
            patientIdentification.setDeath_indicator("");
        }

        //set patient address
//        TODO - confirm address mappings
        PersonAddress personAddress = patient.getPersonAddress();
        if(personAddress != null) {

            PATIENT_ADDRESS pAddress = new PATIENT_ADDRESS();
            PHYSICAL_ADDRESS physicalAddress = new PHYSICAL_ADDRESS();

            physicalAddress.setWard(personAddress.getAddress6() != null ? personAddress.getAddress6() : "");
            physicalAddress.setCounty(personAddress.getCountyDistrict() != null ? personAddress.getCountyDistrict() : "");
            physicalAddress.setNearest_landmark(personAddress.getAddress2() != null ? personAddress.getAddress2() : "");
            physicalAddress.setSub_county(personAddress.getAddress4() != null ? personAddress.getAddress4() : "");
            physicalAddress.setVillage(personAddress.getCityVillage() != null ? personAddress.getCityVillage() : "");
            physicalAddress.setGps_location("");
            pAddress.setPhysical_address(physicalAddress);

            pAddress.setPostal_address("");
            patientIdentification.setPatient_address(pAddress);
        }
//set external identifier if available
        EXTERNAL_PATIENT_ID epd = new EXTERNAL_PATIENT_ID();
        INTERNAL_PATIENT_ID ipd;
//        Form the internal patient IDs
        for (PatientIdentifier patientIdentifier : patient.getIdentifiers()) {
            ipd = new INTERNAL_PATIENT_ID();
            if (patientIdentifier.getIdentifierType().getName().equalsIgnoreCase("Unique Patient Number")) {
                ipd.setAssigning_authority("CCC");
                ipd.setId(patientIdentifier.getIdentifier());
                ipd.setIdentifier_type("CCC_NUMBER");
                internalPatientIds.add(ipd);
            } else if (patientIdentifier.getIdentifierType().getName().equalsIgnoreCase("MPI GODS NUMBER")) {
                if (patientIdentifier.getIdentifierType().getName() != null) {
                    epd.setAssigning_authority("MPI");
                    epd.setId(patientIdentifier.getIdentifier());
                    epd.setIdentifier_type("GODS_NUMBER");
                    patientIdentification.setExternal_patient_id(epd);
                }
                continue;
            }
        }


        patientIdentification.setInternal_patient_id(internalPatientIds);
        patientIdentification.setExternal_patient_id(epd);

        //Set the patient name
        PATIENT_NAME patientname = new PATIENT_NAME();
        PersonName personName = patient.getPersonName();
        patientname.setFirst_name(personName.getGivenName() != null ? personName.getGivenName() : "");
        patientname.setMiddle_name(personName.getMiddleName() != null ? personName.getMiddleName() : "");
        patientname.setLast_name(personName.getFamilyName() != null ? personName.getFamilyName() : "");
        patientIdentification.setPatient_name(patientname);
        //Set the patient mothers name
        MOTHER_NAME motherName = new MOTHER_NAME();
        if (patient.getAttribute("Mother's Name") != null) {
            motherName.setFirst_name(patient.getAttribute("Mother Name") != null ? patient.getAttribute("Mother Name").getValue() : "");
            patientIdentification.setMother_name(motherName);
        }
        //Set mothers name
      //  patientIdentification.setMother_name(new MOTHER_NAME());

        patientIdentification.setSex(patient.getGender());   //        Set the Gender, phone number and marital status
        patientIdentification.setPhone_number(patient.getAttribute("Telephone contact") != null ? patient.getAttribute("Telephone contact").getValue() : "");
        patientIdentification.setMarital_status(patient.getAttribute("Civil Status") != null ? patient.getAttribute("Civil Status").getValue() : "");
        //add patientIdentification to IL message
        ilMessage.setPatient_identification(patientIdentification);

//set patient visit
        PATIENT_VISIT patientVisit = new PATIENT_VISIT();
        // get enrollment Date
        Encounter lastEnrollment = ILUtils.lastEncounter(patient, Context.getEncounterService().getEncounterTypeByUuid("de78a6be-bfc5-4634-adc3-5f1a280455cc"));
        Integer patientEnrollmentTypeConcept = 164932;
        Integer patientEnrollmentSourceConcept = 160540;
        if(lastEnrollment != null) {
            Date lastEnrollmentDate = lastEnrollment.getEncounterDatetime();
            patientVisit.setVisit_date(formatter.format(lastEnrollmentDate));      //hiv_care_enrollment date
            patientVisit.setHiv_care_enrollment_date(formatter.format(lastEnrollmentDate));        //hiv_care_enrollment date
            for (Obs obs : lastEnrollment.getObs()) {
                //set patient type
                if (obs.getConcept().getConceptId().equals(patientEnrollmentTypeConcept)) {    //get patient type
                    patientVisit.setPatient_type(patientTypeConverter(obs.getValueCoded()));
                }
                if (obs.getConcept().getConceptId().equals(patientEnrollmentSourceConcept)) {    //get patient source
                    patientVisit.setPatient_source(patientSourceConverter(obs.getValueCoded()));
                }
            }
        }else{
            patientVisit.setVisit_date("");      //hiv_care_enrollment date
            patientVisit.setHiv_care_enrollment_date("");        //hiv_care_enrollment date
            patientVisit.setPatient_type("");
            patientVisit.setPatient_source("");
        }

      //add patientVisit to IL message
        ilMessage.setPatient_visit(patientVisit);

  //    Next of KIN
        NEXT_OF_KIN patientKins[] = new NEXT_OF_KIN[1];
        NEXT_OF_KIN nok = new NEXT_OF_KIN();
        if (patient.getAttribute("Next of kin name") != null) {
            NOK_NAME fnok = new NOK_NAME();
            String nextOfKinName = patient.getAttribute("Next of kin name").getValue();
            String[] split = nextOfKinName.split(" ");
            switch (split.length){
                case 1:{
                    fnok.setFirst_name(split[0]);
                    break;
                }
                case 2:{
                    fnok.setFirst_name(split[0]);
                    fnok.setMiddle_name(split[1]);
                    break;
                }
                case 3:{
                    fnok.setFirst_name(split[0]);
                    fnok.setMiddle_name(split[1]);
                    fnok.setLast_name(split[2]);
                    break;
                }
            }

            nok.setNok_name(fnok);
        }else{

        }
        nok.setPhone_number(patient.getAttribute("Next of kin contact") != null ? patient.getAttribute("Next of kin contact").getValue() : "");
        nok.setRelationship(patient.getAttribute("Next of kin relationship") != null ? patient.getAttribute("Next of kin relationship").getValue() : "");
        nok.setAddress(patient.getAttribute("Next of kin address") != null ? patient.getAttribute("Next of kin address").getValue() : "");
        nok.setSex("");
        nok.setDate_of_birth("");
        nok.setContact_role("");
        patientKins[0] = nok;
        ilMessage.setNext_of_kin(patientKins);

        return ilMessage;
    }

    static String patientTypeConverter(Concept key) {
        Map<Concept, String> patientTypeList = new HashMap<Concept, String>();
        patientTypeList.put(conceptService.getConcept(164144), "New");
        patientTypeList.put(conceptService.getConcept(160563), "Transfer In");
        patientTypeList.put(conceptService.getConcept(164931), "Transit");
        return patientTypeList.get(key);
    }

    static String patientSourceConverter(Concept key) {
        Map<Concept, String> patientSourceList = new HashMap<Concept, String>();
        patientSourceList.put(conceptService.getConcept(159938), "hbtc");
        patientSourceList.put(conceptService.getConcept(160539), "vct_site");
        patientSourceList.put(conceptService.getConcept(159937), "mch");
        patientSourceList.put(conceptService.getConcept(160536), "ipd_adult");
        patientSourceList.put(conceptService.getConcept(160537), "ipd_child");
        patientSourceList.put(conceptService.getConcept(160541), "tb");
        patientSourceList.put(conceptService.getConcept(160542), "opd");
        patientSourceList.put(conceptService.getConcept(162050), "ccc");
        patientSourceList.put(conceptService.getConcept(160551), "self");
        patientSourceList.put(conceptService.getConcept(5622), "other");
        return patientSourceList.get(key);
    }

}
