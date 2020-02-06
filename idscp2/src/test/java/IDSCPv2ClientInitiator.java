import de.fhg.aisec.ids.idscp2.IDSCPv2Initiator;
import de.fhg.aisec.ids.idscp2.drivers.default_driver_impl.daps.DefaultDapsDriver;
import de.fhg.aisec.ids.idscp2.drivers.default_driver_impl.rat.TPM2Prover;
import de.fhg.aisec.ids.idscp2.drivers.default_driver_impl.rat.TPM2Verifier;
import de.fhg.aisec.ids.idscp2.drivers.default_driver_impl.secure_channel.NativeTLSDriver;
import de.fhg.aisec.ids.idscp2.drivers.interfaces.DapsDriver;
import de.fhg.aisec.ids.idscp2.drivers.interfaces.SecureChannelDriver;
import de.fhg.aisec.ids.idscp2.idscp_core.IDSCPv2Connection;
import de.fhg.aisec.ids.idscp2.idscp_core.configuration.IDSCPv2Configuration;
import de.fhg.aisec.ids.idscp2.idscp_core.configuration.IDSCPv2Settings;
import de.fhg.aisec.ids.idscp2.idscp_core.rat_registry.RatProverDriverRegistry;
import de.fhg.aisec.ids.idscp2.idscp_core.rat_registry.RatVerifierDriverRegistry;

import java.util.concurrent.ConcurrentHashMap;

public class IDSCPv2ClientInitiator implements IDSCPv2Initiator {

    private ConcurrentHashMap<String, IDSCPv2Connection> connections = new ConcurrentHashMap<>();

    public void init(IDSCPv2Settings settings){
        SecureChannelDriver secureChannelDriver = new NativeTLSDriver();
        DapsDriver dapsDriver = new DefaultDapsDriver();

        RatProverDriverRegistry.getInstance().registerDriver("TPM_2", TPM2Prover.class);
        RatVerifierDriverRegistry.getInstance().registerDriver("TPM_2", TPM2Verifier.class);

        IDSCPv2Configuration clientConfig = new IDSCPv2Configuration(this,
                dapsDriver, secureChannelDriver);
        clientConfig.connect(settings);
    }


    @Override
    public void newConnectionHandler(IDSCPv2Connection connection) {
        this.connections.put(connection.getConnectionId(), connection);
        System.out.println("User: New connection with id " + connection.getConnectionId());
    }

    @Override
    public void errorHandler(String error) {
        System.out.println("User: Error occurred: " + error);
    }

    @Override
    public void connectionClosedHandler(String connectionId) {
        System.out.println("User: Connection closed");
        connections.remove(connectionId);
    }
}
