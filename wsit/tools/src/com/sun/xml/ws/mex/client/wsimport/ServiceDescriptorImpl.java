/*
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License).  You may not use this file except in
 * compliance with the License.
 * 
 * You can obtain a copy of the license at
 * https://glassfish.dev.java.net/public/CDDLv1.0.html.
 * See the License for the specific language governing
 * permissions and limitations under the License.
 * 
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at https://glassfish.dev.java.net/public/CDDLv1.0.html.
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * you own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 * 
 * Copyright 2006 Sun Microsystems Inc. All Rights Reserved
 */
package com.sun.xml.ws.mex.client.wsimport;

import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.sun.tools.ws.api.ServiceDescriptor;
import com.sun.xml.ws.mex.client.HttpPoster;
import com.sun.xml.ws.mex.client.MetadataClient;
import com.sun.xml.ws.mex.client.schema.Metadata;
import com.sun.xml.ws.mex.client.schema.MetadataReference;
import com.sun.xml.ws.mex.client.schema.MetadataSection;
import static com.sun.xml.ws.mex.MetadataConstants.POLICY_DIALECT;
import static com.sun.xml.ws.mex.MetadataConstants.SCHEMA_DIALECT;
import static com.sun.xml.ws.mex.MetadataConstants.WSDL_DIALECT;
import javax.xml.ws.WebServiceException;

import org.w3c.dom.Node;

public class ServiceDescriptorImpl extends ServiceDescriptor {
    
    private final List<Source> wsdls;
    private final List<Source> schemas;
    private final List<Source> policies;
    
    private static final Logger logger =
        Logger.getLogger(ServiceDescriptorImpl.class.getName());
    
    public ServiceDescriptorImpl(Metadata mData) {
        wsdls = new ArrayList<Source>();
        schemas = new ArrayList<Source>();
        policies = new ArrayList<Source>();
        populateLists(mData);
    }
    
    /*
     * This will be called recursively for metadata sections
     * that contain metadata references.
     */
    private void populateLists(Metadata mData) {
        for (MetadataSection section : mData.getMetadataSection()) {
            if (section.getMetadataReference() != null) {
                handleReference(section);
            } else if (section.getLocation() != null) {
                handleLocation(section);
            } else {
                handleXml(section);
            }
        }
    }

    private void handleXml(MetadataSection section) {
        String dialect = section.getDialect();
        if (dialect.equals(WSDL_DIALECT)) {
            wsdls.add(createSource(section));
        } else if (dialect.equals(SCHEMA_DIALECT)) {
            schemas.add(createSource(section));
        } else if (dialect.equals(POLICY_DIALECT)) {
            policies.add(createSource(section));
        } else {
            // todo: log unknown dialect
        }
    }

    private void handleReference(MetadataSection section) {
        MetadataReference ref = section.getMetadataReference();
        populateLists(new MetadataClient().retrieveMetadata(ref));
    }
    
    private void handleLocation(MetadataSection section) {
        String location = section.getLocation();
        String dialect = section.getDialect();
        if (dialect.equals(WSDL_DIALECT)) {
            wsdls.add(getSourceFromLocation(location));
        } else if (dialect.equals(SCHEMA_DIALECT)) {
            schemas.add(getSourceFromLocation(location));
        } else if (dialect.equals(POLICY_DIALECT)) {
            policies.add(getSourceFromLocation(location));
        } else {
            // todo: log unknown dialect
        }
    }
    
    public List<Source> getWSDLs() {
        return wsdls;
    }

    public List<Source> getSchemas() {
        return schemas;
    }
    
    public List<Source> getPolicies() {
        return policies;
    }
    
    private Source createSource(MetadataSection section) {
        Node n = (Node) section.getAny();
        return new DOMSource(n);
    }
    
    private Source getSourceFromLocation(String address) {
        try {
            HttpPoster poster = new HttpPoster();
            InputStream response = poster.makeGetCall(address);
            return new StreamSource(response);
        } catch (Exception e) {
            throw new WebServiceException(e);
        }
    }
    
}
