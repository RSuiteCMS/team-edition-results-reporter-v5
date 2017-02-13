package com.rsicms.teamEdition.resultsReporter.utils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.reallysi.rsuite.api.Alias;
import com.reallysi.rsuite.api.ManagedObject;
import com.reallysi.rsuite.api.MetaDataItem;
import com.reallysi.rsuite.api.ObjectType;
import com.reallysi.rsuite.api.RSuiteException;
import com.reallysi.rsuite.api.User;
import com.reallysi.rsuite.api.extensions.ExecutionContext;
import com.reallysi.rsuite.api.remoteapi.result.ByteSequenceResult;
import com.reallysi.rsuite.api.remoteapi.result.HtmlPageResult;
import com.rsicms.pluginUtilities.POI.POIHelper;
import com.rsicms.rsuite.helpers.utils.RSuiteUtils;
import com.rsicms.teamEdition.TEUtils;
import com.rsicms.teamEdition.resultsReporter.datamodel.ContentObjectMetadata;

/**
 * Manages export of metadata to a spreadsheet.
 * 
 */
public class MetadataReporter {

    private static String logPrefix = "";
    private static Log log = LogFactory.getLog(MetadataReporter.class);
    private static final String EXCEL_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    public static Document generateReportAsXml(User user, ExecutionContext context, String skey, List<ManagedObject> moList, List<String> columns,
            String reportTitle, String reportUserInfo) throws RSuiteException {
        logPrefix = "MetadataReporter.generateReportAsXml(): ";

        DocumentBuilderFactory docFactory = context.getXmlApiManager().getDocumentBuilderFactory();
        docFactory.setNamespaceAware(true);
        DocumentBuilder builder;
        try {
            builder = docFactory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            log.error(logPrefix + "ParserConfigurationException " + e.getMessage() + e);
            throw new RSuiteException("ParserConfigurationException: " + e);
        }
        Document reportDoc = builder.newDocument();
        reportDoc.setXmlStandalone(true);
        Element reportElement = reportDoc.createElement("report");
        Element tableElement = reportDoc.createElement("table");

        try {
            List<ContentObjectMetadata> comList = getLmdObjectListForObjects(user, context, moList);
            Map<String, Integer> maxFieldCounts = getMaxFieldCounts(comList);
            List<String> sortedFieldNames = getSortedColumnList(columns, maxFieldCounts);
            Map<Short, String> headerValues = buildHeaderMap(maxFieldCounts, sortedFieldNames);
            Element titleElement = reportDoc.createElement("reportTitle");
            titleElement.setTextContent(reportTitle);
            Element userInfoElement = reportDoc.createElement("reportUserInfo");
            userInfoElement.setTextContent(reportUserInfo);
            Element reportFieldsElement = reportDoc.createElement("reportFields");
            String fieldList = StringUtils.join(columns, ",");
            reportFieldsElement.setTextContent(fieldList);
            buildHeadTr(tableElement, headerValues);
            buildBodyTrs(context, skey, tableElement, comList, maxFieldCounts, sortedFieldNames);

            reportElement.appendChild(titleElement);
            reportElement.appendChild(userInfoElement);
            reportElement.appendChild(reportFieldsElement);
            reportElement.appendChild(tableElement);
            reportDoc.appendChild(reportElement);
        } catch (Exception e) {
            log.error(logPrefix + "Exception constructing report: " + e.getMessage(), e);
        }
        return reportDoc;
    }

    public static void formatReportAsHtmlResult(ExecutionContext context, String serverUrl, String skey, String excelUrl, HtmlPageResult result,
            Document reportDocument) throws RSuiteException {
        URI transformUrl = null;
        try {
            transformUrl = new URI("rsuite:/res/plugin/team-edition-results-reporter/xslt/report.xsl");
        } catch (URISyntaxException e) {
            log.error(logPrefix + "URISyntaxException " + e.getMessage() + e);
        }
        logPrefix = "MetadataReporter.formatReportAsHtmlResult(): ";
        try {
            Source xmlSource = new DOMSource(reportDocument);
            Transformer transformer = context.getXmlApiManager().getTransformer(transformUrl);
            transformer.setParameter("rsuite.sessionkey", skey);
            transformer.setParameter("rsuite.serverurl", serverUrl);
            transformer.setParameter("report.excel.url", excelUrl);
            transformer.setParameter("report.stylesheet.url", "/rsuite-cms/plugin/team-edition-results-reporter/reports.css?skey=" + skey);
            transformer.transform(xmlSource, result.getStreamResult());
        } catch (FactoryConfigurationError e) {
            log.error(logPrefix + "FactoryConfigurationError " + e.getMessage() + e);
        } catch (TransformerException e) {
            log.error(logPrefix + "TransformerException " + e.getMessage() + e);
        }
    }

    public static void formatReportAsExcelFile(ExecutionContext context, ByteSequenceResult result, Document reportDocument) throws RSuiteException {
        logPrefix = "MetadataReporter.formatReportAsExcelFile(): ";
        File spreadsheetFile = null;
        Sheet sheet = POIHelper.createNewWorkbookAndSheet("report");
        try {
            Map<Short, String> headerValues = getHeaderValuesMap(reportDocument);
            POIHelper.addRowWithCells(sheet, (short) 0, headerValues, true);
            List<HashMap<Short, String>> bodyRows = getBodyValuesMap(reportDocument);
            Short rowNum = 1;
            for (HashMap<Short, String> cellValues : bodyRows) {
                // log.info(logPrefix + "Writing body row" + "...");
                HashMap<Short, String> updatedValues = new HashMap<Short, String>();
                for (Short key : cellValues.keySet()) {
                	String val = cellValues.get(key);
                	if (val.startsWith("/rsuite/") || val.startsWith("/rsuite-cms/"))
                		val = TEUtils.getRsuiteServerUrl(context) + val;
                    updatedValues.put(key, val.replaceAll("\\{\\{brk\\}\\}", "\n").trim());
                }
                POIHelper.addRowWithCells(sheet, rowNum, updatedValues, false);
                rowNum++;
            }
            Map<Short, String> reportFieldsValues = getReportFieldsMap(reportDocument);
            POIHelper.addRow(sheet, rowNum);
            rowNum++;
            Row row = POIHelper.addRow(sheet, rowNum);
            Cell cellA = POIHelper.addCell(sheet, row, (short) 0, reportFieldsValues.get((short) 0), false);
            Cell cellB = POIHelper.addCell(sheet, row, (short) 1, reportFieldsValues.get((short) 1), false);
            
            CellStyle cellStyleA = sheet.getWorkbook().createCellStyle();
            Font fontA = sheet.getWorkbook().createFont();
            fontA.setBoldweight(Font.BOLDWEIGHT_BOLD);
            fontA.setColor(IndexedColors.BLUE.getIndex());
            cellStyleA.setFont(fontA);
            cellA.setCellStyle(cellStyleA);

            CellStyle cellStyleB = sheet.getWorkbook().createCellStyle();
            Font fontB = sheet.getWorkbook().createFont();
            fontB.setColor(IndexedColors.BLUE.getIndex());
            cellStyleB.setFont(fontB);
            cellB.setCellStyle(cellStyleB);

            Integer lastColumn = 1;
            if (headerValues.size() > 1)
            	lastColumn = headerValues.size() - 1;
            sheet.addMergedRegion(new CellRangeAddress(
                    (int) rowNum, //first row (0-based)
                    (int) rowNum, //last row  (0-based)
                    1, //first column (0-based)
                    lastColumn  //last column  (0-based)
            ));
            // POIHelper.addRowWithCells(sheet, (short) rowNum,
            // reportFieldsValues, false);
            log.info(logPrefix + "Saving spreadsheet to file");
            File tempDir = context.getRSuiteServerConfiguration().getTmpDir();
            String spreadsheetFileName = POIHelper.saveWorkbookToFile(sheet.getWorkbook(), tempDir);
            spreadsheetFile = new File(spreadsheetFileName);
        } catch (Throwable t) {
            log.error("Unexpected error: " + t.getMessage());
            throw new RSuiteException("Unexpected error: " + t);
        }
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        try {
            byte[] b = FileUtils.readFileToByteArray(spreadsheetFile);
            outStream.write(b);
        } catch (IOException e) {
            log.error(logPrefix + "IOException " + e.getMessage() + e);
        }
        result.setOutputStream(outStream);
        result.setContentType(EXCEL_CONTENT_TYPE);
    }

    private static void buildHeadTr(Element tableElement, Map<Short, String> headerValues) throws XMLStreamException {
        Document doc = tableElement.getOwnerDocument();
        Element trElement = doc.createElement("tr");
        for (Short headerCellNum : headerValues.keySet()) {
            Element thElement = doc.createElement("th");
            thElement.setTextContent(headerValues.get(headerCellNum));
            trElement.appendChild(thElement);
        }
        tableElement.appendChild(trElement);
    }

    private static void buildBodyTrs(ExecutionContext context, String skey, Element tableElement, List<ContentObjectMetadata> comList,
            Map<String, Integer> maxFieldCounts, List<String> sortedFieldNames) throws RSuiteException, XMLStreamException {
        List<HashMap<Short, String>> bodyRows = buildBodyRows(context, skey, comList, maxFieldCounts, sortedFieldNames);
        Document doc = tableElement.getOwnerDocument();
        for (HashMap<Short, String> cellValues : bodyRows) {
            // log.info(logPrefix + "Writing body row" + "...");
            Element trElement = doc.createElement("tr");
            for (Short bodyCellNum : cellValues.keySet()) {
                Element tdElement = doc.createElement("td");
                tdElement.setTextContent(cellValues.get(bodyCellNum));
                trElement.appendChild(tdElement);
            }
            tableElement.appendChild(trElement);
        }
    }

    private static List<HashMap<Short, String>> buildBodyRows(ExecutionContext context, String skey, List<ContentObjectMetadata> comList,
            Map<String, Integer> maxFieldCounts, List<String> sortedFieldNames) throws RSuiteException {
        //log.info(logPrefix + "Building body XML...");
        List<HashMap<Short, String>> bodyRows = new ArrayList<HashMap<Short, String>>();
        Short rowNum = (short) 1;
        for (ContentObjectMetadata com : ContentObjectMetadata.getSortedListOfComs(comList)) {
            HashMap<Short, String> cellValues = new HashMap<Short, String>();
            Short cellNum = (short) 0;
            for (String fieldName : sortedFieldNames) {
                // log.info(logPrefix + "Writing data for field: " + fieldName +
                // "/row " + rowNum);
                if (fieldName.equalsIgnoreCase("RSuite Id")) {
                    cellValues.put(cellNum, com.getMo().getId());
                    cellNum++;
                } else if (fieldName.equalsIgnoreCase("Last Modified")) {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                    cellValues.put(cellNum, sdf.format(com.getMo().getDtModified()));
                    cellNum++;
                } else if (fieldName.equalsIgnoreCase("Date Created")) {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                    cellValues.put(cellNum, sdf.format(com.getMo().getDtCreated()));
                    cellNum++;
                } else if (fieldName.equalsIgnoreCase("Check Out Date")) {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                    if (com.getMo().isCheckedout()) {
                        cellValues.put(cellNum, sdf.format(com.getMo().getDtCheckedOut()));
                    } else {
                        cellValues.put(cellNum, "");
                    }
                    cellNum++;
                } else if (fieldName.equalsIgnoreCase("Check Out User")) {
                    if (com.getMo().isCheckedout()) {
                        cellValues.put(cellNum, com.getMo().getCheckedOutUser());
                    } else {
                        cellValues.put(cellNum, "");
                    }
                    cellNum++;
                } else if (fieldName.equalsIgnoreCase("Created By")) {
                	String userId = com.getMo().getVersionHistory().getVersionEntries().get(com.getMo().getVersionHistory().getVersionEntries().size()-1).getUserId();
                    cellValues.put(cellNum, userId);
                    cellNum++;
                } else if (fieldName.equalsIgnoreCase("Last Modified By")) {
                    cellValues.put(cellNum, com.getMo().getUserId());
                    cellNum++;
                } else if (fieldName.equalsIgnoreCase("Object Type")) {
                    ObjectType type = com.getMo().getObjectType();
                    if (type == ObjectType.CONTENT_ASSEMBLY || type == ObjectType.CONTENT_ASSEMBLY_NODE) {
                        cellValues.put(cellNum, com.getMo().getContainerType());
                    } else if (type == ObjectType.MANAGED_OBJECT) {
                        cellValues.put(cellNum, "XML (" + com.getMo().getLocalName() + ")");
                    } else if (type == ObjectType.MANAGED_OBJECT_NONXML) {
                        cellValues.put(cellNum, "Asset");
                    }
                    cellNum++;
                } else if (fieldName.equalsIgnoreCase("Content Type")) {
                    cellValues.put(cellNum, com.getMo().getContentType());
                    cellNum++;
                } else if (fieldName.equalsIgnoreCase("Name")) {
                	String name = com.getMo().getDisplayName();
                	if (name == null || name.isEmpty())
                		name = "";
                    cellValues.put(cellNum, name);
                    cellNum++;
                } else if (fieldName.equalsIgnoreCase("Alias")) {
                    Alias[] aliases = com.getMo().getAliases();
                    String alias = "";
                    if (aliases != null) {
                        for (int i = 0; i < aliases.length; i++) {
                            if (aliases[i].getType() != null && aliases[i].getType().equalsIgnoreCase("filename")) {
                                alias = aliases[i].getText();
                            }
                        }
                    }
                    if (alias.isEmpty() && aliases.length > 0) {
                        alias = aliases[0].getText();
                    }
                    cellValues.put(cellNum, alias);
                    cellNum++;
                } else if (fieldName.equalsIgnoreCase("Current Version Number")) {
                    String versionLabel = com.getMo().getVersionHistory().getCurrentVersionEntry().getRevisionNumber() + " "
                            + com.getMo().getVersionHistory().getCurrentVersionEntry().getDtCommitted() + " "
                            + com.getMo().getVersionHistory().getCurrentVersionEntry().getNote();
                    cellValues.put(cellNum, versionLabel);
                    cellNum++;
                } else if (fieldName.equalsIgnoreCase("Metadata Summary")) {
                    String metadataSummary = getMetadataSummary(com.getMo());
                    cellValues.put(cellNum, metadataSummary);
                    cellNum++;
                } else if (fieldName.equalsIgnoreCase("Thumbnail")) {
                    String thumbnail = getThumbnail(context, skey, com.getMo());
                    cellValues.put(cellNum, thumbnail);
                    cellNum++;
                } else if (com.getMetadataProperties().get(fieldName) != null) {
                    for (String fieldValue : com.getMetadataProperties().get(fieldName)) {
                        // log.info(logPrefix + "" + rowNum + "/" + cellNum +
                        // " = " + fieldValue);
                        cellValues.put(cellNum, fieldValue);
                        cellNum++;
                    }
                } else if (!maxFieldCounts.containsKey(fieldName)) {
                    // create one blank entry for the lmd field for which no
                    // objects yet have a value
                    cellValues.put(cellNum, "");
                    cellNum++;
                }
                if (maxFieldCounts.containsKey(fieldName)) {
                    Integer valsForThisField = 0;
                    if (com.getMetadataProperties().get(fieldName) != null) {
                        valsForThisField = com.getMetadataProperties().get(fieldName).size();
                    }
                    if (maxFieldCounts.get(fieldName) > 0 && maxFieldCounts.get(fieldName) > valsForThisField) {
                        // log.info(logPrefix +
                        // "Calculating how many blanks cells to add: " +
                        // maxFieldCounts.get(fieldName) + " - "
                        // + valsForThisField);
                        Integer offset = maxFieldCounts.get(fieldName) - valsForThisField;
                        // log.info(logPrefix + "Adding cells to column count: "
                        // + cellNum + " + " + offset);
                        for (int b = 0; b < offset; b++) {
                            cellValues.put(cellNum, "");
                            cellNum++;
                        }
                    }
                }
            }
            bodyRows.add(cellValues);
            rowNum++;
        }
        return bodyRows;
    }

    private static Map<Short, String> buildHeaderMap(Map<String, Integer> maxFieldCounts, List<String> sortedFieldNames) {
        //log.info(logPrefix + "Building header XML");
        Map<Short, String> headerValues = new HashMap<Short, String>();
        Short headerCellNum = 0;
        for (String fieldName : sortedFieldNames) {
            // log.info(logPrefix + "Writing header row entry(ies) for " +
            // fieldName);
            if (!maxFieldCounts.containsKey(fieldName)) {
                headerValues.put(headerCellNum, fieldName);
                headerCellNum++;
            } else {
                for (int i = 0; i < maxFieldCounts.get(fieldName); i++) {
                    headerValues.put(headerCellNum, fieldName);
                    headerCellNum++;
                }
            }
        }
        return headerValues;
    }

    private static Map<Short, String> getHeaderValuesMap(Document reportDocument) {
        // log.info(logPrefix + "Building header cells");
        Map<Short, String> headerValues = new HashMap<Short, String>();
        Element reportElement = reportDocument.getDocumentElement();
        NodeList thElements = reportElement.getElementsByTagName("th");
        // assumes only one header row
        for (int i = 0; i < thElements.getLength(); i++) {
            headerValues.put((short) i, thElements.item(i).getTextContent());
        }
        return headerValues;
    }

    private static Map<Short, String> getReportFieldsMap(Document reportDocument) {
        Map<Short, String> fieldListValues = new HashMap<Short, String>();
        Element reportElement = reportDocument.getDocumentElement();
        NodeList reportListElement = reportElement.getElementsByTagName("reportFields");
        fieldListValues.put((short) 0, "Report fields:");
        fieldListValues.put((short) 1, reportListElement.item(0).getTextContent());
        return fieldListValues;
    }

    private static List<HashMap<Short, String>> getBodyValuesMap(Document reportDocument) {
        // log.info(logPrefix + "Building body cells");
        List<HashMap<Short, String>> bodyValues = new ArrayList<HashMap<Short, String>>();
        Element reportElement = reportDocument.getDocumentElement();
        NodeList trElements = reportElement.getElementsByTagName("tr");
        for (int i = 0; i < trElements.getLength(); i++) {
            NodeList tdElements = trElements.item(i).getChildNodes();
            HashMap<Short, String> bodyRowValues = new HashMap<Short, String>();
            for (int n = 0; n < tdElements.getLength(); n++) {
                if (tdElements.item(n).getNodeType() == Node.ELEMENT_NODE && tdElements.item(n).getNodeName().equals("td")) {
                    bodyRowValues.put((short) n, tdElements.item(n).getTextContent());
                }
            }
            if (bodyRowValues.size() > 0) {
                bodyValues.add(bodyRowValues);
            }
        }
        return bodyValues;
    }

    private static List<String> getSortedColumnList(List<String> columns, Map<String, Integer> maxFieldCounts) {
        // log.info(logPrefix + "Getting list of columns for report...");
        List<String> sortedFieldNames = new ArrayList<String>();
        if (columns != null) {
            sortedFieldNames = columns;
        } else {
            for (String fieldName : maxFieldCounts.keySet()) {
                sortedFieldNames.add(fieldName);
            }
            Collections.sort(sortedFieldNames);
            sortedFieldNames.add(0, "RSuite id");
            sortedFieldNames.add(1, "Name");
        }
        return sortedFieldNames;
    }

    private static Map<String, Integer> getMaxFieldCounts(List<ContentObjectMetadata> comList) {
        // log.info(logPrefix +
        // "Calculating number of columns needed for each metadata field...");
        Map<String, Integer> maxFieldCounts = new HashMap<String, Integer>();
        for (ContentObjectMetadata com : comList) {
            for (String fieldName : com.getMetadataProperties().keySet()) {
                if (!fieldName.startsWith("_")) {
                    if (maxFieldCounts.containsKey(fieldName)) {
                        if (maxFieldCounts.get(fieldName) < com.getMetadataProperties().get(fieldName).size()) {
                            maxFieldCounts.put(fieldName, com.getMetadataProperties().get(fieldName).size());
                        }
                    } else {
                        maxFieldCounts.put(fieldName, com.getMetadataProperties().get(fieldName).size());
                    }
                }
            }
        }
        return maxFieldCounts;
    }

    private static List<ContentObjectMetadata> getLmdObjectListForObjects(User user, ExecutionContext context, List<ManagedObject> moList)
            throws RSuiteException {
        // log.info(logPrefix + "Reading metadata for items to export...");
        List<ContentObjectMetadata> comList = new ArrayList<ContentObjectMetadata>();
        for (ManagedObject mo : moList) {
            ContentObjectMetadata com = new ContentObjectMetadata(context, user, mo.getId());
            List<String> lmdNames = RSuiteUtils.getLmdFieldNames(context, user, mo);
            for (String lmdName : lmdNames) {
                for (MetaDataItem mi : mo.getMetaDataItems()) {
                    if (mi.getName().equals(lmdName)) {
                        com.addMetadataProperty(lmdName, mi.getValue());
                    }
                }
            }
            comList.add(com);
        }
        return comList;
    }

    private static String getThumbnail(ExecutionContext context, String skey, ManagedObject mo) throws RSuiteException {
        String thumbnailUri = "";
        if (mo.isNonXml()) {
            if (mo.getVariant("rsuite:thumbnail") != null) {
                thumbnailUri = TEUtils.getRestUrlV2(context) + "content/binary?id=" + mo.getId() + "&skey=" + skey + "&variant=rsuite%3Athumbnail";
            }
        }
        return thumbnailUri.toString();
    }

    private static String getMetadataSummary(ManagedObject mo) throws RSuiteException {
        StringBuffer metadataSummary = new StringBuffer();
        List<MetaDataItem> mdis = mo.getMetaDataItems();
        for (MetaDataItem mdi : mdis) {
            metadataSummary.append(mdi.getName()).append(" = ").append(mdi.getValue()).append("{{brk}}");
        }
        return metadataSummary.toString();
    }

}
