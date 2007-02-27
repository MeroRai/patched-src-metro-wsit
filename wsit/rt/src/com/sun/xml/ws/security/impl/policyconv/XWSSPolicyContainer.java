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



package com.sun.xml.ws.security.impl.policyconv;

import com.sun.xml.wss.impl.PolicyTypeUtil;
import com.sun.xml.wss.impl.policy.MLSPolicy;
import com.sun.xml.wss.impl.policy.SecurityPolicy;
import com.sun.xml.ws.security.policy.MessageLayout;
import com.sun.xml.wss.impl.policy.PolicyGenerationException;
import com.sun.xml.wss.impl.policy.mls.AuthenticationTokenPolicy;
import com.sun.xml.wss.impl.policy.mls.EncryptionPolicy;
import com.sun.xml.wss.impl.policy.mls.MessagePolicy;
import com.sun.xml.wss.impl.policy.mls.SignaturePolicy;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Abhijit.Das@Sun.COM
 */
public class XWSSPolicyContainer {
    private enum Section {
        ClientIncomingPolicy,
        ClientOutgoingPolicy,
        ServerIncomingPolicy,
        ServerOutgoingPolicy
    };
    
    private Section section;
    private List<SecurityPolicy> policyList;
    private List<SecurityPolicy> effectivePolicyList;
    private MessageLayout mode;
    private int foundTimestamp = -1;
    
    private boolean modified = false;
    
    /** Creates a new instance of PolicyConverter */
    public XWSSPolicyContainer(MessageLayout mode, boolean isServer, boolean isIncoming) {
        this.mode = mode;
        setMessageMode(isServer, isIncoming);
        effectivePolicyList = new ArrayList<SecurityPolicy>();
    }
    public XWSSPolicyContainer(boolean isServer,boolean isIncoming) {
        setMessageMode(isServer, isIncoming);
        effectivePolicyList = new ArrayList<SecurityPolicy>();
    }
    public void setMessageMode(boolean isServer, boolean isIncoming) {
        if ( isServer && isIncoming) {
            section = Section.ServerIncomingPolicy;
        } else if ( isServer && !isIncoming) {
            section = Section.ServerOutgoingPolicy;
        } else if ( !isServer && isIncoming) {
            section = Section.ClientIncomingPolicy;
        } else if ( !isServer && !isIncoming) {
            section = Section.ClientOutgoingPolicy;
        }
    }
    public void setPolicyContainerMode(MessageLayout mode){
        this.mode = mode;
    }
    /**
     * Insert into policyList
     *
     *
     */
    public void insert(SecurityPolicy secPolicy ) {
        if(secPolicy == null){
            return;
        }
        if ( policyList == null ) {
            policyList = new ArrayList<SecurityPolicy>();
        }
        if ( isSupportingToken(secPolicy)) {
            switch (section) {
            case ServerOutgoingPolicy:
            case ClientIncomingPolicy:
                return;
            }
        }
        modified = true;
        policyList.add(secPolicy);
    }
    public MessagePolicy getMessagePolicy()throws PolicyGenerationException {
        if ( modified ) {
            convert();
            modified = false;
        }
        MessagePolicy msgPolicy = new MessagePolicy();
        
        msgPolicy.appendAll(effectivePolicyList);
        removeEmptyPrimaryPolicies(msgPolicy);
        return msgPolicy;
        
    }
    private void removeEmptyPrimaryPolicies(MessagePolicy msgPolicy) {
        for ( Object policy : msgPolicy.getPrimaryPolicies() ) {
            if ( policy instanceof SecurityPolicy) {
                SecurityPolicy secPolicy = (SecurityPolicy)policy;
                if ( PolicyTypeUtil.signaturePolicy(secPolicy)) {
                    if (((SignaturePolicy.FeatureBinding)((SignaturePolicy)secPolicy).getFeatureBinding()).getTargetBindings().size() == 0 ) {
                        msgPolicy.remove(secPolicy);
                    }
                } else if ( PolicyTypeUtil.encryptionPolicy(secPolicy)) {
                    if (((EncryptionPolicy.FeatureBinding)((EncryptionPolicy)secPolicy).getFeatureBinding()).getTargetBindings().size() == 0 ) {
                        msgPolicy.remove(secPolicy);
                    }
                }
            }
        }
    }
    
    /**
     * Insert SecurityPolicy after supporting tokens.
     *
     */
    //private void appendAfterToken(SecurityPolicy xwssPolicy , Section section) {
    private void appendAfterToken(SecurityPolicy xwssPolicy) {
        int pos = -1;
        for ( SecurityPolicy secPolicy : effectivePolicyList) {
            if ( isSupportingToken(secPolicy) || isTimestamp(secPolicy)) {
                continue;
            } else {
                pos = effectivePolicyList.indexOf(secPolicy);
                break;
            }
        }
        if ( pos != -1 ) {
            effectivePolicyList.add(pos, xwssPolicy);
        } else {
            effectivePolicyList.add(xwssPolicy);
        }
    }
    /**
     * Insert SecurityPolicy before supporting Tokens.
     *
     */
    private void prependBeforeToken(SecurityPolicy xwssPolicy ) {
        int pos = -1;
        for ( SecurityPolicy secPolicy : effectivePolicyList) {
            if ( !isSupportingToken(secPolicy)) {
                continue;
            } else {
                pos = effectivePolicyList.indexOf(secPolicy);
            }
        }
        if ( pos != -1 ) {
            effectivePolicyList.add(pos, xwssPolicy);
        } else {
            effectivePolicyList.add(xwssPolicy);
        }
    }
    /**
     *
     * Add Security policy.
     */
    private void append(SecurityPolicy xwssPolicy ) {
        effectivePolicyList.add(xwssPolicy);
    }
    /**
     * Add SecurityPolicy.
     *
     */
    private void prepend(SecurityPolicy xwssPolicy) {
        effectivePolicyList.add(0, xwssPolicy);
    }
    /**
     *
     * @return - true if xwssPolicy is SupportingToken policy else false.
     */
    private boolean isSupportingToken( SecurityPolicy xwssPolicy ) {
        if ( xwssPolicy == null ) {
            return false;
        }
        //UsernameToken, SAML Token Policy, X509Certificate
        if ( PolicyTypeUtil.authenticationTokenPolicy(xwssPolicy)) {
            MLSPolicy binding = ((AuthenticationTokenPolicy)xwssPolicy).getFeatureBinding();
            if ( PolicyTypeUtil.usernameTokenPolicy(binding) ||
                    PolicyTypeUtil.samlTokenPolicy(binding) ||
                    PolicyTypeUtil.x509CertificateBinding(binding)) {
                return true;
            }
        }
        return false;
    }
    /**
     *
     * @return - true if xwssPolicy is TimestampPolicy else false.
     */
    private boolean isTimestamp( SecurityPolicy xwssPolicy ) {
        if ( xwssPolicy != null && PolicyTypeUtil.timestampPolicy(xwssPolicy) ) {
            return true;
        }
        return false;
    }
    /**
     *
     * Lax mode
     */
    private void convertLax() {
        for ( SecurityPolicy xwssPolicy : policyList ) {
            if ( isTimestamp(xwssPolicy )) {
                foundTimestamp = policyList.indexOf(xwssPolicy);
                prepend(xwssPolicy);
                continue;
            }
            
            if ( !isSupportingToken(xwssPolicy)) {
                switch(section) {
                case ClientIncomingPolicy:
                    prepend(xwssPolicy);
                    break;
                case ClientOutgoingPolicy:
                    append(xwssPolicy);
                    break;
                case ServerIncomingPolicy:
                    appendAfterToken(xwssPolicy);
                    break;
                case ServerOutgoingPolicy:
                    append(xwssPolicy);
                    break;
                }
            } else if ( isSupportingToken(xwssPolicy) || isTimestamp(xwssPolicy)) {
                prepend(xwssPolicy);
                
             
            }
        }
    }
    /**
     *
     * Strict mode.
     */
    private void convertStrict() {
        for ( SecurityPolicy xwssPolicy : policyList ) {
            if ( isSupportingToken(xwssPolicy)) {
                prepend(xwssPolicy);
       
            } else if ( isTimestamp(xwssPolicy)) {
                prepend(xwssPolicy);
            } else {
                switch (section ) {
                case ClientIncomingPolicy:
                    appendAfterToken(xwssPolicy);
                    break;
                case ClientOutgoingPolicy:
                    append(xwssPolicy);
                    break;
                case ServerIncomingPolicy:
                    appendAfterToken(xwssPolicy);
                    break;
                case ServerOutgoingPolicy:
                    append(xwssPolicy);
                    break;
                }
            }
        }
    }
    /**
     * LaxTsFirst mode.
     *
     */
    private void convertLaxTsFirst() {
        convertLax();
        if ( foundTimestamp != -1 ) {
            switch (section ) {
            case ClientOutgoingPolicy:
                effectivePolicyList.add(0, effectivePolicyList.remove(foundTimestamp));
                break;
            case ServerOutgoingPolicy:
                effectivePolicyList.add(0, effectivePolicyList.remove(foundTimestamp));
                break;
            }
        }
        
    }
    /**
     * LaxTsLast mode.
     *
     */
    private void convertLaxTsLast() {
        convertLax();
        if ( foundTimestamp != -1 ) {
            switch (section) {
            case ClientOutgoingPolicy:
                effectivePolicyList.add(effectivePolicyList.size() -1, effectivePolicyList.remove(foundTimestamp));
                break;
            case ServerOutgoingPolicy:
                effectivePolicyList.add(effectivePolicyList.size() -1, effectivePolicyList.remove(foundTimestamp));
                break;
            }
        }
    }
    /**
     *
     * Convert WS-Security Policy to XWSS policy.
     */
    public void convert() {
        if ( MessageLayout.Lax == mode ) {
            convertLax();
        } else if ( MessageLayout.Strict == mode ) {
            convertStrict();
        } else if ( MessageLayout.LaxTsFirst == mode ) {
            convertLaxTsFirst();
        } else if ( MessageLayout.LaxTsLast == mode ) {
            convertLaxTsLast();
        }
    }
}

