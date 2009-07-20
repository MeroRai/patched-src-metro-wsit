/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 1997-2008 Sun Microsystems, Inc. All rights reserved.
 * 
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 * 
 * Contributor(s):
 * 
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package com.sun.xml.ws.rx;

import com.sun.xml.ws.api.SOAPVersion;
import com.sun.xml.ws.api.addressing.AddressingVersion;
import com.sun.xml.ws.rx.mc.MakeConnectionSupportedFeature;
import com.sun.xml.ws.rx.rm.ReliableMessagingFeature;
import com.sun.xml.ws.rx.rm.ReliableMessagingFeature.BackoffAlgorithm;
import com.sun.xml.ws.rx.rm.ReliableMessagingFeature.DeliveryAssurance;
import com.sun.xml.ws.rx.rm.ReliableMessagingFeature.SecurityBinding;
import org.glassfish.gmbal.ManagedObjectManager;

/**
 * TODO split into sperate configurations for WS-MC and WS-RM
 *
 * @author Marek Potociar (marek.potociar at sun.com)
 */
public interface RxConfiguration {
    public static final String ACK_REQUESTED_HEADER_SET = RxConfiguration.class.getName() + ".ACK_REQUESTED_HEADER_SET";
    
    /**
     * @see ReliableMessagingFeature
     */
    public boolean isReliableMessagingEnabled();
    
    /**
     * @see MakeConnectionSupportedFeature
     */
    public boolean isMakeConnectionSupportEnabled();

    /**
     * Provides information about the SOAP protocol version used on the endpoint.
     * 
     * @return the SOAP protocol version used on the RM-enabled endpoint
     */
    public SOAPVersion getSoapVersion();

    /**
     * Provides information about the WS-Addressing protocol version used on the endpoint.
     * 
     * @return the WS-Addressing protocol version used on the RM-enabled endpoint
     */
    public AddressingVersion getAddressingVersion();
    
    /**
     * Provides information if the port, which this configuration belongs to, has 
     * any request/response operations.
     *
     * @return {@code true} in case the port has any request/response operations; {@code false} otherwise
     */
    public boolean requestResponseOperationsDetected();
    
    /**
     * GMBAL/JMX manager
     */
    public ManagedObjectManager getManagedObjectManager();
}