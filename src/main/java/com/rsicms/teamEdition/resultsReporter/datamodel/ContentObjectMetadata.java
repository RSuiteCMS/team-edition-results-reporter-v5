package com.rsicms.teamEdition.resultsReporter.datamodel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.reallysi.rsuite.api.ManagedObject;
import com.reallysi.rsuite.api.MetaDataItem;
import com.reallysi.rsuite.api.RSuiteException;
import com.reallysi.rsuite.api.User;
import com.reallysi.rsuite.api.extensions.ExecutionContext;

/**
 * An mo or ca and its metadata.
 * 
 */
public class ContentObjectMetadata {

    private ManagedObject mo = null;
    private String matchedId = "";
    private Map<String, List<String>> metadataProperties = new HashMap<String, List<String>>();
    private Map<String, List<String>> newMetadataProperties = new HashMap<String, List<String>>();
    private Map<String, List<String>> deletedMetadataProperties = new HashMap<String, List<String>>();
    private Map<String, List<String>> unchangedMetadataProperties = new HashMap<String, List<String>>();

    public ContentObjectMetadata(ExecutionContext context, User user, String identifier) throws RSuiteException {
        ManagedObject objectMo = null;
        try {
            objectMo = context.getManagedObjectService().getManagedObject(user, identifier);
        } catch (Exception e) {
            objectMo = context.getManagedObjectService().getObjectByAlias(user, identifier);
            if (objectMo == null) {
                throw new RSuiteException("This identifier is not a known RSuite moid or alias.");
            }
        }
        this.mo = objectMo;
        this.matchedId = identifier;
    }

    /**
     * Add a property value to the complete list of values being read from the spreadsheet or MO
     * 
     * @param fieldName
     * @param value
     */
    public void addMetadataProperty(String fieldName, String value) {
        addProperty(fieldName, value, metadataProperties);
    }


    /**
     * Add a property value to the list of values to add/being added to the MO
     * 
     * @param fieldName
     * @param value
     */
    public void addNewMetadataProperty(String fieldName, String value) {
        addProperty(fieldName, value, newMetadataProperties);
    }

    /**
     * Add a property value to the list of values to delete from/being deleted from the MO
     * 
     * @param fieldName
     * @param value
     */
    public void addDeletedMetadataProperty(String fieldName, String value) {
        addProperty(fieldName, value, deletedMetadataProperties);
    }

    /**
     * Add a property value to the list of values that are unchanged for the MO
     * 
     * @param fieldName
     * @param value
     */
    public void addUnchangedMetadataProperty(String fieldName, String value) {
        addProperty(fieldName, value, unchangedMetadataProperties);
    }

    public ManagedObject getMo() {
        return this.mo;
    }

    public String getMoPrimaryIdentifier() throws RSuiteException {
        String primaryAlias = "";
//        try {
//            Alias[] aliases = this.mo.getAliases();
//            if (aliases != null) {
//                for (Alias alias : aliases) {
//                    if (alias.getType() != null && alias.getType().equals("filename")) {
//                        primaryAlias = alias.getText();
//                        break;
//                    }
//                }
//            }
            if (primaryAlias.isEmpty()) {
                primaryAlias = this.mo.getId();
            }
//        } catch (RSuiteException e) {
//            log.error("Unexpected error: " + e.getMessage());
//            throw new RSuiteException("ContentObjectMetadata.getMoPrimaryIdentifier(): Error getting primary identifier for object.");
//        }
        return primaryAlias;
    }

    public List<String> getSortedFieldNames() {
        List<String> keyList = new ArrayList<String>();
        for (String key : this.metadataProperties.keySet()) {
            keyList.add(key);
        }
        Collections.sort(keyList);
        return keyList;
    }

    public Map<String, List<String>> getMetadataProperties() {
        return this.metadataProperties;
    }

    public Map<String, List<String>> getNewMetadataProperties() {
        return this.newMetadataProperties;
    }

    public Map<String, List<String>> getDeletedMetadataProperties() {
        return this.deletedMetadataProperties;
    }

    public Map<String, List<String>> getUnchangedMetadataProperties() {
        return this.unchangedMetadataProperties;
    }

    public List<String> getExistingMoValuesForProperty(ExecutionContext context, String property) throws RSuiteException {
        List<String> valueList = new ArrayList<String>();
        List<MetaDataItem> mdItems = mo.getMetaDataItems();
            for (MetaDataItem mdi : mdItems) {
                if (mdi.getName().equals(property)) {
                    valueList.add(mdi.getValue());
                }
            }
        return valueList;
    }

    public Map<String, Integer> getMetadataPropertyCounts() {
        Map<String, Integer> metadataPropertyCounts = new HashMap<String, Integer>();
        for (String key : this.metadataProperties.keySet()) {
            metadataPropertyCounts.put(key, this.metadataProperties.get(key).size());
        }
        return metadataPropertyCounts;
    }

    public static List<ContentObjectMetadata> getSortedListOfComs(List<ContentObjectMetadata> comList) {
        Collections.sort(comList, new Comparator<ContentObjectMetadata>() {

            private String getSortString(ContentObjectMetadata com) {
                try {
                	if (com.getMo().getDisplayName() == null) {
                		return "";
                	}
                    return com.getMo().getDisplayName().toLowerCase();
                } catch (RSuiteException e) {
                    return com.getMo().getId();
                }
            }
            @Override
            public int compare(ContentObjectMetadata com0, ContentObjectMetadata com1) {
                return getSortString(com0).compareTo(getSortString(com1));
            }
        }); 
        return comList;
    }

    public String getMatchedId() {
        return this.matchedId;
    }

    private void addProperty(String fieldName, String value, Map<String, List<String>> mdProps) {
        if (mdProps.containsKey(fieldName)) {
            List<String> values = mdProps.get(fieldName);
            values.add(value);
            mdProps.put(fieldName, values);
        } else {
            List<String> values = new ArrayList<String>();
            values.add(value);
            mdProps.put(fieldName, values);
        }
    }

}