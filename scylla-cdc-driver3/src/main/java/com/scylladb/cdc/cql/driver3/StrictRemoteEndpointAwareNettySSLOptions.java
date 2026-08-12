package com.scylladb.cdc.cql.driver3;

import com.datastax.driver.core.EndPoint;
import com.datastax.driver.core.RemoteEndpointAwareNettySSLOptions;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;

/**
 * Netty TLS options that verify the certificate identity of every contact point and discovered
 * node.
 *
 * <p>The upstream driver's remote-endpoint-aware options pass the peer host to the TLS engine for
 * SNI, but they do not enable JSSE endpoint identification. A trusted certificate for a different
 * ScyllaDB endpoint would therefore be accepted. CDC sessions must authenticate both the issuing
 * CA and the actual DNS/IP endpoint.
 */
final class StrictRemoteEndpointAwareNettySSLOptions
    extends RemoteEndpointAwareNettySSLOptions {
  static final String ENDPOINT_IDENTIFICATION_ALGORITHM = "HTTPS";

  StrictRemoteEndpointAwareNettySSLOptions(SslContext context) {
    super(context);
  }

  @Override
  public SslHandler newSSLHandler(SocketChannel channel, EndPoint remoteEndpoint) {
    SslHandler handler = super.newSSLHandler(channel, remoteEndpoint);
    enableEndpointIdentification(handler.engine());
    return handler;
  }

  static void enableEndpointIdentification(SSLEngine engine) {
    SSLParameters parameters = engine.getSSLParameters();
    parameters.setEndpointIdentificationAlgorithm(ENDPOINT_IDENTIFICATION_ALGORITHM);
    engine.setSSLParameters(parameters);
  }
}
