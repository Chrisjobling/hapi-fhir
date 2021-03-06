package ca.uhn.fhir.rest.server;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.junit.*;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.annotation.Create;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.util.PortUtil;
import ca.uhn.fhir.util.TestUtil;

public class CreateBinaryR4Test {
	private static CloseableHttpClient ourClient;
	private static FhirContext ourCtx = FhirContext.forR4();
	private static Binary ourLastBinary;
	private static byte[] ourLastBinaryBytes;
	private static String ourLastBinaryString;
	private static int ourPort;
	private static Server ourServer;

	@Before
	public void before() {
		ourLastBinary = null;
		ourLastBinaryBytes = null;
		ourLastBinaryString = null;
	}

	@Test
	public void testRawBytesBinaryContentType() throws Exception {
		HttpPost post = new HttpPost("http://localhost:" + ourPort + "/Binary");
		post.setEntity(new ByteArrayEntity(new byte[] { 0, 1, 2, 3, 4 }));
		post.addHeader("Content-Type", "application/foo");
		CloseableHttpResponse status = ourClient.execute(post);
		try {
			assertEquals("application/foo", ourLastBinary.getContentType());
			assertArrayEquals(new byte[] { 0, 1, 2, 3, 4 }, ourLastBinary.getContent());
			assertArrayEquals(new byte[] { 0, 1, 2, 3, 4 }, ourLastBinaryBytes);
		} finally {
			IOUtils.closeQuietly(status);
		}
	}

	/**
	 * Technically the client shouldn't be doing it this way, but we'll be accepting
	 */
	@Test
	public void testRawBytesFhirContentType() throws Exception {

		Binary b = new Binary();
		b.setContentType("application/foo");
		b.setContent(new byte[] { 0, 1, 2, 3, 4 });
		String encoded = ourCtx.newJsonParser().encodeResourceToString(b);

		HttpPost post = new HttpPost("http://localhost:" + ourPort + "/Binary");
		post.setEntity(new StringEntity(encoded));
		post.addHeader("Content-Type", Constants.CT_FHIR_JSON);
		CloseableHttpResponse status = ourClient.execute(post);
		try {
			assertEquals("application/foo", ourLastBinary.getContentType());
			assertArrayEquals(new byte[] { 0, 1, 2, 3, 4 }, ourLastBinary.getContent());
		} finally {
			IOUtils.closeQuietly(status);
		}
	}

	@Test
	public void testRawBytesFhirContentTypeContainingFhir() throws Exception {

		Patient p = new Patient();
		p.getText().setDivAsString("A PATIENT");

		Binary b = new Binary();
		b.setContentType("application/xml+fhir");
		b.setContent(ourCtx.newXmlParser().encodeResourceToString(p).getBytes("UTF-8"));
		String encoded = ourCtx.newJsonParser().encodeResourceToString(b);

		HttpPost post = new HttpPost("http://localhost:" + ourPort + "/Binary");
		post.setEntity(new StringEntity(encoded));
		post.addHeader("Content-Type", Constants.CT_FHIR_JSON);
		CloseableHttpResponse status = ourClient.execute(post);
		try {
			assertEquals("application/xml+fhir", ourLastBinary.getContentType());
			assertArrayEquals(b.getContent(), ourLastBinary.getContent());
			assertEquals(encoded, ourLastBinaryString);
			assertArrayEquals(encoded.getBytes("UTF-8"), ourLastBinaryBytes);
		} finally {
			IOUtils.closeQuietly(status);
		}
	}

	@Test
	public void testRawBytesNoContentType() throws Exception {
		HttpPost post = new HttpPost("http://localhost:" + ourPort + "/Binary");
		post.setEntity(new ByteArrayEntity(new byte[] { 0, 1, 2, 3, 4 }));
		CloseableHttpResponse status = ourClient.execute(post);
		try {
			assertNull(ourLastBinary.getContentType());
			assertArrayEquals(new byte[] { 0, 1, 2, 3, 4 }, ourLastBinary.getContent());
		} finally {
			IOUtils.closeQuietly(status);
		}
	}

	@AfterClass
	public static void afterClassClearContext() throws Exception {
		ourServer.stop();
		TestUtil.clearAllStaticFieldsForUnitTest();
	}

	@BeforeClass
	public static void beforeClass() throws Exception {
		ourPort = PortUtil.findFreePort();
		ourServer = new Server(ourPort);

		BinaryProvider binaryProvider = new BinaryProvider();

		ServletHandler proxyHandler = new ServletHandler();
		RestfulServer servlet = new RestfulServer(ourCtx);
		servlet.setResourceProviders(binaryProvider);
		ServletHolder servletHolder = new ServletHolder(servlet);
		proxyHandler.addServletWithMapping(servletHolder, "/*");
		ourServer.setHandler(proxyHandler);
		ourServer.start();

		PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager(5000, TimeUnit.MILLISECONDS);
		HttpClientBuilder builder = HttpClientBuilder.create();
		builder.setConnectionManager(connectionManager);
		ourClient = builder.build();
	}

	public static class BinaryProvider implements IResourceProvider {

		@Create()
		public MethodOutcome createBinary(@ResourceParam Binary theBinary, @ResourceParam String theBinaryString, @ResourceParam byte[] theBinaryBytes) {
			ourLastBinary = theBinary;
			ourLastBinaryString = theBinaryString;
			ourLastBinaryBytes = theBinaryBytes;
			return new MethodOutcome(new IdType("Binary/001/_history/002"));
		}

		@Override
		public Class<? extends IBaseResource> getResourceType() {
			return Binary.class;
		}

	}

}
