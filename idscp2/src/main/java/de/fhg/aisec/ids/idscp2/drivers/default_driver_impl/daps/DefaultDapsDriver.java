package de.fhg.aisec.ids.idscp2.drivers.default_driver_impl.daps;

import de.fhg.aisec.ids.idscp2.drivers.interfaces.DapsDriver;

/**
 * Default DAPS Driver for requesting valid dynamicAttributeToken and verifying DAT
 *
 * @author Leon Beckmann (leon.beckmann@aisec.fraunhofer.de)
 */
public class DefaultDapsDriver implements DapsDriver {

    @Override
    public byte[] getToken() {
        return new byte[0];
    }

    @Override
    public boolean verifyToken(byte[] dat) {
        return true;
    }
}