package com.flozano.springhttpleak;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.pool.PoolStats;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import com.sun.net.httpserver.HttpServer;

public class HttpComponentsClientRequestFactoryLeak {

	private static final int REQUESTS = 1000;
	private static final int THREADS_REQUESTING = 20;
	private static final int POOL_SIZE = 20;
	private static final int PORT = 8082;
	HttpServer server;

	@Before
	@SuppressWarnings("restriction")
	public void setup() throws IOException {
		server = com.sun.net.httpserver.HttpServer.create(
				new InetSocketAddress(PORT), 0);
		server.createContext("/example", (exchange) -> {
			byte[] response = "OK".getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, response.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(response);
			}
		});
		server.setExecutor(null);
		server.start();
	}

	@SuppressWarnings("restriction")
	@After
	public void tearDown() {
		server.stop(0);
	}

	@Test
	public void testForLeak() throws InterruptedException {
		try (PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager()) {
			cm.setMaxTotal(POOL_SIZE);
			cm.setDefaultMaxPerRoute(POOL_SIZE);
			HttpComponentsClientHttpRequestFactory rf = new HttpComponentsClientHttpRequestFactory(
					HttpClientBuilder.create().setConnectionManager(cm).build());
			RestTemplate rt = new RestTemplate(rf);
			ExecutorService es = null;
			try {
				es = Executors.newFixedThreadPool(THREADS_REQUESTING);
				for (int i = 0; i < REQUESTS; i++) {
					es.submit(() -> rt.put("http://127.0.0.1:" + PORT
							+ "/example", "Hello"));
				}
			} finally {
				es.shutdown();
				assertTrue(es.awaitTermination(5, TimeUnit.SECONDS));
			}
			PoolStats stats = cm.getTotalStats();
			assertEquals(POOL_SIZE, stats.getAvailable());
			assertEquals(0, stats.getLeased());
		}
	}
}
