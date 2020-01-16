package de.fhg.aisec.ids.idscp2.idscp_core.finite_state_machine;

import com.google.protobuf.InvalidProtocolBufferException;
import de.fhg.aisec.ids.idscp2.drivers.interfaces.*;
import de.fhg.aisec.ids.idscp2.error.IDSCPv2Exception;
import de.fhg.aisec.ids.idscp2.idscp_core.IdscpMessageFactory;
import de.fhg.aisec.ids.idscp2.idscp_core.IdscpMsgListener;
import de.fhg.aisec.ids.idscp2.idscp_core.secure_channel.SecureChannel;
import de.fhg.aisec.ids.messages.IDSCPv2.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;

public class FSM implements FsmListener{
    private static final Logger LOG = LoggerFactory.getLogger(FSM.class);

    /*  -----------   IDSCPv2 Protocol States   ---------- */
    private final State STATE_CLOSED = new State();
    private final State STATE_WAIT_FOR_HELLO = new State();
    private final State STATE_WAIT_FOR_RAT= new State();
    private final State STATE_WAIT_FOR_RAT_CLIENT = new State();
    private final State STATE_WAIT_FOR_RAT_SERVER = new State();
    private final State STATE_WAIT_FOR_DAT_AND_RAT_CLIENT = new State();
    private final State STATE_WAIT_FOR_DAT_AND_RAT_SERVER = new State();
    private final State STATE_ESTABLISHED = new State();
    /*  ----------------   end of states   --------------- */

    //toDo error codes

    private State currentState;
    private final State initialState = STATE_CLOSED;
    private SecureChannel secureChannel;
    private RatProverDriver ratProverDriver;
    private RatVerifierDriver ratVerifierDriver;
    private DapsDriver dapsDriver;
    private IdscpMsgListener listener = null;
    private final CountDownLatch listenerLatch = new CountDownLatch(1);
    private IdscpMessage cachedIdscpHello = null;
    private final Object idscpHandshakeLock = new Object();
    private final Object fsmIsBusy = new Object();
    //toDo cipher suites
    private String[] ratSupportedSuite = new String[] {"TPM_2, TRUST_ZONE, SGX"};
    private String[] ratExpectedSuite = new String[] {"TPM_2, TRUST_ZONE, SGX"};
    private byte[] remoteDat = null;
    private RatProverInstance ratProver = null;
    private RatVerifierInstance ratVerifier = null;

    public FSM(SecureChannel secureChannel, RatProverDriver ratProver, RatVerifierDriver ratVerifier,
               DapsDriver dapsDriver){

        this.secureChannel = secureChannel;
        this.ratProverDriver = ratProver;
        this.ratVerifierDriver = ratVerifier;
        this.dapsDriver = dapsDriver;
        secureChannel.setFsm(this);

        /*  -----------   Protocol Transitions   ---------- */

        /*---------------------------------------------------
        * STATE_CLOSED - Transition Description
        * ---------------------------------------------------
        * INTERNAL_CONTROL_MESSAGE.START_IDSCP_HANDSHAKE ---> send IDSCP_HELLO ---> STATE_WAIT_FOR_HELLO
        * INTERNAL_CONTROL_MESSAGE.IDSCP_STOP ---> send IDSCP_CLOSE ---> STATE_CLOSED
        * IDSCP_MESSAGE.IDSCP_HELLO ---> cache IDSCP_HELLO ---> STATE_CLOSED
        * ALL_OTHER_MESSAGES ---> STATE_CLOSED
        * --------------------------------------------------- */
        STATE_CLOSED.addTransition(InternalControlMessage.START_IDSCP_HANDSHAKE.getValue(), new Transition(
                event -> {
                    LOG.debug("Get DAT Token vom DAT_DRIVER");
                    byte[] dat = this.dapsDriver.getToken();
                    LOG.debug("Send IDSCP_HELLO");
                    IdscpMessage idscpHello = IdscpMessageFactory.
                            getIdscpHelloMessage(dat, this.ratSupportedSuite, this.ratExpectedSuite);
                    this.send(idscpHello);
                    //toDo set handshake timeout
                    LOG.debug("Switch to state STATE_WAIT_FOR_HELLO");
                    return STATE_WAIT_FOR_HELLO;
                }
        ));

        STATE_CLOSED.addTransition(InternalControlMessage.IDSCP_STOP.getValue(), new Transition(
                event -> {
                    LOG.debug("Send IDSCP_CLOSE");
                    IdscpMessage idscpClose = IdscpMessageFactory.getIdscpCloseMessage();
                    send(idscpClose);
                    LOG.debug("Stay in state STATE_CLOSED");
                    notifyHandshakeCompleteLock();
                    return STATE_CLOSED;
                }
        ));

        STATE_CLOSED.addTransition(IdscpMessage.IDSCPHELLO_FIELD_NUMBER, new Transition(
                event -> {
                    LOG.debug("Cache IDSCP_HELLO");
                    this.cachedIdscpHello = event.getIdscpMessage();
                    LOG.debug("Stay in state STATE_CLOSED");
                    return STATE_CLOSED;
                }
        ));

        STATE_CLOSED.setNoTransitionHandler(
                event -> {
                    LOG.debug("No transition available for given event " + event.toString());
                    LOG.debug("Stay in state STATE_CLOSED");
                    notifyHandshakeCompleteLock();
                    return STATE_CLOSED;
                }
            );

        /*---------------------------------------------------
         * STATE_WAIT_FOR_HELLO - Transition Description
         * ---------------------------------------------------
         * INTERNAL_CONTROL_MESSAGE.ERROR ---> send IDSCP_CLOSE ---> STATE_CLOSED
         * INTERNAL_CONTROL_MESSAGE.IDSCP_STOP ---> send IDSCP_CLOSE ---> STATE_CLOSED
         * IDSCP_CLOSE ---> STATE_CLOSED
         * INTERNAL_CONTROL_MESSAGE.TIMEOUT ---> send IDSCP_ERROR, send IDSCP_CLOSE ---> STATE_CLOSED
         * IDSCP_HELLO ---> RAT no match: send IDSCP_ERROR, send IDSCP_CLOSE ---> STATE_CLOSED
         * IDSCP_HELLO ---> DAT Verification failed: send IDSCP_ERROR, send IDSCP_CLOSE ---> STATE_CLOSED
         * IDSCP_HELLO ---> verify DAT, verifyRatCipher, set DAT Timeout, start RAT P&V ---> STATE_WAIT_FOR_RAT
         * ALL_OTHER_MESSAGES ---> STATE_WAIT_FOR_HELLO
         * --------------------------------------------------- */
        STATE_WAIT_FOR_HELLO.addTransition(InternalControlMessage.ERROR.getValue(), new Transition(
                event -> {
                    LOG.debug("An internal control error occurred, send IDSC_CLOSE");
                    send(IdscpMessageFactory.getIdscpCloseMessage());
                    notifyHandshakeCompleteLock();
                    LOG.debug("Switch to state STATE_CLOSED");
                    return STATE_CLOSED;
                }
        ));

        STATE_WAIT_FOR_HELLO.addTransition(InternalControlMessage.IDSCP_STOP.getValue(), new Transition(
                event -> {
                    LOG.debug("Send IDSC_CLOSE");
                    send(IdscpMessageFactory.getIdscpCloseMessage());
                    notifyHandshakeCompleteLock();
                    LOG.debug("Switch to state STATE_CLOSED");
                    return STATE_CLOSED;
                }
        ));

        STATE_WAIT_FOR_HELLO.addTransition(IdscpMessage.IDSCPCLOSE_FIELD_NUMBER, new Transition(
                event -> {
                   LOG.debug("Received IDSCP_CLOSE");
                   notifyHandshakeCompleteLock();
                   LOG.debug("Switch to state STATE_CLOSED");
                   return STATE_CLOSED;
                }
        ));

        STATE_WAIT_FOR_HELLO.addTransition(InternalControlMessage.TIMEOUT.getValue(), new Transition(
                event -> {
                    LOG.debug("STATE_WAIT_FOR_HELLO timeout. Send IDSCP_ERROR and IDSCP_CLOSE");
                    send(IdscpMessageFactory.getIdscpErrorMessage("Timeout", ""));
                    send(IdscpMessageFactory.getIdscpCloseMessage());
                    notifyHandshakeCompleteLock();
                    LOG.debug("Switch to state STATE_CLOSED");
                    return STATE_CLOSED;
                }
        ));

        STATE_WAIT_FOR_HELLO.addTransition(IdscpMessage.IDSCPHELLO_FIELD_NUMBER, new Transition(
                event -> {
                    LOG.debug("Received IDSCP_HELLO");
                    IdscpHello idscpHello = event.getIdscpMessage().getIdscpHello();
                    LOG.debug("Calculate Rat mechanisms");
                    if (!calculateRatMechanisms(null, null) //toDo rat ciphers
                    ){
                        LOG.debug("Cannot find a match for RAT cipher. Send IDSCP_ERROR and IDSCP_CLOSE");
                        send(IdscpMessageFactory.getIdscpErrorMessage("No match for RAT mechanism", ""));
                        send(IdscpMessageFactory.getIdscpCloseMessage());
                        notifyHandshakeCompleteLock();
                        LOG.debug("Switch to state STATE_CLOSED");
                        return STATE_CLOSED;
                    }
                    LOG.debug("Verify received DAT");
                    //check if Dat is available and verify dat
                    if (!idscpHello.hasDynamicAttributeToken() || !this.dapsDriver
                            .verifyToken(idscpHello.getDynamicAttributeToken().getToken().toByteArray())){
                        LOG.debug("No valid remote DAT is available. Send IDSCP_ERROR and IDSCP_CLOSE");
                        send(IdscpMessageFactory.getIdscpErrorMessage("No valid DAT", ""));
                        send(IdscpMessageFactory.getIdscpCloseMessage());
                        notifyHandshakeCompleteLock();
                        LOG.debug("Switch to state STATE_CLOSED");
                        return STATE_CLOSED;
                    }
                    LOG.debug("Remote DAT is valid");
                    //toDo set DAT timeout
                    LOG.debug("Start RAT Prover and Verifier");
                    //toDo which RAT mechanism is chosen
                    this.ratVerifier = ratVerifierDriver.start(this);
                    this.ratProver = ratProverDriver.start(this);
                    LOG.debug("Switch to state STATE_ESTABLISHED");
                    notifyHandshakeCompleteLock();
                    return STATE_ESTABLISHED;
                }
        ));

        STATE_WAIT_FOR_HELLO.setNoTransitionHandler(
                event -> {
                    LOG.debug("No transition available for given event " + event.toString());
                    LOG.debug("Stay in state STATE_WAIT_FOR_HELLO");
                    return STATE_WAIT_FOR_HELLO;
                }
        );

        /*---------------------------------------------------
         * STATE_WAIT_FOR_RAT - Transition Description
         * ---------------------------------------------------
         * INTERNAL_CONTROL_MESSAGE.ERROR ---> send IDSCP_ERROR, send IDSCP_CLOSE ---> STATE_CLOSED
         * INTERNAL_CONTROL_MESSAGE.IDSCP_STOP ---> send IDSCP_CLOSE ---> STATE_CLOSED
         * INTERNAL_CONTROL_MESSAGE.DAT_TIMER_EXPIRED ---> send DAT_EXPIRED, set timeout ---> STATE_WAIT_FOR_DAT_AND_RAT_CLIENT
         * //toDo INTERNAL_CONTROL_MESSAGE.SEND_IDSCP_MESSAGE ---> send IDSCP_DATA ---> STATE_ESTABLISHED
         * INTERNAL_CONTROL_MESSAGE.REPEAT_RAT ---> send IDSCP_RE_RAT ---> WAIT_FOR_RAT_CLIENT
         * IDSCP_MESSAGE.RE_RAT ---> start RAT_Prover ---> STATE_WAIT_FOR_RAT_SERVER
         * IDSCP_MESSAGE.DATA ---> listener.onMessage() ---> STATE_ESTABLISHED
         * IDSCP_MESSAGE.CLOSE ---> STATE_CLOSED
         * IDSCP_MESSAGE.DAT_EXPIRED ---> send DAT, start RAT_PROVER ---> STATE_WAIT_FOR_RAT_SERVER
         * --------------------------------------------------- */

        /*---------------------------------------------------
         * STATE_ESTABLISHED - Transition Description
         * ---------------------------------------------------
         * INTERNAL_CONTROL_MESSAGE.ERROR ---> send IDSCP_ERROR, send IDSCP_CLOSE ---> STATE_CLOSED
         * INTERNAL_CONTROL_MESSAGE.IDSCP_STOP ---> send IDSCP_CLOSE ---> STATE_CLOSED
         * INTERNAL_CONTROL_MESSAGE.DAT_TIMER_EXPIRED ---> send DAT_EXPIRED, set timeout ---> STATE_WAIT_FOR_DAT_AND_RAT_CLIENT
         * //toDo INTERNAL_CONTROL_MESSAGE.SEND_IDSCP_MESSAGE ---> send IDSCP_DATA ---> STATE_ESTABLISHED
         * INTERNAL_CONTROL_MESSAGE.REPEAT_RAT ---> send IDSCP_RE_RAT ---> WAIT_FOR_RAT_CLIENT
         * IDSCP_MESSAGE.RE_RAT ---> start RAT_Prover ---> STATE_WAIT_FOR_RAT_SERVER
         * IDSCP_MESSAGE.DATA ---> listener.onMessage() ---> STATE_ESTABLISHED
         * IDSCP_MESSAGE.CLOSE ---> STATE_CLOSED
         * IDSCP_MESSAGE.DAT_EXPIRED ---> send DAT, start RAT_PROVER ---> STATE_WAIT_FOR_RAT_SERVER
         * --------------------------------------------------- */
        STATE_ESTABLISHED.addTransition(InternalControlMessage.ERROR.getValue(), new Transition(
                event -> {
                    LOG.debug("Error occurred, send IDSCP_ERROR and IDSCP_CLOSE and close idscp connection");
                    send(IdscpMessageFactory.getIdscpErrorMessage("Error occurred", ""));
                    send(IdscpMessageFactory.getIdscpCloseMessage());
                    return STATE_CLOSED;
                }
        ));

        STATE_ESTABLISHED.addTransition(InternalControlMessage.IDSCP_STOP.getValue(), new Transition(
                event -> {
                    send(IdscpMessageFactory.getIdscpCloseMessage());
                    LOG.debug("Close idscp connection and send IDSCP_CLOSE");
                    return STATE_CLOSED;
                }
        ));

        STATE_ESTABLISHED.addTransition(InternalControlMessage.DAT_TIMER_EXPIRED.getValue(), new Transition(
                event -> {
                    LOG.debug("Remote DAT expired. Send IDSCP_DAT_EXPIRED");
                    send(IdscpMessageFactory.getIdscpDatExpiredMessage());
                    //toDo set dat timeout
                    LOG.debug("Switch to state STATE_WAIT_FOR_DAT_AND_RAT_CLIENT");
                    return STATE_WAIT_FOR_DAT_AND_RAT_CLIENT;
                }
        ));

        STATE_ESTABLISHED.addTransition(InternalControlMessage.REPEAT_RAT.getValue(), new Transition(
                event -> {
                    LOG.debug("RAT timer expired. Send IDSCP_RE_RAT");
                    send(IdscpMessageFactory.getIdscpReRatMessage("RAT expired"));
                    LOG.debug("Switch to state STATE_WAIT_FOR_RAT_CLIENT");
                    return STATE_WAIT_FOR_RAT_CLIENT;
                }
        ));

        STATE_ESTABLISHED.addTransition(IdscpMessage.IDSCPRERAT_FIELD_NUMBER, new Transition(
                event -> {
                    LOG.debug("Received IDSCP_RERAT with cause {}", event.getIdscpMessage().getIdscpReRat().getCause());
                    LOG.debug("Start RAT_PROVER");
                    //toDo trigger RAT_PROVER
                    LOG.debug("Switch to state STATE_WAIT_FOR_RAT_SERVER");
                    return STATE_WAIT_FOR_RAT_SERVER;
                }
        ));

        STATE_ESTABLISHED.addTransition(IdscpMessage.IDSCPDATEXPIRED_FIELD_NUMBER, new Transition(
                event -> {
                    LOG.debug("DAT expired. Send new DAT and repeat RAT");
                    send(IdscpMessageFactory.getIdscpDatMessage(this.dapsDriver.getToken()));
                    //toDo trigger RAT Prover
                    LOG.debug("Switch to state STATE_WAIT_FOR_RAT_SERVER");
                    return STATE_WAIT_FOR_RAT_SERVER;
                }
        ));

        STATE_ESTABLISHED.addTransition(IdscpMessage.IDSCPDATA_FIELD_NUMBER, new Transition(
                event -> {
                    try {
                        this.listenerLatch.await();
                        this.listener.onMessage(event.getIdscpMessage());
                        LOG.debug("Idscp data was passed to connection listener");
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }

                    LOG.debug("Stay in state STATE_ESTABLISHED");
                    return STATE_ESTABLISHED;
                }
        ));

        STATE_ESTABLISHED.addTransition(IdscpMessage.IDSCPCLOSE_FIELD_NUMBER, new Transition(
                event -> {
                    LOG.debug("Receive IDSCP_CLOSED");
                    try {
                        this.listenerLatch.await();
                        this.listener.onMessage(event.getIdscpMessage());
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                    LOG.debug("Switch to STATE_CLOSED");
                    return STATE_CLOSED;
                }
        ));

        STATE_ESTABLISHED.setNoTransitionHandler(
                event -> {
                    LOG.debug("No transition available for given event " + event.toString());
                    LOG.debug("Stay in state STATE_ESTABLISHED");
                    return STATE_ESTABLISHED;
                }
        );
        /*  ----------------   end of transitions   --------------- */

        //set initial state
        currentState = initialState;
    }

    @Override
    public void onMessage(byte[] data){
        //parse message and create new IDSCP Message Event, then pass it to current state and update new state
        try {
            IdscpMessage message = IdscpMessage.parseFrom(data);
            Event e = new Event(message);
            synchronized (fsmIsBusy){
                currentState = currentState.feedEvent(e);
            }
        } catch (InvalidProtocolBufferException e) {
            LOG.warn("Cannot parse raw data into IdscpMessage {}. Send error message", data);
            //toDo sendError("Cannot parse raw data into IdscpMessage", "");
        }
    }

    @Override
    public void onControlMessage(InternalControlMessage controlMessage) {
        //create Internal Control Message Event and pass it to current state and update new state
        Event e = new Event(controlMessage);
        synchronized (fsmIsBusy){
            currentState = currentState.feedEvent(e);
        }
    }

    public void terminate(){
        LOG.info("Close idscp connection");
        onControlMessage(InternalControlMessage.IDSCP_STOP);
        LOG.debug("Close secure channel");
        secureChannel.close();
    }

    public void startIdscpHandshake() throws IDSCPv2Exception {
        if (currentState.equals(STATE_CLOSED)){
            //trigger handshake init
            onControlMessage(InternalControlMessage.START_IDSCP_HANDSHAKE);

            //check if a idscpHello was already received and trigger next transition
            synchronized (fsmIsBusy){
                if (cachedIdscpHello != null){
                    Event e = new Event(cachedIdscpHello);
                    currentState = currentState.feedEvent(e);
                }
            }

            try {
                //wait until handshake was successful or failed
                synchronized (idscpHandshakeLock) {
                    idscpHandshakeLock.wait();
                }

                if (!isConnected()){
                    //handshake failed, throw exception
                    throw new IDSCPv2Exception("Handshake failed");
                }

            } catch (InterruptedException e) {
                throw new IDSCPv2Exception("Handshake failed because thread was interrupted");
            }
        }
    }

    public void send(IdscpMessage msg){
        secureChannel.send(msg.toByteArray());
    }

    public boolean isConnected(){
        return currentState.equals(STATE_ESTABLISHED);
    }

    public void registerMessageListener(IdscpMsgListener listener){
        this.listener = listener;
        listenerLatch.countDown();
    }

    public void setEndpointConnectionId(String id){
        this.secureChannel.setEndpointConnectionId(id);
    }

    private void notifyHandshakeCompleteLock(){
        synchronized (idscpHandshakeLock){
            idscpHandshakeLock.notify();
        }
    }

    private boolean calculateRatMechanisms(String[] remoteExpectedSuite, String[] remoteSupportedSuite){
        return true;
        //toDo implement logic
    }
}