
// ESXConfigListLoader.java --
//
// ESXConfigListLoader.java is part of ElectricCommander.
//
// Copyright (c) 2005-2010 Electric Cloud, Inc.
// All rights reserved.
//

package ecplugins.esx.client;

import java.util.HashMap;
import java.util.Map;

import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.RequestException;
import com.google.gwt.http.client.Response;
import com.google.gwt.xml.client.Node;

import com.electriccloud.commander.gwt.client.ChainedCallback;
import com.electriccloud.commander.gwt.client.ComponentBase;
import com.electriccloud.commander.gwt.client.HasErrorPanel;
import com.electriccloud.commander.gwt.client.MultiRequestLoader;
import com.electriccloud.commander.gwt.client.MultiRequestLoaderCallback;
import com.electriccloud.commander.gwt.client.legacyrequests.CommanderRequestCallback;
import com.electriccloud.commander.gwt.client.legacyrequests.GetPropertyRequest;
import com.electriccloud.commander.gwt.client.requests.CgiRequestProxy;
import com.electriccloud.commander.gwt.client.requests.Loader;
import com.electriccloud.commander.gwt.client.responses.CommanderError;
import com.electriccloud.commander.gwt.client.util.StringUtil;

import static com.electriccloud.commander.gwt.client.ComponentBaseFactory.getPluginName;
import static com.electriccloud.commander.gwt.client.util.XmlUtil.getNodeByName;
import static com.electriccloud.commander.gwt.client.util.XmlUtil.getNodeValueByName;

public class ESXConfigListLoader
    extends Loader
{

    //~ Instance fields --------------------------------------------------------

    private final ESXConfigList m_configList;
    private final CgiRequestProxy  m_cgiRequestProxy;
    private String                 m_editorName;

    //~ Constructors -----------------------------------------------------------

    public ESXConfigListLoader(
            ESXConfigList configList,
            ComponentBase    queryObject,
            ChainedCallback  callback)
    {
        this(configList, null, queryObject, callback);
    }

    public ESXConfigListLoader(
            ESXConfigList configList,
            String           implementedMethod,
            ComponentBase    queryObject,
            ChainedCallback  callback)
    {
        super(queryObject, callback);
        m_configList      = configList;
        m_cgiRequestProxy = new CgiRequestProxy(getPluginName(), "esx.cgi");
    }

    //~ Methods ----------------------------------------------------------------

    @Override public void load()
    {
        Map<String, String> cgiParams = new HashMap<String, String>();

        cgiParams.put("cmd", "getCfgList");
        loadConfigs(cgiParams);
    }

    private void loadConfigs(Map<String, String> cgiParams)
    {

        try {
            String request = m_cgiRequestProxy.issueGetRequest(cgiParams,
                    new RequestCallback() {
                        @Override public void onError(
                                Request   request,
                                Throwable exception)
                        {
                            ((HasErrorPanel) m_queryObject).addErrorMessage(
                                "Error loading ESX configuration list: ",
                                exception);
                        }

                        @Override public void onResponseReceived(
                                Request  request,
                                Response response)
                        {
                            String responseString = response.getText();

                            // if HTML returned we never made it to the CGI
                            Boolean isHtml = (responseString.indexOf(
                                        "DOCTYPE HTML") != -1);
                            String  error;

                            if (!isHtml) {
                                error = m_configList.parseResponse(
                                        responseString);
                            }
                            else {
                                error = responseString;
                            }

                            if (m_queryObject.getLog()
                                             .isDebugEnabled()) {
                                m_queryObject.getLog()
                                             .debug(
                                                 "Recieved CGI response: "
                                                 + responseString
                                                 + " isHTML:" + isHtml
                                                 + " error:" + error);
                            }

                            if (error != null) {
                                ((HasErrorPanel) m_queryObject).addErrorMessage(
                                    error);
                            }
                            else {

                                if (StringUtil.isEmpty(m_editorName)
                                        || m_configList.isEmpty()) {

                                    // We're done!
                                    if (m_callback != null) {
                                        m_callback.onComplete();
                                    }
                                }
                                else {
                                    loadEditors();
                                }
                            }
                        }
                    });

            if (m_queryObject.getLog()
                             .isDebugEnabled()) {
                m_queryObject.getLog()
                             .debug("Issued CGI request: " + request);
            }
        }
        catch (RequestException e) {

            if (m_queryObject instanceof HasErrorPanel) {
                ((HasErrorPanel) m_queryObject).addErrorMessage(
                    "Error loading ESX configuration list: ", e);
            }
            else {
                m_queryObject.getLog()
                             .error(e);
            }
        }
    }

    private void loadEditors()
    {
        MultiRequestLoader loader  = new MultiRequestLoader(m_queryObject,
                new MultiRequestLoaderCallback() {
                    @Override public void onComplete()
                    {

                        // We're done!
                        if (m_callback != null) {
                            m_callback.onComplete();
                        }
                    }
                });
        GetPropertyRequest request = new GetPropertyRequest(
                "/plugins/EC-ESX/project/ui_forms/" + m_editorName);

        request.setExpand(false);
        loader.addRequest(request, new EditorLoaderCallback("esxcfg"));
        loader.load();
    }

    public void setEditorName(String editorName)
    {
        m_editorName = editorName;
    }

    //~ Inner Classes ----------------------------------------------------------

    public class EditorLoaderCallback
        implements CommanderRequestCallback
    {

        //~ Instance fields ----------------------------------------------------

        private final String m_configPlugin;

        //~ Constructors -------------------------------------------------------

        public EditorLoaderCallback(String configPlugin)
        {
            m_configPlugin = configPlugin;
        }

        //~ Methods ------------------------------------------------------------

        @Override public void handleError(Node responseNode)
        {
            CommanderError error = new CommanderError(responseNode);

            if (m_queryObject instanceof HasErrorPanel) {
                ((HasErrorPanel) m_queryObject).addErrorMessage(error);
            }
            else {
                m_queryObject.getLog()
                             .error(error);
            }
        }

        @Override public void handleResponse(Node responseNode)
        {

            if (m_queryObject.getLog()
                             .isDebugEnabled()) {
                m_queryObject.getLog()
                             .debug("Commander getProperty request returned: "
                                 + responseNode);
            }

            Node propertyNode = getNodeByName(responseNode, "property");

            if (propertyNode != null) {
                String value = getNodeValueByName(propertyNode, "value");

                if (!StringUtil.isEmpty(value)) {
                    m_configList.setEditorDefinition(m_configPlugin, value);

                    return;
                }
            }

            // There was no property value found in the response
            String errorMsg = "Editor '" + m_editorName
                + "' not found for ESX plugin '" + m_configPlugin + "'";

            if (m_queryObject instanceof HasErrorPanel) {
                ((HasErrorPanel) m_queryObject).addErrorMessage(errorMsg);
            }
            else {
                m_queryObject.getLog()
                             .error(errorMsg);
            }
        }
    }
}