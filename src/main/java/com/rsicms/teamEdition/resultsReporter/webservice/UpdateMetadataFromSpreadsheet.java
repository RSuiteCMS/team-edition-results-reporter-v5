package com.rsicms.teamEdition.resultsReporter.webservice;

import java.util.List;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.reallysi.rsuite.api.RSuiteException;
import com.reallysi.rsuite.api.User;
import com.reallysi.rsuite.api.remoteapi.CallArgument;
import com.reallysi.rsuite.api.remoteapi.CallArgumentList;
import com.reallysi.rsuite.api.remoteapi.DefaultRemoteApiHandler;
import com.reallysi.rsuite.api.remoteapi.RemoteApiExecutionContext;
import com.reallysi.rsuite.api.remoteapi.RemoteApiResult;
import com.reallysi.rsuite.api.remoteapi.result.HtmlPageResult;
import com.rsicms.teamEdition.TEUtils;
import com.rsicms.teamEdition.resultsReporter.datamodel.ContentObjectMetadata;
import com.rsicms.teamEdition.resultsReporter.utils.MetadataImporter;
import com.rsicms.teamEdition.resultsReporter.utils.MetadataSpreadsheetInformation;

/**
 * 
 */
public class UpdateMetadataFromSpreadsheet extends DefaultRemoteApiHandler {

    private static Log log = LogFactory.getLog(UpdateMetadataFromSpreadsheet.class);
    private String classPrefix = "UpdateMetadataFromSpreadsheet(): ";

    @Override
    public RemoteApiResult execute(RemoteApiExecutionContext context, CallArgumentList args) throws RSuiteException {

        log.debug(classPrefix + "Returned arguments are: ");
        for (CallArgument arg : args.getAll()) {
            log.debug(classPrefix + "  " + arg.getName() + " = " + arg.getValue());
        }

        FileItem spreadsheetFile = args.getFirstFile("spreadsheet");
        String reportOnly = args.getFirstString("reportOnly");
        Boolean preFlight = false;
        if (reportOnly != null) {
            preFlight = true;
        }

        User user = context.getSession().getUser();

        StringBuffer reportSb = new StringBuffer();

        MetadataSpreadsheetInformation spreadsheetInfo = MetadataImporter.readSpreadsheet(context, user, spreadsheetFile);
        if (preFlight == false) {
            MetadataImporter.importMetadata(context, user, spreadsheetInfo);
        }
        formatReport(context, spreadsheetInfo, reportSb, preFlight);

        HtmlPageResult result = new HtmlPageResult(reportSb.toString());
        result.setContentType("text/html");
        return result;

    }

    private void formatReport(RemoteApiExecutionContext context, MetadataSpreadsheetInformation spreadsheetInfo, StringBuffer reportSb, Boolean preFlight)
            throws RSuiteException {
        reportSb.append("<html>");
        reportSb.append("<head><title>Metadata Import</title>");
        reportSb.append("<link rel=\"stylesheet\" type=\"text/css\" href=\"").append("/rsuite-cms/")
                .append("plugin/team-edition-results-reporter/reports.css?skey=").append(context.getSession().getKey()).append("\" />");
        reportSb.append("<script type=\"text/javascript\" src=\"").append(TEUtils.getRestUrlV1(context))
                .append("static/team-edition-results-reporter/scripts/sorttable.js\"></script>");
        reportSb.append("</head>");
        reportSb.append("<body>");
        if (preFlight == true) {
            reportSb.append("<h2>Metadata Import *Preflight Check*</h2>");
        } else {
            reportSb.append("<h2>Metadata Import</h2>");
        }

        if (spreadsheetInfo.hasFailures() || spreadsheetInfo.hasWarnings()) {
            reportSb.append("<p class=\"failure\">RSuite could not read the spreadsheet: </p>").append(spreadsheetInfo.reportFailuresByTypeAsHtml(30))
                    .append(spreadsheetInfo.reportWarningsByTypeAsHtml(30));
        } else {
            String rerun = "<p class=\"check\">If this is correct, run the import again to apply the changes.</p>";
            if (preFlight == true) {
                reportSb.append(rerun);
            } else {
                int updateCount = 0;
                if (spreadsheetInfo.getUpdatedMos() != null) {
                    updateCount = spreadsheetInfo.getUpdatedMos().size();
                }
                reportSb.append("<p class=\"check\">The import updated metadata for ").append(updateCount).append(" item(s).</p>");
            }
            reportSb.append("<table class=\"sortable\">");
            reportSb.append("<tr><th width=\"10%\">RSuite ID</th><th width=\"15%\">Matched on</th><th width=\"15%\">Title</th><th width=\"60%\">Metadata Changes");
            reportSb.append("<table class=\"plain\" width=\"100%\">");
            reportSb.append("<tr><th width=\"15%\">Field</th><th width=\"15%\">Delete</th><th width=\"15%\">Keep</th><th width=\"15%\">Add</th><th width=\"40%\">Errors</th></tr>");
            reportSb.append("</td></tr></table></th></tr>");
            List<String> badIdList = spreadsheetInfo.getbadIds();
            if (badIdList != null) {
                for (String id : badIdList) {
                    reportSb.append("<tr>");
                    reportSb.append("<td>").append(id).append("</td>");
                    reportSb.append("<td>");
                    reportSb.append("<span style=\"color: red\">").append("no match").append("</span> ");
                    reportSb.append("</td>");
                    reportSb.append("<td></td>");
                    reportSb.append("<td>");
                    reportSb.append("<table class=\"plain\" width=\"100%\">");
                    reportSb.append("<tr>");
                    reportSb.append("<td width=\"15%\"></td>");
                    reportSb.append("<td width=\"15%\"></td>");
                    reportSb.append("<td width=\"15%\"></td>");
                    reportSb.append("<td width=\"15%\"></td>");
                    reportSb.append("<td width=\"40%\">");
                    reportSb.append("<span style=\"color: red\">").append("This ID could not be found.").append("</span> ");
                    reportSb.append("</td></tr></table>");
                    reportSb.append("</td>");
                    reportSb.append("</tr>");
                }
            }
            List<ContentObjectMetadata> coms = spreadsheetInfo.getCOMList();
            for (ContentObjectMetadata com : coms) {
                List<String> undefinedFieldList = spreadsheetInfo.getUndefinedFields().get(com.getMo());
                List<String> unavailableFieldList = spreadsheetInfo.getUnavailableFields().get(com.getMo());
                List<String> tooManyValuesFieldList = spreadsheetInfo.getTooManyValuesFields().get(com.getMo());
                List<String> versionedButLockedFieldList = spreadsheetInfo.getVersionedButLockedFields().get(com.getMo());
                Boolean skippedLocked = false;
                if (spreadsheetInfo.getSkippedMosLocked() != null && spreadsheetInfo.getSkippedMosLocked().contains(com.getMo())) {
                    skippedLocked = true;
                }
                Boolean skippedMdError = false;
                if (spreadsheetInfo.getSkippedMosMdErrors() != null && spreadsheetInfo.getSkippedMosMdErrors().contains(com.getMo())) {
                    skippedMdError = true;
                }
                Boolean skippedOther = false;
                if (spreadsheetInfo.getSkippedMosOther() != null && spreadsheetInfo.getSkippedMosOther().contains(com.getMo())) {
                    skippedOther = true;
                }

                String updatedMo = "";
                if (spreadsheetInfo.getUpdatedMos().contains(com.getMo())) {
                    updatedMo = " class=\"updatedMo\"";
                }
                reportSb.append("<tr>");
                reportSb.append("<td").append(updatedMo).append(">").append(com.getMo().getId()).append("</td>");
                reportSb.append("<td").append(updatedMo).append(">").append(com.getMatchedId()).append("</td>");
                reportSb.append("<td").append(updatedMo).append(">").append(com.getMo().getDisplayName()).append("</td>");
                reportSb.append("<td>");
                if (skippedLocked == true) {
                    reportSb.append("<span style=\"color: red; display: block\">").append("NO UPDATES MADE: ITEM MUST FIRST BE CHECKED IN.").append("</span> ");
                }
                if (skippedMdError == true) {
                    reportSb.append("<span style=\"color: red; display: block\">").append("NO UPDATES MADE: METADATA ERRORS WOULD RESULT.").append("</span> ");
                }
                if (skippedOther == true) {
                    reportSb.append("<span style=\"color: red; display: block\">")
                            .append("NO UPDATES MADE: UNEXPECTED PROBLEMS. CHECK THAT THE ITEM IS NOT LOCKED BY ANOTHER USER AND TRY AGAIN.")
                            .append("</span> ");
                }
                reportSb.append("<table class=\"plain\" width=\"100%\">");

                for (String propKey : spreadsheetInfo.getDedupedFieldList()) {
                    List<String> newPropValues = com.getNewMetadataProperties().get(propKey);
                    List<String> unchangedPropValues = com.getUnchangedMetadataProperties().get(propKey);
                    List<String> deletedPropValues = com.getDeletedMetadataProperties().get(propKey);
                    reportSb.append("<tr>");
                    reportSb.append("<td width=\"15%\">").append(propKey).append("</td>");
                    reportSb.append("<td width=\"15%\">");
                    if (deletedPropValues != null) {
                        for (String propValue : deletedPropValues) {
                            reportSb.append("<span style=\"text-decoration:line-through\">").append(propValue).append("</span> ");
                        }
                    }
                    reportSb.append("</td>");
                    reportSb.append("<td width=\"15%\">");
                    if (unchangedPropValues != null) {
                        for (String propValue : unchangedPropValues) {
                            reportSb.append(propValue).append(" ");
                        }
                    }
                    reportSb.append("</td>");
                    reportSb.append("<td width=\"15%\">");
                    if (newPropValues != null) {
                        for (String propValue : newPropValues) {
                            reportSb.append(propValue).append(" ");
                        }
                    }
                    reportSb.append("</td>");
                    reportSb.append("<td width=\"40%\">");
                    if (undefinedFieldList != null && undefinedFieldList.contains(propKey)) {
                        reportSb.append("<span style=\"color: red; display: block\">").append("This metadata field is not defined.").append("</span> ");
                    }
                    if (unavailableFieldList != null && unavailableFieldList.contains(propKey)) {
                        reportSb.append("<span style=\"color: red; display: block\">").append("This field is not available for this content item.")
                                .append("</span> ");
                    }
                    if (tooManyValuesFieldList != null && tooManyValuesFieldList.contains(propKey)) {
                        reportSb.append("<span style=\"color: red; display: block\">").append("Only one value is permitted for this field.").append("</span> ");
                    }
                    if (versionedButLockedFieldList != null && versionedButLockedFieldList.contains(propKey)) {
                        reportSb.append("<span style=\"color: red; display: block\">")
                                .append("Changing this field requires a check out, but the content item is locked to another user.").append("</span> ");
                    }
                    reportSb.append("</td>");
                    reportSb.append("</tr>");
                }
                reportSb.append("</table>");
                reportSb.append("</td>");
                reportSb.append("</tr>");
            }
            reportSb.append("</table>");
            if (preFlight == true) {
                reportSb.append(rerun);
            }
        }
    }

}
