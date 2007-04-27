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
package com.sun.xml.ws.policy.spi;

import com.sun.xml.ws.policy.PolicyAssertion;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import javax.xml.namespace.QName;

/**
 * This abstract policy assertion validator validates assertions by their qualified
 * name. Server and client side validation methods return {@link Fitness} based on 
 * following schema:
 *
 * <ul>
 * <li>{@link Fitness#SUPPORTED} - if the assertion qualified name is in the list of
 * supported assertion names on the server/client side</li>
 * <li>{@link Fitness#UNSUPPORTED} - if the assertion qualified name is not in the list of
 * supported assertion names on the server/client side, however it is in the list of 
 * assertion names supported on the other side</li>
 * <li>{@link Fitness#UNKNOWN} - if the assertion qualified name is not present in the any of
 * the lists of supported assertion names</li>
 * </ul>
 *
 * For some domains such validation may be sufficient enough. Other domains may
 * use functionality of this base class as a first step validation before any attempts 
 * to validate content of the assertion. To do this one needs to override and reuse 
 * the default behavior of {@link #validateClientSide(PolicyAssertion)} and 
 * {@link #validateServerSide(PolicyAssertion)} methods.
 *
 * @author Marek Potociar (marek.potociar at sun.com)
 */
public abstract class AbstractQNameValidator implements PolicyAssertionValidator {
    private final Set<String> supportedDomains = new HashSet<String>();
    private final Collection<QName> serverAssertions;
    private final Collection<QName> clientAssertions;
    
    /**
     * Constructor that takes two collections specifying qualified names of assertions 
     * supported on either server or client side. The set of all assertion namespaces 
     * defines list of all domains supported  by the assertion validator 
     * (see {@link PolicyAssertionValidator#declareSupportedDomains}).
     */
    protected AbstractQNameValidator(Collection<QName> serverSideAssertions, Collection<QName> clientSideAssertions) {
        if (serverSideAssertions != null) {
            this.serverAssertions = new HashSet<QName>(serverSideAssertions);
            for (QName assertion : this.serverAssertions) {
                supportedDomains.add(assertion.getNamespaceURI());
            }
        } else {
            this.serverAssertions = new HashSet<QName>(0);
        }
        
        if (clientSideAssertions != null) {
            this.clientAssertions = new HashSet<QName>(clientSideAssertions);
            for (QName assertion : this.clientAssertions) {
                supportedDomains.add(assertion.getNamespaceURI());
            }
        } else {
            this.clientAssertions = new HashSet<QName>(0);
        }        
    }        
        
    public String[] declareSupportedDomains() {
        return supportedDomains.toArray(new String[supportedDomains.size()]);
    }
    
    public Fitness validateClientSide(PolicyAssertion assertion) {
        return validateAssertion(assertion, clientAssertions, serverAssertions);
    }
    
    public Fitness validateServerSide(PolicyAssertion assertion) {
        return validateAssertion(assertion, serverAssertions, clientAssertions);
    }
     
    private Fitness validateAssertion(PolicyAssertion assertion, Collection<QName> thisSideAssertions, Collection<QName> otherSideAssertions) {
        QName assertionName = assertion.getName();
        if (thisSideAssertions.contains(assertionName)) {
            return Fitness.SUPPORTED;
        } else if (otherSideAssertions.contains(assertionName)) {
            return Fitness.UNSUPPORTED;
        } else {
            return Fitness.UNKNOWN;                    
        }        
    }
}
