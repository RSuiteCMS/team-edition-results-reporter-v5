package com.rsicms.teamEdition.resultsReporter.webservice;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Document;

import com.reallysi.rsuite.api.ContentAssembly;
import com.reallysi.rsuite.api.ContentAssemblyItem;
import com.reallysi.rsuite.api.ManagedObject;
import com.reallysi.rsuite.api.ObjectType;
import com.reallysi.rsuite.api.RSuiteException;
import com.reallysi.rsuite.api.Session;
import com.reallysi.rsuite.api.User;
import com.reallysi.rsuite.api.content.ContentDisplayObject;
import com.reallysi.rsuite.api.content.ContentObjectPath;
import com.reallysi.rsuite.api.extensions.ExecutionContext;
import com.reallysi.rsuite.api.remoteapi.CallArgument;
import com.reallysi.rsuite.api.remoteapi.CallArgumentList;
import com.reallysi.rsuite.api.remoteapi.ContextPath;
import com.reallysi.rsuite.api.remoteapi.DefaultRemoteApiHandler;
import com.reallysi.rsuite.api.remoteapi.RemoteApiExecutionContext;
import com.reallysi.rsuite.api.remoteapi.RemoteApiResult;
import com.reallysi.rsuite.api.remoteapi.result.ByteSequenceResult;
import com.reallysi.rsuite.api.remoteapi.result.HtmlPageResult;
import com.reallysi.rsuite.api.remoteapi.result.NotificationResult;
import com.reallysi.rsuite.api.search.Search;
import com.reallysi.rsuite.api.search.SearchHistory;
import com.reallysi.rsuite.api.search.SearchResultSet;
import com.reallysi.rsuite.service.ContentAssemblyService;
import com.reallysi.rsuite.service.ManagedObjectService;
import com.rsicms.rsuite.helpers.utils.RSuiteUtils;
import com.rsicms.teamEdition.TEUtils;
import com.rsicms.teamEdition.resultsReporter.utils.MetadataReporter;

public class ReturnReportResults extends DefaultRemoteApiHandler {

    private static Log log = LogFactory.getLog(ReturnReportResults.class);
    private String classPrefix = "ReturnReportResults(): ";
    private String reportTitle = "";
    private String reportUserInfo = "";
    private String reportSourceName = "";
    private static SimpleDateFormat dt = new SimpleDateFormat("yyyy-MM-dd");

    @Override
    public RemoteApiResult execute(RemoteApiExecutionContext context, CallArgumentList args) throws RSuiteException {

//        log.info(classPrefix + "Returned arguments are: ");
//        for (CallArgument arg : args.getAll()) {
//            log.info(classPrefix + "  " + arg.getName() + " = " + arg.getValue());
//        }
        User user = context.getSession().getUser();

        String reportType = args.getFirstValue("reportType");

        List<String> fieldList = getFieldList(args);
        List<ManagedObject> moList = getMoList(context, args, user);
        Document reportDocument = MetadataReporter.generateReportAsXml(user, context, context.getSession().getKey(), moList, fieldList, reportTitle, reportUserInfo);

        if (reportType.equals("preview")) {
            HtmlPageResult htmlResult = new HtmlPageResult();
            String skey = context.getSession().getKey();
            String urlArgs = "";
            for (CallArgument arg : args.getAll()) {
                if (!arg.getName().equals("apiName") && !arg.getName().equals("reportType")
                        && !arg.getValue().isEmpty()) {
                    urlArgs = urlArgs + "&" + arg.getName() + "=" + arg.getValue();
                }
            }
            String excelUrl = TEUtils.getRestUrlV1(context) + "api/team-edition-results-reporter:ReturnReportResults?skey=" + skey + "&reportType=excel" + urlArgs;
            MetadataReporter.formatReportAsHtmlResult(context, TEUtils.getRsuiteServerUrl(context), skey, excelUrl, htmlResult, reportDocument);
            return htmlResult;
        } else if (reportType.equals("excel")) {
            String suggestedFilename = "Metadata Report - " + reportSourceName + " (" + user.getUserId() + "-" + moList.size() + " items) " + dt.format(new Date());
            /*
             * if a folder content report + folder name + number items + date if
             * search content report + search results + number items + date
             */
            ByteSequenceResult result = new ByteSequenceResult();
            MetadataReporter.formatReportAsExcelFile(context, result, reportDocument);
            result.setSuggestedFileName(suggestedFilename + ".xlsx");
            return result;
        }
        return new NotificationResult("Report failed.");
    }

    private List<String> getFieldList(CallArgumentList args) {
        List<String> fieldList = new ArrayList<String>();
        if (args.getFirstString("textListOfFields") != null && !args.getFirstString("textListOfFields").isEmpty()) {
            for (String fieldName : args.getFirstString("textListOfFields").split(",")) {
                fieldList.add(fieldName.trim());
            }
            return fieldList;
        }
        for (CallArgument arg : args.getAll("metadataIdType")) {
            if (arg.getValue().equals("RSuite Id and Alias")) {
                fieldList.add("RSuite Id");
                fieldList.add("Alias");
            } else if (arg.getValue().equals("RSuite Id")) {
                fieldList.add("RSuite Id");
            } else {
                fieldList.add("Alias");
            }
        }
        for (CallArgument arg : args.getAll("lmdSelection")) {
            fieldList.add(arg.getValue());
        }
        for (CallArgument arg : args.getAll("systemMdSelection")) {
            fieldList.add(arg.getValue());
        }
        return fieldList;
    }

    private List<ManagedObject> getMoList(RemoteApiExecutionContext context, CallArgumentList args, User user) throws RSuiteException {
        ManagedObjectService moSvc = context.getManagedObjectService();
        ContentAssemblyService caSvc = context.getContentAssemblyService();

        String exportType = args.getFirstValue("exportType");

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat sdfTime = new SimpleDateFormat("HH:mm");
        SimpleDateFormat sdfUsTime = new SimpleDateFormat("hh:mm");
        Date now = new Date();
        String amPm = "am";
        if (!sdfTime.format(now).equals(sdfUsTime.format(now))) {
            amPm = "pm";
        }
        reportUserInfo = "Report run by user " + user.getUserId() + " on " + sdf.format(now) + " at " + sdfTime.format(now) + " (" + sdfUsTime.format(now) + amPm + ") (server time)";
        List<ManagedObject> moList = new ArrayList<ManagedObject>();
        if (exportType.equals("ca")) {
            //log.info(classPrefix + "Exporting metadata for a container");
            String caId = args.getFirstValue("rsuiteId");
            ManagedObject caMo = moSvc.getManagedObject(user, caId);
            if (caMo == null) {
        		log.error("Selected item with id " + caId + " is null. This is unexpected.");
        		throw new RSuiteException("Selected item with id " + caId + " is null. Content might be corrupt.");
            }
            try {
            	caMo = RSuiteUtils.getRealMo(context, user, caMo);
            } catch (Exception e) {
            	log.error("The real MO for reference MO with id " + caMo.getId() + " returned null and will not be included in the report.");
            	throw new RSuiteException("The real MO for selected item with id " + caId + " is null. Content might be corrupt.");
            }
            if (!caMo.getObjectType().equals(ObjectType.CONTENT_ASSEMBLY) && !caMo.getObjectType().equals(ObjectType.CONTENT_ASSEMBLY_NODE)) {
        		log.error("Selected item with id " + caId + " is not a folder. Only folders can be reported against with this feature.");
        		throw new RSuiteException("Selected item with id " + caId + " is not a folder. Only folders can be reported against with this feature.");
            }
            ContentAssembly contextCa = caSvc.getContentAssembly(user, caMo.getId());
            getDescendantMos(user, context, contextCa, moList, true);
            reportSourceName = caMo.getDisplayName();
            reportTitle = "Folder Report: " + caMo.getDisplayName() + " (" + moList.size() + " items, subfolders included)";
            //log.info(classPrefix + "MO count whose metadata will be exported (descendants of " + contextCa.getId() + "): " + moList.size());
        } else if (exportType.equals("results")) {
            String searchId = args.getFirstValue("searchId");
            String allResults = args.getFirstValue("allResults");
            if (searchId == null) {
            	if (args.getContentObjectPaths(user) == null || args.getContentObjectPaths(user).size() == 0) {
            		log.error("ContentObjectPaths not set for remoteapihandler when scope=broweTree! Can't use results reporter for all results in folders until fixed.");
            		throw new RSuiteException("ContentObjectPaths not set for remoteapihandler when scope=broweTree! Can't use results reporter for all results in folders until fixed.");
            	}
            	List<ContentObjectPath> cops = args.getContentObjectPaths(user);
            	ContentObjectPath cop = cops.get(0);
            	String copUri = cop.getUri();
                ContextPath cp = ContextPath.fromString(copUri);
            	ManagedObject caMo = null;
            	for (int i = 0; i < cp.size(); i++) {
            		String id = cp.get(cp.size() - (i+1)).getId();
            		caMo = moSvc.getManagedObject(user, id);
                    if (caMo == null) {
                    	log.error("A folder in the results with ID=" + id + " is null and cannot be included in the report.");
                    	continue;
                    }
                    try {
                    	caMo = RSuiteUtils.getRealMo(context, user, caMo);
                    } catch (Exception e) {
                    	log.error("The real MO for reference MO with id " + caMo.getId() + " returned null and will not be included in the report.");
                    	break;
                    }
                    if (caMo.getObjectType().equals(ObjectType.CONTENT_ASSEMBLY)) 
                    	break;
            	}
                if (allResults != null && allResults.equals("allResults")) {
                    ContentAssembly contextCa = caSvc.getContentAssembly(user, caMo.getId());
                    reportSourceName = caMo.getDisplayName();
                    //log.info(classPrefix + "Exporting metadata for all browse results");
                    getDescendantMos(user, context, contextCa, moList, false);
                    reportTitle = "Folder Report: " + caMo.getDisplayName() + " (" + moList.size() + " items, subfolders excluded)";
                    // TODO Get the advised list of managed objects
                } else {
                    reportSourceName = caMo.getDisplayName();
                    //log.info(classPrefix + "Exporting metadata for selected browse results");
                    getMoListForSelections(context, moSvc, args, user, moList);
                    reportTitle = "Folder Report: " + caMo.getDisplayName() + " (" + moList.size() + " selected items)";
                }
                //log.info(classPrefix + "MO count whose metadata will be exported (browse results): " + moList.size());
            } else {
                if (allResults != null && allResults.equals("allResults")) {
                    //log.info(classPrefix + "Exporting metadata for all search results");
                    addAllSearchResultsToMoList(context, user, moList, searchId);
                    reportTitle = "Search Results Report (all " + moList.size() + " results)";
                } else {
                    //log.info(classPrefix + "Exporting metadata for selected search results");
                    getMoListForSelections(context, moSvc, args, user, moList);
                    reportTitle = "Search Results Report (" + moList.size() + " selected results)";
                }
                reportSourceName = "Search Results";
                //log.info(classPrefix + "MO count whose metadata will be exported (search results): " + moList.size());
            }
        } else if (exportType.equals("briefcase")) {
            //log.info(classPrefix + "Exporting metadata from briefcase");
            getMoListForSelections(context, moSvc, args, user, moList);
            reportSourceName = "Briefcase";
            reportTitle = "Briefcase Items Report (" + moList.size() + " items)";
            //log.info(classPrefix + "MO count whose metadata will be exported (briefcase): " + moList.size());
        }
        return moList;
    }

    private void getMoListForSelections(RemoteApiExecutionContext context, ManagedObjectService moSvc, CallArgumentList args, User user,
            List<ManagedObject> moList) throws RSuiteException {
        for (ManagedObject mo : args.getManagedObjects(user)) {
            try {
            	mo = RSuiteUtils.getRealMo(context, user, mo);
            } catch (Exception e) {
            	log.error("The real MO for reference MO with id " + mo.getId() + " returned null and will not be included in the report.");
            	continue;
            }
            moList.add(mo);
        }
    }

    private List<ManagedObject> addAllSearchResultsToMoList(RemoteApiExecutionContext context, User user, List<ManagedObject> moList, String searchId)
            throws RSuiteException {
        Session sess = context.getSession();
        SearchHistory searchHist = (SearchHistory) sess.getAttribute("SearchHistory");
        if (searchHist != null) {
            Search search = searchHist.getSearch(searchId);
            SearchResultSet results = search.getResults();
            for (int i = 1; i <= results.getCountResults(); i++) {
                try {
                    //log.info("Adding result with count " + i);
                    ContentDisplayObject item = results.getResult(i);
                    if (item.getManagedObject() == null) {
                    	log.error("An object in the selection is null and cannot be included in the report.");
                    	continue;
                    }
                    ManagedObject mo = null;
                    try {
                    	mo = RSuiteUtils.getRealMo(context, user, item.getManagedObject());
                    } catch (Exception e) {
                    	log.error("The real MO for reference MO with id " + mo.getId() + " returned null and will not be included in the report.");
                    	continue;
                    }
                    moList.add(mo);
                } catch (Exception e) {
                    /*
                     * TODO probably just that count was inaccurate/exceeded
                     * returned bucket size. Need better way of making sure get
                     * all the actual results and also don't get error.
                     */
                    break;
                }
            }
        }
        return moList;
    }

    private void getDescendantMos(User user, ExecutionContext context, ContentAssembly ca, List<ManagedObject> moList, Boolean descendants)
            throws RSuiteException {
        ContentAssemblyService casvc = context.getContentAssemblyService();
        ManagedObjectService mosvc = context.getManagedObjectService();

        List<? extends ContentAssemblyItem> kids = ca.getChildrenObjects();
        for (ContentAssemblyItem kid : kids) {
            ManagedObject mo = mosvc.getManagedObject(user, kid.getId());
            if (mo == null) {
            	log.error("A descendent object is null and cannot be included in the report.");
            	continue;
            }
            try {
            mo = RSuiteUtils.getRealMo(context, user, mo);
            } catch (Exception e) {
            	log.error("The real MO for reference MO with id " + mo.getId() + " returned null and will not be included in the report.");
            	continue;
            }
            moList.add(mo);
            ObjectType type = mo.getObjectType();
            // Not attempting to handling CA Nodes here
            if (type == ObjectType.CONTENT_ASSEMBLY) {
                if (descendants == true) {
                    ContentAssembly childCa = casvc.getContentAssembly(user, mo.getId());
                    if (childCa == null) {
                    	log.error("A descendent folder is null and cannot be included in the report.");
                    	continue;
                    }
                    getDescendantMos(user, context, childCa, moList, true);
                }
            }
        }
    }

}
