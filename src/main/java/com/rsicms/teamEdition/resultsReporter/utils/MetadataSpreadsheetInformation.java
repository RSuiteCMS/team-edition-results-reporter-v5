package com.rsicms.teamEdition.resultsReporter.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.reallysi.rsuite.api.ManagedObject;
import com.rsicms.rsuite.helpers.messages.impl.ProcessMessageContainerImpl;
import com.rsicms.teamEdition.resultsReporter.datamodel.ContentObjectMetadata;

/**
 * Holds the details of art log load attempts for 
 * subsequent reporting.
 *
 */
public class MetadataSpreadsheetInformation extends ProcessMessageContainerImpl {
	
    Set<String> dedupedFieldList = new HashSet<String>();
    List<ContentObjectMetadata> comList = new ArrayList<ContentObjectMetadata>(); 
    List<ManagedObject> newMos = new ArrayList<ManagedObject>();
    List<ManagedObject> updatedMos = new ArrayList<ManagedObject>();
    List<ManagedObject> lockedMos = new ArrayList<ManagedObject>();
    List<ManagedObject> lockBeforeUpdateMos = new ArrayList<ManagedObject>();
    List<ManagedObject> skippedMosMdErrors = new ArrayList<ManagedObject>();
    List<ManagedObject> skippedMosLocked = new ArrayList<ManagedObject>();
    List<ManagedObject> skippedMosOther = new ArrayList<ManagedObject>();
    List<String> badIds = new ArrayList<String>();
    Map<ManagedObject, List<String>> undefinedFields = new HashMap<ManagedObject, List<String>>();
    Map<ManagedObject, List<String>> unavailableFields = new HashMap<ManagedObject, List<String>>();
    Map<ManagedObject, List<String>> tooManyValuesFields = new HashMap<ManagedObject, List<String>>();
    Map<ManagedObject, List<String>> versionedButLockedFields = new HashMap<ManagedObject, List<String>>();
    Map<ManagedObject, List<String>> badValues = new HashMap<ManagedObject, List<String>>();
    Map<ManagedObject, List<String>> unknownProblems = new HashMap<ManagedObject, List<String>>();


    public void populateDedupedFieldList(List<String> fieldList) {
        Set<String> potentialFields = new HashSet<String>(fieldList);
        for (String potentialField : potentialFields) {
            if (!isNonMdField(potentialField)) {
                this.dedupedFieldList.add(potentialField);  
            }
        }
    }
    
    public void addCOMList(List<ContentObjectMetadata> coms) {
	    this.comList.addAll(coms);
	}
    
    public void addUpdatedMo(ManagedObject mo) {
        this.updatedMos.add(mo);
	}


    public void addLockedMo(ManagedObject mo) {
        this.lockedMos.add(mo);
    }

    public void addLockBeforeUpdateMo(ManagedObject mo) {
        this.lockBeforeUpdateMos.add(mo);
    }

    public void addSkippedMosMdErrors(ManagedObject mo) {
        this.skippedMosMdErrors.add(mo);
    }

    public void addSkippedMosLocked(ManagedObject mo) {
        this.skippedMosLocked.add(mo);
    }

    public void addSkippedMosOther(ManagedObject mo) {
        this.skippedMosOther.add(mo);
    }

    public void addUndefinedField(ManagedObject mo, String fieldName) {
        addToStack(mo, this.undefinedFields, fieldName);
    }

    public void addUnavailableField(ManagedObject mo, String fieldName) {
        addToStack(mo, this.unavailableFields, fieldName);
    }

    public void addTooManyValuesField(ManagedObject mo, String fieldName) {
        addToStack(mo, this.tooManyValuesFields, fieldName);
    }

    public void addVersionedButLockedField(ManagedObject mo, String fieldName) {
        addToStack(mo, this.versionedButLockedFields, fieldName);
    }

    public void addUnknownProblem(ManagedObject mo, String problem) {
        addToStack(mo, this.unknownProblems, problem);
    }

    public void addBadIds(String id) {
        this.badIds.add(id);
    }

    public Set<String> getDedupedFieldList() {
        return this.dedupedFieldList;
    }
    
    public List<ContentObjectMetadata> getCOMList() {
        List<ContentObjectMetadata> result = new ArrayList<ContentObjectMetadata>(this.comList);
        return result;
    }
    
	public List<ManagedObject> getUpdatedMos() {
		List<ManagedObject> result = new ArrayList<ManagedObject>(this.updatedMos);
		return result;
	}

    public List<ManagedObject> getLockedMos() {
        List<ManagedObject> result = new ArrayList<ManagedObject>(this.lockedMos);
        return result;
    }

    public List<ManagedObject> getLockBeforeUpdateMos() {
        List<ManagedObject> result = new ArrayList<ManagedObject>(this.lockBeforeUpdateMos);
        return result;
    }

    public List<ManagedObject> getSkippedMosMdErrors() {
        List<ManagedObject> result = new ArrayList<ManagedObject>(this.skippedMosMdErrors);
        return result;
    }

    public List<ManagedObject> getSkippedMosLocked() {
        List<ManagedObject> result = new ArrayList<ManagedObject>(this.skippedMosLocked);
        return result;
    }

    public List<ManagedObject> getSkippedMosOther() {
        List<ManagedObject> result = new ArrayList<ManagedObject>(this.skippedMosOther);
        return result;
    }

    public Map<ManagedObject, List<String>> getUndefinedFields() {
        return this.undefinedFields;
    }

    public Map<ManagedObject, List<String>> getUnavailableFields() {
        return this.unavailableFields;
    }

    public Map<ManagedObject, List<String>> getTooManyValuesFields() {
        return this.tooManyValuesFields;
    }

    public Map<ManagedObject, List<String>> getVersionedButLockedFields() {
        return this.versionedButLockedFields;
    }

    public Map<ManagedObject, List<String>> getUnknownProblems() {
        return this.unknownProblems;
    }

    public List<String> getbadIds() {
        return this.badIds;
    }

    private void addToStack(ManagedObject mo, Map<ManagedObject, List<String>> stack, String value) {
        if (stack.containsKey(mo)) {
            List<String> fieldValuePairs = stack.get(mo);
            fieldValuePairs.add(value);
            stack.put(mo, fieldValuePairs);
        } else {
            List<String> fieldValuePairs = new ArrayList<String>();
            fieldValuePairs.add(value);
            stack.put(mo, fieldValuePairs);
        } 
    }


    private static Boolean isNonMdField(String fieldName) {
        // TODO create a data type for non-LMD fields and use that for all
        // contexts when need to ignore/handle
        List<String> nonMdFields = new ArrayList<String>();
        nonMdFields.add("id");
        nonMdFields.add("rsuite name");
        nonMdFields.add("name");
        nonMdFields.add("rsuite id");
        nonMdFields.add("last modified");
        nonMdFields.add("last modified by");
        nonMdFields.add("date created");
        nonMdFields.add("check out date");
        nonMdFields.add("check out status");
        nonMdFields.add("check out user");
        nonMdFields.add("object type");
        nonMdFields.add("content type");
        nonMdFields.add("alias");
        nonMdFields.add("current version number");
        nonMdFields.add("metadata summary");
        nonMdFields.add("thumbnail");
        Boolean isNonMd = false;
        if (nonMdFields.contains(fieldName.toLowerCase())) {
            isNonMd = true;
        }
        return isNonMd;
    }
}
