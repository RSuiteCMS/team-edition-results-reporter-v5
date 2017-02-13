package com.rsicms.teamEdition.resultsReporter.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.reallysi.rsuite.api.ElementMatchingOptions;
import com.reallysi.rsuite.api.LayeredMetadataDefinition;
import com.reallysi.rsuite.api.ManagedObject;
import com.reallysi.rsuite.api.MetaDataItem;
import com.reallysi.rsuite.api.RSuiteException;
import com.reallysi.rsuite.api.User;
import com.reallysi.rsuite.api.VersionType;
import com.reallysi.rsuite.api.control.ObjectCheckInOptions;
import com.reallysi.rsuite.api.extensions.ExecutionContext;
import com.reallysi.rsuite.service.ManagedObjectService;
import com.rsicms.pluginUtilities.POI.POIHelper;
import com.rsicms.rsuite.helpers.messages.ProcessFailureMessage;
import com.rsicms.rsuite.helpers.messages.impl.GenericProcessFailureMessage;
import com.rsicms.teamEdition.resultsReporter.datamodel.ContentObjectMetadata;

/**
 * Manages import of LMD from a spreadsheet.
 * 
 */
public class MetadataImporter {

    private static Log log = LogFactory.getLog(MetadataImporter.class);

    public static MetadataSpreadsheetInformation readSpreadsheet(ExecutionContext context, User user, FileItem metadataFile) throws RSuiteException {
        MetadataSpreadsheetInformation msi = new MetadataSpreadsheetInformation();

        if (metadataFile == null) {
            msi.addFailureMessage("No File", "Metadata Import", "No metadata file selected.");
            return msi;
        }

        try {
            return readMetadata(context, user, metadataFile.getInputStream(), metadataFile.getName());
        } catch (IOException e) {
            msi.addFailureMessage("Exception", "Metadata Import",
                    "I/O exception trying to read metadata file " + metadataFile.getName() + ": " + e.getMessage());
            return msi;
        }
    }

    public static Workbook getWorksheet(InputStream excelStream, String extension) throws RSuiteException {
        Workbook wb = null;
        try {
            if ("xls".equals(extension)) {
                POIFSFileSystem poiFilesystem = new POIFSFileSystem(excelStream);
                wb = new HSSFWorkbook(poiFilesystem);
            }
            if ("xlsx".equals(extension)) {
                wb = new XSSFWorkbook(excelStream);
            } else {
                throw new RSuiteException("Unrecognized file extension \"" + extension + "\". Expected .xls or .xlsx");
            }
        } catch (IOException e) {
            throw new RSuiteException("IOException parsing Excel data: " + e.getMessage());
        } catch (Exception e) {
            throw new RSuiteException("Unexpected Exception parsing Excel data: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
        return wb;
    }

    public static void importMetadata(ExecutionContext context, User user, MetadataSpreadsheetInformation msi) {
        ManagedObjectService moSvc = context.getManagedObjectService();

        for (ContentObjectMetadata item : msi.comList) {
            ManagedObject mo = item.getMo();

            if (msi.getLockedMos() != null && msi.getLockedMos().contains(mo)) {
                msi.addSkippedMosLocked(mo);
                continue;
            } else if ((msi.getTooManyValuesFields() != null && msi.getTooManyValuesFields().containsKey(mo))
                    || (msi.getUnavailableFields() != null && msi.getUnavailableFields().containsKey(mo))
                    || (msi.getUndefinedFields() != null && msi.getUndefinedFields().containsKey(mo))) {
                msi.addSkippedMosMdErrors(mo);
                continue;
            }

            Boolean moAlreadyCheckedOutByUser = false;
            try {
                if (msi.getLockBeforeUpdateMos() != null && msi.getLockBeforeUpdateMos().contains(mo)) {
                    if (mo.isCheckedoutButNotByUser(user)) {
                        msi.addLockedMo(mo);
                        msi.addSkippedMosLocked(mo);
                        continue;
                    } else {
                        if (!mo.isCheckedout()) {
                            moSvc.checkOut(user, mo.getId());
                        } else {
                            moAlreadyCheckedOutByUser = true;
                        }
                    }
                }
                Boolean updateMade = false;
                List<MetaDataItem> mdis = mo.getMetaDataItems();
                if (item.getDeletedMetadataProperties() != null) {
                    for (String key : item.getDeletedMetadataProperties().keySet()) {
                        for (String value : item.getDeletedMetadataProperties().get(key)) {
                            for (MetaDataItem mdi : mdis) {
                                if (mdi.getName().equals(key) && mdi.getValue().equals(value)) {
                                    moSvc.removeMetaDataEntry(user, mo.getId(), mdi);
                                    updateMade = true;
                                }
                            }
                        }
                    }
                }
                if (item.getNewMetadataProperties() != null) {
                    for (String key : item.getNewMetadataProperties().keySet()) {
                        List<MetaDataItem> newMdis = new ArrayList<MetaDataItem>();
                        for (String value : item.getNewMetadataProperties().get(key)) {
                            // log.info("Adding new metadata item for " +
                            // item.getMatchedId() + ": " + value);
                            MetaDataItem mdi = new MetaDataItem(key, value);
                            newMdis.add(mdi);
                        }
                        moSvc.addMetaDataEntries(user, mo.getId(), newMdis);
                        updateMade = true;
                    }
                }

                if (msi.getLockBeforeUpdateMos() != null && msi.getLockBeforeUpdateMos().contains(mo) && moAlreadyCheckedOutByUser == false) {
                    ObjectCheckInOptions options = new ObjectCheckInOptions();
                    options.setVersionType(VersionType.MINOR);
                    options.setVersionNote("Metadata updated from spreadsheet");
                    moSvc.checkIn(user, mo.getId(), options);
                }
                if (updateMade == true) {
                    msi.addUpdatedMo(mo);
                }
            } catch (RSuiteException e) {
                if (msi.getLockBeforeUpdateMos() != null && msi.getLockBeforeUpdateMos().contains(mo) && moAlreadyCheckedOutByUser == false) {
                    try {
                        moSvc.undoCheckout(user, mo.getId());
                    } catch (RSuiteException e1) {
                        log.error("MetadataImporter().importMetadata: Error cancelling checkout of MO. " + e);
                    }
                }
                msi.addSkippedMosOther(mo);
                log.error("MetadataImporter().importMetadata: Error updating MO. " + e);
            }
        }
    }

    public static MetadataSpreadsheetInformation readMetadata(ExecutionContext context, User user, InputStream excelStream, String metadataFilename)
            throws RSuiteException {
        String methodName = "readMetadata(): ";

        String extension = FilenameUtils.getExtension(metadataFilename);

        MetadataSpreadsheetInformation results = new MetadataSpreadsheetInformation();

        if (!extension.equalsIgnoreCase("xlsx") && !extension.equalsIgnoreCase("xls")) {
            results.addFailureMessage("Wrong File Type", metadataFilename, "Expected '.xls' or '.xlsx' extension. Filename was \"" + metadataFilename + "\"");
            return results;
        }

        log.info("readMetadata(): Reading file \"" + metadataFilename + "\"...");

        try {

            Workbook wb = getWorksheet(excelStream, extension);

            Sheet sheet = wb.getSheetAt(0);
            List<ContentObjectMetadata> moMetadataItems = readContentMetadataItems(context, user, sheet, results);

            if (moMetadataItems.size() == 0) {
                log.info(methodName + "No Data");
                ProcessFailureMessage msg = new GenericProcessFailureMessage("No Data", "Spreadsheet", "Failed to find any entries.");
                results.addFailureMessage(msg);
            }
            results.addCOMList(moMetadataItems);

            log.info("readMetadata(): Done");
        } catch (Exception e) {
            results.addFailureMessage(new GenericProcessFailureMessage("Unexpected Exception", "processLogSheet()", "Unexpected "
                    + e.getClass().getSimpleName() + " processing metadata: " + e.getMessage()));
            log.error("metadataLog(): Unexpected exception processing metadata spreadsheet: " + e.getMessage(), e);
        }

        return results;
    }

    public static List<ContentObjectMetadata> readContentMetadataItems(ExecutionContext context, User user, Sheet sheet, MetadataSpreadsheetInformation results)
            throws RSuiteException {
        String methodName = "readContentMetadataItems(): ";

        log.info(methodName + "Reading Spreadsheet");

        Cell cell = null;

        List<ContentObjectMetadata> objects = new ArrayList<ContentObjectMetadata>();

        Row headingRow = sheet.getRow(0);
        Row firstDataRow = sheet.getRow(1);

        // Get the metadata field names from the heading row
        log.info(methodName + "Reading Header Row");
        List<String> headingFieldNames = new ArrayList<String>();
        int idIndex = -1;
        for (int cellNdx = 0; cellNdx < headingRow.getLastCellNum(); cellNdx++) {
            String fieldName = POIHelper.getCellValueAsString(headingRow.getCell(cellNdx)).trim();
            if (idIndex == -1 && isIdField(fieldName)) {
                idIndex = cellNdx;
            }
            headingFieldNames.add(fieldName);
            // log.info("Found header value: " + fieldName);
        }
        results.populateDedupedFieldList(headingFieldNames);

        // Now iterate over the data rows. First empty cell in column 1
        // signals end of data rows.
        Row row = null;
        for (int rowNdx = firstDataRow.getRowNum(); rowNdx < sheet.getLastRowNum() + 1; rowNdx++) {
            row = sheet.getRow(rowNdx);
            try {
                cell = row.getCell(0);
            } catch (Exception e) {
                // row.getCell(0) returns an exception when you reach the empty
                // row above the row that contains the list of fields
                break;
            }
            String firstCellValue = POIHelper.getCellValueAsString(cell);
            if ("Report fields".equals(firstCellValue.trim())) {
                break;
            }

            List<String> fieldValues = new ArrayList<String>();
            for (int cellNdx = 0; cellNdx < headingRow.getLastCellNum(); cellNdx++) {
                String fieldValue = "";
                if (cellNdx < row.getLastCellNum()) {
                    fieldValue = POIHelper.getCellValueAsString(row.getCell(cellNdx)).trim();
                }
                fieldValues.add(fieldValue);
            }
            ContentObjectMetadata object = null;
            try {
                object = new ContentObjectMetadata(context, user, fieldValues.get(idIndex));
                int i = 0;
                for (String fieldName : headingFieldNames) {
                    // iterate over header fields, and for those that are LMD fields, populate list of values for this row
                    if (results.getDedupedFieldList().contains(fieldName) && fieldValues.size() > i && !fieldValues.get(i).isEmpty()) {
                        object.addMetadataProperty(fieldName, fieldValues.get(i));
                    }
                    i++;
                }
                for (String propKey : results.getDedupedFieldList()) {
                    List<String> spreadsheetPropValues = object.getMetadataProperties().get(propKey);
                    List<String> existingValues = object.getExistingMoValuesForProperty(context, propKey);
                    for (String propValue : existingValues) {
                        if (spreadsheetPropValues == null || !spreadsheetPropValues.contains(propValue)) {
                            object.addDeletedMetadataProperty(propKey, propValue);
                        }
                    }
                    for (String propValue : existingValues) {
                        if (spreadsheetPropValues != null && spreadsheetPropValues.contains(propValue)) {
                            object.addUnchangedMetadataProperty(propKey, propValue);
                        }
                    }
                    if (spreadsheetPropValues != null) {
                        for (String propValue : spreadsheetPropValues) {
                            if (!existingValues.contains(propValue)) {
                                object.addNewMetadataProperty(propKey, propValue);
                            }
                        }
                    }
                    LayeredMetadataDefinition def = context.getMetaDataService().getLayeredMetaDataDefinition(user, propKey);
                    if (def == null) {
                        results.addUndefinedField(object.getMo(), propKey);
                    } else {
                        Boolean isAvailable = def.isAssociatedWithElementCriteria(object.getMo().getNamespaceURI(), object.getMo().getLocalName(),
                                new ElementMatchingOptions());
                        Boolean repeatable = def.allowMultiple();
                        Boolean isVersioned = def.isVersioned();
                        Boolean moIsLocked = object.getMo().isCheckedoutButNotByUser(user);

                        int deletedValues = 0;
                        if (object.getDeletedMetadataProperties().containsKey(propKey)) {
                            deletedValues = object.getDeletedMetadataProperties().get(propKey).size();
                        }
                        int unchangedValues = 0;
                        if (object.getUnchangedMetadataProperties().containsKey(propKey)) {
                            unchangedValues = object.getUnchangedMetadataProperties().get(propKey).size();
                        }
                        int newValues = 0;
                        if (object.getNewMetadataProperties().containsKey(propKey)) {
                            newValues = object.getNewMetadataProperties().get(propKey).size();
                        }
                        int valueCount = unchangedValues + newValues;
                        if (isAvailable == false) {
                            results.addUnavailableField(object.getMo(), propKey);
                        } else if (repeatable == false && valueCount > 1) {
                            results.addTooManyValuesField(object.getMo(), propKey);
                        }
                        if (deletedValues + newValues > 0 && isVersioned == true && moIsLocked == true) {
                            results.addVersionedButLockedField(object.getMo(), propKey);
                        } else if (deletedValues + newValues > 0 && isVersioned == true) {
                            // This isn't fullproof logic. Could have an
                            // unchanged md field that is the only one that
                            // requires a lock, in which case this would result
                            // in an unnecessary version. Fix if anyone
                            // ever cares.
                            if (results.getLockBeforeUpdateMos() != null && !results.getLockBeforeUpdateMos().contains(object.getMo())) {
                                results.addLockBeforeUpdateMo(object.getMo());
                            }
                        }
                    }
                }
                objects.add(object);
            } catch (RSuiteException e) {
                log.info(methodName + "Error while getting ContentObjectMetadata: " + e.getMessage());
                if (!results.getbadIds().contains(fieldValues.get(idIndex))) {
                    results.addBadIds(fieldValues.get(idIndex));
                }
            }
        }

        return objects;
    }

    private static Boolean isIdField(String fieldName) {
        Boolean isId = false;
        if (fieldName.equalsIgnoreCase("id") || fieldName.equalsIgnoreCase("RSuite id") || fieldName.equalsIgnoreCase("RSuite alias")
                || fieldName.equalsIgnoreCase("alias")) {
            isId = true;
        }
        return isId;
    }

}
