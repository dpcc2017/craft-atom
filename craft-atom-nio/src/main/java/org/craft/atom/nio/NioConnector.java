package org.craft.atom.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.Selector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.craft.atom.io.Channel;
import org.craft.atom.io.IoHandler;
import org.craft.atom.nio.api.NioConnectorConfig;
import org.craft.atom.nio.api.NioTcpConnector;
import org.craft.atom.nio.spi.NioBufferSizePredictor;
import org.craft.atom.nio.spi.NioChannelEventDispatcher;

/**
 * Connects to server based TCP or UDP, communicates with the server, and fires events.
 * 
 * @author mindwind
 * @version 1.0, Feb 24, 2013
 * @see NioTcpConnector
 * @see NioUdpConnector
 */
abstract public class NioConnector extends NioReactor {
	
	private static final Log LOG = LogFactory.getLog(NioConnector.class);
	
	protected final IoHandler handler;
	protected final NioConnectorConfig config;
	protected final NioProcessorPool pool;
	protected final ExecutorService executorService = Executors.newCachedThreadPool();
	
	protected volatile Selector selector;
	protected volatile boolean selectable = false;
	protected volatile boolean shutdown = false;
	
	// ~ ------------------------------------------------------------------------------------------------------------
	
	/**
	 * Constructs a new connector with default configuration.
	 *  
	 * @param handler
	 */
	public NioConnector(IoHandler handler) {
		this(handler, new NioConnectorConfig());
	}
	
	/**
	 * Constructs a new connector with the specified configuration.
	 *  
	 * @param handler
	 * @param config
	 */
	public NioConnector(IoHandler handler, NioConnectorConfig config) {
		this(handler, config, new NioOrderedThreadPoolChannelEventDispatcher(), new NioAdaptiveBufferSizePredictor());
	}
	
	/**
	 * Constructs a new connector with the specified configuration and dispatcher.
	 * 
	 * @param handler
	 * @param config
	 * @param dispatcher
	 */
	public NioConnector(IoHandler handler, NioConnectorConfig config, NioChannelEventDispatcher dispatcher) {
		this(handler, config, dispatcher, new NioAdaptiveBufferSizePredictor());
	}
	
	/**
	 * Constructs a new connector with the specified configuration, dispatcher and predictor.
	 * 
	 * @param handler
	 * @param config
	 * @param dispatcher
	 * @param predictor
	 */
	public NioConnector(IoHandler handler, NioConnectorConfig config, NioChannelEventDispatcher dispatcher, NioBufferSizePredictor predictor) {
		if (handler == null) {
			throw new IllegalArgumentException("Handler should not be null!");
		}
		
		this.config = (config == null ? new NioConnectorConfig() : config);
		this.handler = handler;
		this.dispatcher = dispatcher;
		this.predictor = predictor;
		this.pool = new NioProcessorPool(config, handler, dispatcher);
		try {
			init();
		} catch (IOException e) {
			throw new RuntimeException("Failed to construct.", e);
		} finally {
			if (!selectable && selector != null) {
				try {
					selector.close();
				} catch (IOException e) {
					LOG.warn("unexpected exception", e);
				}
			}
		}
	}
	
	// ~ ------------------------------------------------------------------------------------------------------------
	
	private void init() throws IOException {
		selector = Selector.open();
		selectable = true;
	}
	
	/**
	 * Asynchronous connects to the specified ip and port.
	 * 
	 * @param ip
	 * @param port
	 * @return <code>Future</code> instance which is completed when the channel initiated by this call succeeds or fails.
	 */
	public Future<Channel<byte[]>> connect(String ip, int port) {
		SocketAddress remoteAddress = new InetSocketAddress(ip, port);
		return connect(remoteAddress);
	}
	
	/**
	 * Asynchronous connects to the specified remote address.
	 * 
	 * @param remoteAddress
	 * @return <code>Future</code> instance which is completed when the channel initiated by this call succeeds or fails.
	 */
	public Future<Channel<byte[]>> connect(SocketAddress remoteAddress) {
        return connect(remoteAddress, null);
    }
	
	/**
	 * Asynchronous connects to the specified remote address and binds to the specified local address.
	 * 
	 * @param remoteAddress
	 * @param localAddress
	 * @return <code>Future</code> instance which is completed when the channel initiated by this call succeeds or fails.
	 */
	public Future<Channel<byte[]>> connect(SocketAddress remoteAddress, SocketAddress localAddress) {
		if (!this.selectable) {
			throw new IllegalStateException("The connector is already shutdown.");
		}

		if (remoteAddress == null) {
			throw new IllegalArgumentException("Remote address is null.");
		}
		
		if (handler == null) {
			throw new IllegalStateException("Handler is not be set!");
		}
		
		return connectByProtocol(remoteAddress, localAddress);
	}
	
	public void shutdown() {
		this.selectable = false;
		this.shutdown = true;
		this.selector.wakeup();
	}
	
	abstract protected Future<Channel<byte[]>> connectByProtocol(SocketAddress remoteAddress, SocketAddress localAddress);
	
}
