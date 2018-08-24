/*-
 * ========================LICENSE_START=================================
 * camel-ids
 * %%
 * Copyright (C) 2018 Fraunhofer AISEC
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package de.fhg.camel.ids.server;

import de.fhg.ids.comm.CertificatePair;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;

/** Default websocket factory. Used when no custom websocket is needed. */
public class DefaultWebsocketFactory implements WebSocketFactory {

  private final CertificatePair certificatePair;

  public DefaultWebsocketFactory(CertificatePair certificatePair) {
    this.certificatePair = certificatePair;
  }

  @Override
  public DefaultWebsocket newInstance(
      ServletUpgradeRequest request,
      String protocol,
      String pathSpec,
      NodeSynchronization sync,
      WebsocketConsumer consumer) {
    // Create final, complete pair from the local (server) certificate ...
    CertificatePair finalPair = new CertificatePair(certificatePair);
    // ... plus the remote (client) certificate from the request
    finalPair.setRemoteCertificate(request.getCertificates()[0]);
    return new DefaultWebsocket(sync, pathSpec, consumer, finalPair);
  }
}
