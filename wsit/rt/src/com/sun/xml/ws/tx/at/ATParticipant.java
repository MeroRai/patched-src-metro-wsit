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
package com.sun.xml.ws.tx.at;

import com.sun.xml.ws.api.SOAPVersion;
import com.sun.xml.ws.api.addressing.AddressingVersion;
import com.sun.xml.ws.api.addressing.OneWayFeature;
import com.sun.xml.ws.api.addressing.WSEndpointReference;
import com.sun.xml.ws.api.message.Packet;
import com.sun.xml.ws.api.tx.Participant;
import com.sun.xml.ws.api.tx.Protocol;
import com.sun.xml.ws.api.tx.TXException;
import com.sun.xml.ws.developer.MemberSubmissionEndpointReference;
import com.sun.xml.ws.developer.StatefulWebServiceManager;
import static com.sun.xml.ws.tx.common.Constants.*;
import com.sun.xml.ws.tx.common.StatefulWebserviceFactory;
import com.sun.xml.ws.tx.common.StatefulWebserviceFactoryFactory;
import com.sun.xml.ws.tx.common.TxLogger;
import com.sun.xml.ws.tx.common.Util;
import com.sun.xml.ws.tx.coordinator.Coordinator;
import com.sun.xml.ws.tx.coordinator.Registrant;
import com.sun.xml.ws.tx.webservice.member.at.CoordinatorPortType;
import com.sun.xml.ws.tx.webservice.member.at.ParticipantPortType;
import com.sun.xml.ws.tx.webservice.member.coord.RegisterType;

import javax.transaction.xa.Xid;
import javax.xml.namespace.QName;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPFault;
import javax.xml.ws.EndpointReference;
import javax.xml.ws.WebServiceException;
import java.net.URI;
import java.util.logging.Level;

/**
 * This class encapsulates a WS-AT participant.
 * <p/>
 * <p> A participant represents one of the three ws-at protocols:
 * completion, volatile 2PC or durable 2PC.
 * <p/>
 * <p> Participant lifecycle consist of generating a endpoint reference
 * <p/>
 * <p/>
 * Transaction timeout from Participants perspective.
 * Coordination Context expires  specifies the period, measured from
 * the point in time at which the context was first created or received, after which a
 * transaction MAY be terminated solely due to its length of operation.
 * A 2PC participant MAY elect to abort its work in the transaction so long as it has not
 * already decided to prepare.
 *
 * @author Ryan.Shoemaker@Sun.COM
 * @version $Revision: 1.3.2.1 $
 * @since 1.0
 */
public class ATParticipant extends Registrant {

    static public enum STATE {
        NONE, ACTIVE, PREPARING, PREPARED, PREPARED_SUCCESS, COMMITTING, ABORTING, COMMITTED, ABORTED, READONLY
    }

    /* PPS */
    // TODO: workaround until jaxws-ri stateful webservice can compute this URI 
    private static final URI LOCAL_PPS_URI =
            Util.createURI(WSTX_WS_SCHEME, null, WSTX_WS_PORT, WSTX_WS_CONTEXT + "/wsat/2pc");

    protected STATE state = STATE.NONE;
    protected Xid xid;

    // Equivalent to an XAResource for WSAT
    private Participant participant = null;
    private boolean remoteParticipant;

    static private TxLogger logger = TxLogger.getATLogger(ATParticipant.class);

    static private boolean fallbackSet = false;
    static final String WSAT_COORDINATOR = "WSATCoordinator";
    private StatefulWebServiceManager swsMgr = null;

    private EndpointReference exportCoordinatorProtocolServiceForATParticipant(Coordinator c) {
        StatefulWebserviceFactory swf = StatefulWebserviceFactoryFactory.getInstance();
        return swf.createService(WSAT_COORDINATOR, "Coordinator",
                ATCoordinator.localCoordinationProtocolServiceURI, AddressingVersion.MEMBER,
                c.getIdValue(), this.getIdValue());
    }

    /**
     * Register will figure out if participant will register with local or remote Coordination Protocol Service.
     */
    public ATParticipant(Coordinator parent, Participant participant) {
        super(parent, participant.getProtocol());
        this.participant = participant;
        this.remoteParticipant = false;

        if (logger.isLogging(Level.FINEST)) {
            logger.finest("ATParticipant", getCoordIdPartId());
        }
        // TODO: implement participant timeout from parent.getExpires().
    }

    /**
     * Remote ATParticipant with a local Coordinator.
     * ParticipantProtocolService received as part of registerRequest.
     */
    public ATParticipant(Coordinator parent, RegisterType registerRequest) {
        super(parent, registerRequest);
        if (logger.isLogging(Level.FINEST)) {
            logger.finest("Remote ATParticipant", getCoordIdPartId());
        }
        participant = null;
        setCoordinatorProtocolService(exportCoordinatorProtocolServiceForATParticipant(parent));
        remoteParticipant = true;
        if (logger.isLogging(Level.FINEST)) {
            logger.finest("Remote ATParticipant:", getCoordIdPartId() + " CPS:" + getCoordinatorProtocolService());
        }
    }


    public ParticipantPortType getParticipantPort(EndpointReference epr) {
        return ATCoordinator.getWSATCoordinatorService().getParticipant(epr);
    }

    public boolean isVolatile() {
        return getProtocol() == Protocol.VOLATILE;
    }


    public boolean isDurable() {
        return getProtocol() == Protocol.DURABLE;
    }

    /**
     * A participant is forgotten after it has sent committed or aborted to coordinator.
     */
    public void forget() {
        if (swsMgr != null && this.localParticipantProtocolService != null) {
            swsMgr.unexport(localParticipantProtocolService);
            localParticipantProtocolService = null;
            swsMgr = null;
        }
        getATCoordinator().forget(this);
    }

    public CoordinatorPortType getATCoordinatorWS(boolean nonterminalNotification) {
        if (getCoordinatorProtocolService() == null && !isRegistrationCompleted()) {
            if (logger.isLogging(Level.WARNING)) {
                logger.warning("getATCoordinatorWS", "no register response received from  " +
                        getATCoordinator().getContext().getRootRegistrationService().toString() +
                        " for " + getCoordIdPartId());
            }
        }

        return getATCoordinatorWS(getCoordinatorProtocolService(),
                getParticipantProtocolService(),
                nonterminalNotification);
    }

    public static CoordinatorPortType getATCoordinatorWS(EndpointReference toCPS, EndpointReference replyToPPS,
                                                         boolean nonterminalNotification) {
        OneWayFeature owf = new OneWayFeature();
        WSEndpointReference wsepr = null;
        if (nonterminalNotification) {
            if (replyToPPS != null) {
                try {
                    wsepr = new WSEndpointReference(replyToPPS);
                } catch (Exception xse) {
                    if (logger.isLogging(Level.SEVERE)) {
                        logger.severe("getATCoordinatorWS", "unexpected exception converting replyToPPS " + replyToPPS.toString() +
                                " exception" + xse.getLocalizedMessage());
                    }
                }
                if (wsepr != null) {
                    owf.setReplyTo(wsepr);
                }
            } else {
                if (logger.isLogging(Level.WARNING)) {
                    logger.warning("getATCoordinatorWS", "Protocol Provider Service EPR should not be null for non-terminal notfication");
                }
            }
        }
        assert toCPS != null;
        return ATCoordinator.getWSATCoordinatorService().getCoordinator(toCPS, owf);
    }

    public ParticipantPortType getATParticipantWS(boolean nonterminalNotification) {
        return this.getATParticipantWS(this.getParticipantProtocolService(),
                this.getCoordinatorProtocolService(), nonterminalNotification);
    }


    public static ParticipantPortType getATParticipantWS(EndpointReference toPPS, EndpointReference replyToCPS,
                                                         boolean nonterminalNotification) {
        OneWayFeature owf = new OneWayFeature();
        WSEndpointReference wsepr = null;
        if (nonterminalNotification) {
            if (replyToCPS != null) {
                try {
                    wsepr = new WSEndpointReference(replyToCPS);
                } catch (Exception xse) {
                    if (logger.isLogging(Level.SEVERE)) {
                        logger.severe("getATCoordinatorWS", "unexpected exception converting replyToCPS " + replyToCPS.toString() +
                                " exception" + xse.getLocalizedMessage());
                    }
                }
                if (wsepr != null) {
                    owf.setReplyTo(wsepr);
                } else {
                    if (logger.isLogging(Level.WARNING)) {
                        logger.warning("getATParticipantWS", "Coordinator Provider Service EPR should not be null for non-terminal notfication");
                    }
                }
            }
        }
        assert toPPS != null;
        return ATCoordinator.getWSATCoordinatorService().getParticipant(toPPS, owf);
    }

    public ATCoordinator getATCoordinator() {
        return (ATCoordinator) getCoordinator();
    }


    /**
     * Return participant's state for Atomic Transaction 2PC Protocol.
     */
    public ATParticipant.STATE getState() {
        return state;
    }

    protected Xid getXid() {
        return xid;
    }

    /**
     * Returns participant state. or (something for abort).
     */
    public void prepare() throws TXException {
        if (logger.isLogging(Level.FINER)) {
            logger.entering("prepare", "coordId=" + getCoordinator().getIdValue() + " partId=" + getIdValue());
        }
        switch (getState()) {
            case NONE:
            case ABORTING:
                abort();
                throw new TXException("Rollback");

            case ACTIVE:
                internalPrepare();
                break;

            case PREPARED_SUCCESS:
                // just resend
                if (isRemoteCPS()) {
                    try {
                        getATCoordinatorWS(true).preparedOperation(null);
                    } catch (WebServiceException wse) {
                        if (logger.isLogging(Level.WARNING)) {
                            logger.warning("prepare", "prepared to web service failed. "
                                    + wse.getLocalizedMessage());
                        }
                        throw wse;
                    } catch (Exception e) {
                        if (logger.isLogging(Level.SEVERE)) {
                            logger.severe("prepare", "prepared to web service failed. "
                                    + e.getLocalizedMessage());
                        }
                    }
                } else {
                    getATCoordinator().prepared(getIdValue());
                }
                break;
            case PREPARING:
            case PREPARED:
            case COMMITTING:
                // ignore PREPARE in these states
                break;
        }
        if (logger.isLogging(Level.FINER)) {
            logger.exiting("prepare", "coordId=" + getCoordinator().getIdValue() + " partId=" + getIdValue());
        }
    }

    private void internalPrepare() throws TXException {
        if (remoteParticipant) {
            if (participant != null) {
                if (logger.isLogging(Level.WARNING)) {
                    logger.warning("remotePrepare", "detected non-null participant that will not be prepared locally");
                }
            }
            remotePrepare();
        } else {
            localPrepare();
        }
    }

    private void remotePrepare() {
        state = STATE.PREPARING;
        // TODO: resend if don't receive prepared notfication from coordinator in some communication timeout amount of time
        if (logger.isLogging(Level.FINER)) {
            logger.entering("remotePrepare", getCoordIdPartId());
        }
        try {
            getATParticipantWS(true).prepareOperation(null);
        } catch (WebServiceException wse) {
            if (logger.isLogging(Level.WARNING)) {
                logger.warning("remotePrepare", "prepared to web service failed. "
                        + wse.getLocalizedMessage());
            }
            throw wse;
        } catch (Exception e) {
            if (logger.isLogging(Level.SEVERE)) {
                logger.severe("remotePrepare", "prepared to web service failed. "
                        + e.getLocalizedMessage());
            }
        }

        if (logger.isLogging(Level.FINER)) {
            logger.exiting("remotePrepare", getCoordIdPartId());
        }
    }

    private void localPrepare() throws TXException {
        if (logger.isLogging(Level.FINER)) {
            logger.entering("localPrepare", getCoordIdPartId());
        }
        Participant.STATE result = null;
        state = STATE.PREPARING;
        try {
            result = participant.prepare();
        } catch (TXException e) {
            // failure during prepare, just abort

            // set participant to null. don't want to call its abort(), it already knows its aborted
            participant = null;
            abort();
            throw new TXException("Rollback");
        } catch (Exception e) {
            participant = null;
            abort();
            throw new TXException("Rollback");
        }
        switch (result) {
            case P_OK:
                state = STATE.PREPARED;
                if (isRemoteCPS()) {
                    if (logger.isLogging(Level.FINEST)) {
                        logger.finest("localPrepare", "send prepared to remote coordinator"
                                + getIdValue());
                    }
                    try {
                        getATCoordinatorWS(true).preparedOperation(null);
                    } catch (WebServiceException wse) {
                        if (logger.isLogging(Level.WARNING)) {
                            logger.warning("localPrepare", "prepared to web service failed. "
                                    + wse.getLocalizedMessage());
                        }
                        throw wse;
                    }
                } else {
                    if (logger.isLogging(Level.FINEST)) {
                        logger.finest("localPrepare", "send prepared to local coordinator"
                                + getIdValue());
                    }
                    getATCoordinator().prepared(this.getIdValue());
                }
                state = STATE.PREPARED_SUCCESS;
                break;

            case P_READONLY:
                state = STATE.READONLY;
                if (isRemoteCPS()) {
                    if (logger.isLogging(Level.FINEST)) {
                        logger.finest("localPrepare", "send readonly to remote coordinator for participant id"
                                + getIdValue());
                    }
                    try {
                        getATCoordinatorWS(false).readOnlyOperation(null);
                    } catch (WebServiceException wse) {
                        if (logger.isLogging(Level.WARNING)) {
                            logger.warning("localPrepare", "readonly to web service failed. "
                                    + wse.getLocalizedMessage());
                        }
                        throw wse;
                    }
                } else {
                    if (logger.isLogging(Level.FINEST)) {
                        logger.finest("localPrepare", "send readonly to remote coordinator for participant id" +
                                getIdValue());
                    }
                    getATCoordinator().readonly(getIdValue());
                }
                if (logger.isLogging(Level.FINE)) {
                    logger.fine("prepare", "readonly " + getCoordIdPartId());
                }
                forget();
                break;
        }
        if (logger.isLogging(Level.FINER)) {
            logger.exiting("ATParticipant.localPrepare");
        }
    }

    /**
     * Send Terminal notification
     */
    private void remoteCommit() {
        // TODO: resend if don't receive committed notification from coordinator in some communication timeout amount of time

        if (logger.isLogging(Level.FINER)) {
            logger.entering("remoteCommit()", getIdValue());
        }
        this.getATParticipantWS(true).commitOperation(null);
        if (logger.isLogging(Level.FINER)) {
            logger.exiting("remoteCommit");
        }
    }

    public void commit() throws TXException {
        if (logger.isLogging(Level.FINER)) {
            logger.entering("commit" + getCoordIdPartId());
        }
        if (remoteParticipant) {
            remoteCommit();
        } else {
            localCommit();
        }
        if (logger.isLogging(Level.FINER)) {
            logger.entering("commit" + getCoordIdPartId());
        }
    }

    private void localCommit() {
        switch (getState()) {
            case NONE:

                // TODO send committed using wsa:replyTo EPR.
                // this case
                break;
            case ABORTING:
                if (logger.isLogging(Level.WARNING)) {
                    logger.warning("localCommit", "fault inconsistent internal state: " + getState() +
                            " for " + getCoordIdPartId());
                }
                //fault wsat:InconsistentInternalState
                abort();

                break;
            case ACTIVE:
            case PREPARING:
            case PREPARED:
                if (logger.isLogging(Level.WARNING)) {
                    logger.warning("localCommit", "fault invalid state: " + getState() +
                            " for " + getCoordIdPartId());
                }
                // TODO throw fault coor:InvalidState
                abort();
                break;

            case PREPARED_SUCCESS:
                state = STATE.COMMITTING;
                participant.commit();
                participant = null;   // no longer need to contact participant.
                if (logger.isLogging(Level.INFO)) {
                    logger.info("localCommit", "committed " + getCoordIdPartId());
                }
                if (isRemoteCPS()) {
                    try {
                        getATCoordinatorWS(false).committedOperation(null);
                    } catch (WebServiceException wse) {
                        if (logger.isLogging(Level.WARNING)) {
                            logger.warning("localCommit", "committed to web service failed. "
                                    + wse.getLocalizedMessage());
                        }
                        throw wse;
                    }
                } else {
                    getATCoordinator().committed(getIdValue());
                }
                forget();
                break;

            case COMMITTING:
                if (isRemoteCPS()) {
                    getATCoordinatorWS(false).committedOperation(null);
                } else {
                    getATCoordinator().committed(getIdValue());
                }
                forget();

                break;
        }
    }

    public void abort() {
        if (logger.isLogging(Level.FINER)) {
            logger.entering("abort", getCoordIdPartId());
        }

        //TODO. put switch statement over all possible 2pc transaction state.
        //      invalid states require fault to be sent
        state = STATE.ABORTING;

        // local rollback
        if (participant != null) {
            participant.abort();
            participant = null;   // no need to contact participant anymore
        }
        // pass rollback to remote participant
        if (remoteParticipant) {
            remoteRollback();
        }
        if (isRemoteCPS()) {
            try {
                getATCoordinatorWS(false).abortedOperation(null);
            } catch (WebServiceException wse) {
                if (logger.isLogging(Level.WARNING)) {
                    logger.warning("localPrepare", "prepared to web service failed. "
                            + wse.getLocalizedMessage());
                }
                throw wse;
            }
        } else {
            getATCoordinator().aborted(getIdValue());
        }

        // try {
        if (logger.isLogging(Level.FINE)) {
            logger.fine("abort", getCoordIdPartId());
        }
        forget();
        // } catch (XAException ex) {
        //    ex.printStackTrace();
        //}

        if (logger.isLogging(Level.FINER)) {
            logger.exiting("abort", getCoordIdPartId());
        }
    }

    /**
     * Send terminal notification
     */
    private void remoteRollback() {
        // TODO: resend if don't receive aborted notification from coordinator in some communication timeout amount of time

        if (logger.isLogging(Level.FINER)) {
            logger.entering("remoteRollack", getCoordIdPartId());
        }
        getATParticipantWS(true).rollbackOperation(null);
        if (logger.isLogging(Level.FINER)) {
            logger.exiting("remoteRollback", getCoordIdPartId());
        }
    }

    public void setCoordinatorProtocolService(EndpointReference cps) {
        super.setCoordinatorProtocolService(cps);

        if (cps != null) {
            // wscoor:registerResponse successfully communicated CPS, change participant's state
            state = STATE.ACTIVE;
        }
    }

    void prepared() {
        // TODO:  given current state, check if it is valid to set to this state.
        state = STATE.PREPARED_SUCCESS;
    }

    void committed() {
        // TODO: verify state transition does not need to throw invalid state fault.
        state = STATE.COMMITTING;
    }

    void readonly() {
        // TODO: verify state transition does not need to throw invalid state fault.
        state = STATE.READONLY;
    }

    void aborted() {
        // TODO: verify state transition does not need to throw invalid state fault.
        state = STATE.ABORTED;
    }

    /**
     * This fault is sent by a participant to indicate that it cannot fulfill its obligations.
     * This indicates a global consistency failure and is an unrecoverable condition.
     *
     * @param soapVersion SOAP verion for returned fault.
     */
    private Packet newInconsistentInternalStateFault(SOAPVersion soapVersion, String detail) {
        Packet faultResponsePacket = null;
        // wsa:Action Constants.WSAT_FAULT_ACTION_URI
        // [Code] Sender
        // [Subcode] wsat:InconsistentInternalState
        // [Reason] A global consistency failure has occurred. This is an unrecoverable condition.
        // [Detail] detail
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * @see com.sun.xml.ws.tx.common.WsaHelper
     * @deprecated since now
     */
    SOAPFault createSOAPFault(String message) {
        try {
            SOAPFault fault = SOAPVersion.SOAP_11.saajSoapFactory.createFault();
            fault.setFaultString(message);
            // TODO: fix deprecated constant reference
            // fault.setFaultCode(JAXWSAConstants.SOAP11_SENDER_QNAME);
            fault.appendFaultSubcode(new QName(WSAT_SOAP_NSURI, "InconsistentInternalState"));
            fault.setFaultRole("A global consistent failure has occurred. This is an unrecoverable condition.");
            return fault;
        } catch (SOAPException ex) {
            throw new WebServiceException(ex);
        }
    }

    protected String getCoordIdPartId() {
        return " coordId=" + getCoordinator().getIdValue() + " partId=" + getIdValue() + " ";
    }

    private EndpointReference localParticipantProtocolService = null;

    /**
     * No need to export an external stateful web service for this usage case.
     */
    public EndpointReference getLocalParticipantProtocolService() {
        if (localParticipantProtocolService == null) {
            if (isRemoteCPS()) {
                StatefulWebserviceFactory swf = StatefulWebserviceFactoryFactory.getInstance();
                localParticipantProtocolService =
                        swf.createService(WSAT_COORDINATOR, "Participant",
                                LOCAL_PPS_URI, AddressingVersion.MEMBER,
                                getATCoordinator().getIdValue(), this.getId().getValue());
                swsMgr = swf.getManager(WSAT_COORDINATOR, "Participant");
            } else {
                MemberSubmissionEndpointReference epr = new MemberSubmissionEndpointReference();
                epr.addr = new MemberSubmissionEndpointReference.Address();
                epr.addr.uri = LOCAL_PPS_URI.toString();
                localParticipantProtocolService = epr;
            }
        }
        return localParticipantProtocolService;
    }
}
