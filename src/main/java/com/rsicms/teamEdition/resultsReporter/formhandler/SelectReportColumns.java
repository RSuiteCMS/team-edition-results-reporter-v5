package com.rsicms.teamEdition.resultsReporter.formhandler;

import static com.rsicms.pluginUtilities.FormsUtils.addFormSelectTypeParameter;
import static com.rsicms.pluginUtilities.FormsUtils.addFormSubmitButtonsParameter;
import static com.rsicms.pluginUtilities.FormsUtils.addFormTextParameter;
import static com.rsicms.pluginUtilities.FormsUtils.addFormHiddenParameter;
import static com.rsicms.pluginUtilities.FormsUtils.allowMultiple;
import static com.rsicms.pluginUtilities.FormsUtils.dontAllowMultiple;
import static com.rsicms.pluginUtilities.FormsUtils.notReadOnly;
import static com.rsicms.pluginUtilities.FormsUtils.notRequired;
import static com.rsicms.pluginUtilities.FormsUtils.nullDataType;
import static com.rsicms.pluginUtilities.FormsUtils.nullDataTypeOptions;
import static com.rsicms.pluginUtilities.FormsUtils.nullValidationMessage;
import static com.rsicms.pluginUtilities.FormsUtils.nullValidationRegex;
import static com.rsicms.pluginUtilities.FormsUtils.nullValue;
import static com.rsicms.pluginUtilities.FormsUtils.nullValues;
import static com.rsicms.pluginUtilities.FormsUtils.required;
import static com.rsicms.pluginUtilities.FormsUtils.sortNatural;
import static com.rsicms.pluginUtilities.FormsUtils.sortNoSort;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.reallysi.rsuite.api.DataType;
import com.reallysi.rsuite.api.DataTypeOptionValue;
import com.reallysi.rsuite.api.FormControlType;
import com.reallysi.rsuite.api.LayeredMetadataDefinition;
import com.reallysi.rsuite.api.RSuiteException;
import com.reallysi.rsuite.api.extensions.Plugin;
import com.reallysi.rsuite.api.forms.DataTypeManager;
import com.reallysi.rsuite.api.forms.DefaultFormHandler;
import com.reallysi.rsuite.api.forms.FormColumnInstance;
import com.reallysi.rsuite.api.forms.FormDefinition;
import com.reallysi.rsuite.api.forms.FormInstance;
import com.reallysi.rsuite.api.forms.FormInstanceCreationContext;
import com.reallysi.rsuite.api.forms.FormParameterInstance;
import com.reallysi.rsuite.api.remoteapi.CallArgument;
import com.reallysi.rsuite.api.vfs.BrowsePath;

/**
 * Provides lists of asset types into the selected container.
 * 
 */
public class SelectReportColumns extends DefaultFormHandler {
    private static Log log = LogFactory.getLog(SelectReportColumns.class);

    public void initialize(FormDefinition formDefinition) {
    }

    @Override
    public void adjustFormInstance(FormInstanceCreationContext context, FormInstance formInstance) throws RSuiteException {
//        log.info("Returned arguments are: ");
//        for (CallArgument arg : context.getArgs().getAll()) {
//            log.info("  " + arg.getName() + " = " + arg.getValue());
//        }
        String exportType = context.getArgs().getFirstValue("exportType", "");
        
        Plugin fcPlugin = context.getPluginManager().get("rsuite-fieldchooser-control");
        FormControlType selectorFCT = FormControlType.CHECKBOX;
        if (fcPlugin != null) {
        	selectorFCT = FormControlType.fromName("fieldchooser");
        } else {
        	log.info("Field chooser plugin not deployed - using checklist instead.");
        }

        String instructions = "Select the fields you want to include in the report.";
        if (exportType.equals("results")) {
            instructions += " If you are reporting on the contents of a folder, only items directly in the "
                    + "folder are included. If you would also like to report on content in subfolders, run the " + "report by clicking on the folder instead.";
        } else if (exportType.equals("ca")) {
            instructions += " The report will include items in subfolders.";
        }
        formInstance.setInstructions(instructions);

        //Create data type for identifiers
        List<DataTypeOptionValue> idDatatypeOptions = new ArrayList<DataTypeOptionValue>();
        idDatatypeOptions.add(new DataTypeOptionValue("RSuite Id", "RSuite Id"));
        idDatatypeOptions.add(new DataTypeOptionValue("Alias", "Alias"));
        idDatatypeOptions.add(new DataTypeOptionValue("RSuite Id and Alias", "RSuite Id and Alias"));

        //Populate data type for layered metadata definitions
        List<DataTypeOptionValue> lmdDatatypeOptions = new ArrayList<DataTypeOptionValue>();
        Map<String, LayeredMetadataDefinition> mdDefs = context.getMetaDataService().getLayeredMetaDataDefinitions();
        for (String md : mdDefs.keySet()) {
            if (!md.startsWith("_")) {
            	String label = md;
            	if (mdDefs.get(md).getLabel() != null && !mdDefs.get(md).getLabel().isEmpty())
            		label = mdDefs.get(md).getLabel();
                lmdDatatypeOptions.add(new DataTypeOptionValue(md, label));
            }
        }
        //Get data type for system fields that can be exported
        DataTypeManager dtMgr = context.getDomainManager().getCurrentDomainContext().getDataTypeManager();
        DataType dt = dtMgr.getDataType(context.getUser(), "teBaseReportColumns");
        DataTypeOptionValue[] systemMdDatatypeOptions = dt.getOptionValuesProvider().getOptionValues();
        List<DataTypeOptionValue> systemMdDatatypeOptionsList = new ArrayList<DataTypeOptionValue>();
        for (DataTypeOptionValue datatypeValue : systemMdDatatypeOptions) {
            systemMdDatatypeOptionsList.add(datatypeValue);
        }

        List<FormColumnInstance> cols = new ArrayList<FormColumnInstance>();
        
        FormColumnInstance fci = new FormColumnInstance();
        FormColumnInstance fciMdFields = new FormColumnInstance();
        FormColumnInstance fciSystemFields = new FormColumnInstance();
        FormColumnInstance fciTextFields = new FormColumnInstance();

        List<FormParameterInstance> params = new ArrayList<FormParameterInstance>();
        List<FormParameterInstance> paramsMdFields = new ArrayList<FormParameterInstance>();
        List<FormParameterInstance> paramsSystemFields = new ArrayList<FormParameterInstance>();
        List<FormParameterInstance> paramsTextFields = new ArrayList<FormParameterInstance>();

        //Create the all results check box
        if (exportType.equals("results")) {
            List<DataTypeOptionValue> resultsOptions = new ArrayList<DataTypeOptionValue>();
            resultsOptions.add(new DataTypeOptionValue("allResults", "All results"));
            String[] values = nullValues;
            if (context.getArgs().getFirstValue("rsuiteId", "").isEmpty()) {
                values = new String[1];
                values[0] = "allResults";
            }
            addFormSelectTypeParameter(FormControlType.CHECKBOX, params, "allResults", "Include all results or only selected results?", nullDataType,
                    resultsOptions, values, sortNoSort, dontAllowMultiple, notRequired, notReadOnly);
        }

        //Populate ID column
        String[] idValues = new String[1];
        idValues[0] = idDatatypeOptions.get(2).getValue();
        addFormSelectTypeParameter(FormControlType.RADIOBUTTON, params, "metadataIdType", "Identifier(s)", "", idDatatypeOptions, idValues,
                sortNoSort, dontAllowMultiple, required, notReadOnly);
        

        String[] defaultSystemValues = new String[1];
        defaultSystemValues[0] = "Name";
        
        //system values column
        addFormSelectTypeParameter(selectorFCT, paramsSystemFields, "systemMdSelection", "System fields", nullDataType, systemMdDatatypeOptionsList, defaultSystemValues,
                sortNoSort, allowMultiple, notRequired, notReadOnly);
        
        //metadata column
        addFormSelectTypeParameter(selectorFCT, paramsMdFields, "lmdSelection", "Metadata fields", nullDataType, lmdDatatypeOptions, nullValues,
                sortNatural, allowMultiple, notRequired, notReadOnly);

        //text alternative "column"
        addFormTextParameter(paramsTextFields, "textListOfFields", "List of comma-delimited fields (alternative to selecting above; copy list from a previous report)", 
                nullDataType, nullDataTypeOptions, nullValue, notRequired, nullValidationRegex, nullValidationMessage, notReadOnly);
        
        fci.addParams(params);
        fci.setName("reportColumnsCol1");
        cols.add(fci);

        fciSystemFields.addParams(paramsSystemFields);
        fciSystemFields.setName("reportColumnsCol2");
        cols.add(fciSystemFields);

        fciMdFields.addParams(paramsMdFields);
        fciMdFields.setName("reportColumnsCol3");
        cols.add(fciMdFields);
        
        fciTextFields.addParams(paramsTextFields);
        fciTextFields.setName("reportColumnsColText");
        cols.add(fciTextFields);

        List<FormParameterInstance> controlsParams = new ArrayList<FormParameterInstance>();
        List<DataTypeOptionValue> submitButtonValues = new ArrayList<DataTypeOptionValue>();
        submitButtonValues.add(new DataTypeOptionValue("submit", "Create Report"));
        submitButtonValues.add(new DataTypeOptionValue("cancel", "Cancel"));
        addFormSubmitButtonsParameter(controlsParams, "submitButton", submitButtonValues);
        FormColumnInstance controlsFci = new FormColumnInstance();
        controlsFci.addParams(controlsParams);
        controlsFci.setName("controls");
        cols.add(controlsFci);

        formInstance.setColumns(cols);

    }

}
