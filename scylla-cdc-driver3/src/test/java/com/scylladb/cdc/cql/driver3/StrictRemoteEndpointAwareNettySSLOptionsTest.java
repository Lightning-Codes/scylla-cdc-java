package com.scylladb.cdc.cql.driver3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.ReferenceCountUtil;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import org.junit.jupiter.api.Test;

class StrictRemoteEndpointAwareNettySSLOptionsTest {
  @Test
  void enablesHttpsEndpointIdentification() throws Exception {
    SSLEngine engine =
        SSLContext.getDefault().createSSLEngine("scylladb-ashburn-ad-1-0.sophena.svc", 9142);

    StrictRemoteEndpointAwareNettySSLOptions.enableEndpointIdentification(engine);

    assertEquals(
        StrictRemoteEndpointAwareNettySSLOptions.ENDPOINT_IDENTIFICATION_ALGORITHM,
        engine.getSSLParameters().getEndpointIdentificationAlgorithm());
  }

  @Test
  void acceptsCertificateWithMatchingDnsSan() throws Exception {
    assertTrue(handshakeSucceeds("scylladb-ashburn-ad-1-0.sophena.svc.cluster.local"));
  }

  @Test
  void rejectsCertificateWithWrongDnsSan() throws Exception {
    assertFalse(handshakeSucceeds("scylladb-ashburn-ad-2-0.sophena.svc.cluster.local"));
  }

  private static boolean handshakeSucceeds(String peerHost) throws Exception {
    SelfSignedCertificate certificate =
        new SelfSignedCertificate("scylladb-ashburn-ad-1-0.sophena.svc.cluster.local");
    try {
      SslContext serverContext =
          SslContextBuilder.forServer(certificate.certificate(), certificate.privateKey())
              .sslProvider(SslProvider.JDK)
              .build();
      SslContext clientContext =
          SslContextBuilder.forClient()
              .trustManager(certificate.certificate())
              .sslProvider(SslProvider.JDK)
              .build();

      SSLEngine clientEngine =
          clientContext.newEngine(ByteBufAllocator.DEFAULT, peerHost, 9142);
      StrictRemoteEndpointAwareNettySSLOptions.enableEndpointIdentification(clientEngine);
      SslHandler clientHandler = new SslHandler(clientEngine);
      SslHandler serverHandler = serverContext.newHandler(ByteBufAllocator.DEFAULT);
      EmbeddedChannel client = new EmbeddedChannel(clientHandler);
      EmbeddedChannel server = new EmbeddedChannel(serverHandler);

      try {
        for (int attempt = 0;
            attempt < 100
                && !clientHandler.handshakeFuture().isDone()
                && !serverHandler.handshakeFuture().isDone();
            attempt++) {
          transferTlsFrames(client, server);
          transferTlsFrames(server, client);
          client.runPendingTasks();
          server.runPendingTasks();
        }

        transferTlsFrames(client, server);
        transferTlsFrames(server, client);
        assertTrue(clientHandler.handshakeFuture().isDone(), "TLS handshake did not finish");
        return clientHandler.handshakeFuture().isSuccess();
      } finally {
        client.finishAndReleaseAll();
        server.finishAndReleaseAll();
      }
    } finally {
      certificate.delete();
    }
  }

  private static void transferTlsFrames(EmbeddedChannel source, EmbeddedChannel destination) {
    Object frame;
    while ((frame = source.readOutbound()) != null) {
      if (!destination.isOpen()) {
        ReferenceCountUtil.release(frame);
        continue;
      }
      try {
        destination.writeInbound(frame);
      } catch (Exception ignored) {
        // A failed handshake can close the destination while its TLS alert is still in flight.
      }
    }
  }
}
