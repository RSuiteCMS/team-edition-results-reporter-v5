package com.rsicms.teamEdition.resultsReporter.webservice;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.reallysi.rsuite.api.RSuiteException;
import com.reallysi.rsuite.api.remoteapi.CallArgumentList;
import com.reallysi.rsuite.api.remoteapi.DefaultRemoteApiHandler;
import com.reallysi.rsuite.api.remoteapi.RemoteApiExecutionContext;
import com.reallysi.rsuite.api.remoteapi.RemoteApiResult;
import com.reallysi.rsuite.api.remoteapi.result.InvokeWebServiceAction;
import com.reallysi.rsuite.api.remoteapi.result.RestResult;
import com.reallysi.rsuite.api.remoteapi.result.UserInterfaceAction;

/**
 * 
 */
public class ProcessReportSubmission extends DefaultRemoteApiHandler {

    private static Log log = LogFactory.getLog(ProcessReportSubmission.class);
    private String classPrefix = "ProcessReportSubmission(): ";


    @Override
    public RemoteApiResult execute(RemoteApiExecutionContext context, CallArgumentList args) throws RSuiteException {

//        log.info(classPrefix + "Returned arguments are: ");
//        for (CallArgument arg : args.getAll()) {
//            log.info(classPrefix + "  " + arg.getName() +  " = " + arg.getValue());
//        }

    	log.info(classPrefix + "Launching report generator."); 
        RestResult result = new RestResult();
        UserInterfaceAction action = new InvokeWebServiceAction("team-edition-results-reporter:ReturnReportResults");
        action.addProperty("formParams", args); // make the properties available as arguments in the form handler
        action.addProperty("serviceParams", args); // make the properties available as arguments in the web service
        action.addProperty("useTransport", "tab");
        result.addAction(action);
        return result;
    }

}
