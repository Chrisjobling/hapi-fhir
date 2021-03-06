package ca.uhn.fhir.rest.server;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.param.TokenAndListParam;
import ca.uhn.fhir.util.PortUtil;
import ca.uhn.fhir.util.TestUtil;
import ca.uhn.fhir.util.UrlUtil;
import jdk.nashorn.internal.ir.annotations.Ignore;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.hamcrest.Matchers;
import org.hl7.fhir.dstu3.hapi.rest.server.GraphQLProviderDstu3;
import org.hl7.fhir.dstu3.hapi.validation.DefaultProfileValidationSupport;
import org.hl7.fhir.dstu3.model.*;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.utilities.graphql.Argument;
import org.hl7.fhir.utilities.graphql.IGraphQLStorageServices;
import org.hl7.fhir.utilities.graphql.ReferenceResolution;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class GraphQLDstu3ProviderTest {

	private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(GraphQLDstu3ProviderTest.class);
	private static CloseableHttpClient ourClient;
	private static FhirContext ourCtx = FhirContext.forDstu3();
	private static int ourPort;
	private static Server ourServer;

	@AfterClass
	public static void afterClassClearContext() throws Exception {
		ourServer.stop();
		TestUtil.clearAllStaticFieldsForUnitTest();
	}

	@BeforeClass
	public static void beforeClass() throws Exception {
		ourPort = PortUtil.findFreePort();
		ourServer = new Server(ourPort);



		ServletHandler proxyHandler = new ServletHandler();
		RestfulServer servlet = new RestfulServer(ourCtx);
		servlet.setDefaultResponseEncoding(EncodingEnum.JSON);
		servlet.setPagingProvider(new FifoMemoryPagingProvider(10));

		servlet.registerProvider(new DummyPatientResourceProvider());
		MyStorageServices storageServices = new MyStorageServices();
		servlet.registerProvider(new GraphQLProviderDstu3(ourCtx, new DefaultProfileValidationSupport(), storageServices));
		ServletHolder servletHolder = new ServletHolder(servlet);
		proxyHandler.addServletWithMapping(servletHolder, "/*");
		ourServer.setHandler(proxyHandler);
		ourServer.start();

		PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager(5000, TimeUnit.MILLISECONDS);
		HttpClientBuilder builder = HttpClientBuilder.create();
		builder.setConnectionManager(connectionManager);
		ourClient = builder.build();

	}

	@Before
	public void before() {
		//nothing
	}

	@Test
	@Ignore
	public void testGraphInstance() throws Exception {
		String query = "{name{family,given}}";
		HttpGet httpGet = new HttpGet("http://localhost:" + ourPort + "/Patient/123/$graphql?query=" + UrlUtil.escape(query));
		CloseableHttpResponse status = ourClient.execute(httpGet);
		try {
			String responseContent = IOUtils.toString(status.getEntity().getContent(), StandardCharsets.UTF_8);
			ourLog.info(responseContent);
			assertEquals(200, status.getStatusLine().getStatusCode());

			assertEquals("{\n" +
				"  \"name\":[{\n" +
				"    \"family\":[\"FAMILY\"],\n" +
				"    \"given\":[\"GIVEN1\",\"GIVEN2\"]\n" +
				"  },{\n" +
				"    \"given\":[\"GivenOnly1\",\"GivenOnly2\"]\n" +
				"  }]\n" +
				"}", responseContent);
			assertThat(status.getFirstHeader(Constants.HEADER_CONTENT_TYPE).getValue(), Matchers.startsWith("application/json"));

		} finally {
			IOUtils.closeQuietly(status.getEntity().getContent());
		}

	}

	@Test
	@org.junit.Ignore
	public void testGraphSystemInstance() throws Exception {
		String query = "{Patient(id:123){id,name{given,family}}}";
		HttpGet httpGet = new HttpGet("http://localhost:" + ourPort + "/$graphql?query=" + UrlUtil.escape(query));
		CloseableHttpResponse status = ourClient.execute(httpGet);
		try {
			String responseContent = IOUtils.toString(status.getEntity().getContent(), StandardCharsets.UTF_8);
			ourLog.info(responseContent);
			assertEquals(200, status.getStatusLine().getStatusCode());

			assertEquals("{\n" +
				"  \"Patient\":{\n" +
				"    \"name\":[{\n" +
				"      \"given\":[\"GIVEN1\",\"GIVEN2\"],\n" +
				"      \"family\":[\"FAMILY\"]\n" +
				"    },{\n" +
				"      \"given\":[\"GivenOnly1\",\"GivenOnly2\"]\n" +
				"    }]\n" +
				"  }\n" +
				"}", responseContent);
			assertThat(status.getFirstHeader(Constants.HEADER_CONTENT_TYPE).getValue(), Matchers.startsWith("application/json"));

		} finally {
			IOUtils.closeQuietly(status.getEntity().getContent());
		}

	}

	@Test
	@Ignore
	public void testGraphSystemList() throws Exception {
		String query = "{PatientList(name:\"pet\"){name{family,given}}}";
		HttpGet httpGet = new HttpGet("http://localhost:" + ourPort + "/$graphql?query=" + UrlUtil.escape(query));
		CloseableHttpResponse status = ourClient.execute(httpGet);
		try {
			String responseContent = IOUtils.toString(status.getEntity().getContent(), StandardCharsets.UTF_8);
			ourLog.info(responseContent);
			assertEquals(200, status.getStatusLine().getStatusCode());

			assertEquals("{\n" +
				"  \"PatientList\":[{\n" +
				"    \"name\":[{\n" +
				"      \"family\":[\"pet\"],\n" +
				"      \"given\":[\"GIVEN1\",\"GIVEN2\"]\n" +
				"    },{\n" +
				"      \"given\":[\"GivenOnly1\",\"GivenOnly2\"]\n" +
				"    }]\n" +
				"  },{\n" +
				"    \"name\":[{\n" +
				"      \"given\":[\"GivenOnlyB1\",\"GivenOnlyB2\"]\n" +
				"    }]\n" +
				"  }]\n" +
				"}", responseContent);
			assertThat(status.getFirstHeader(Constants.HEADER_CONTENT_TYPE).getValue(), Matchers.startsWith("application/json"));

		} finally {
			IOUtils.closeQuietly(status.getEntity().getContent());
		}

	}

	@Test
	@Ignore
	public void testGraphInstanceWithFhirpath() throws Exception {
		String query = "{name(fhirpath:\"family.exists()\"){text,given,family}}";
		HttpGet httpGet = new HttpGet("http://localhost:" + ourPort + "/Patient/123/$graphql?query=" + UrlUtil.escape(query));
		CloseableHttpResponse status = ourClient.execute(httpGet);
		try {
			String responseContent = IOUtils.toString(status.getEntity().getContent(), StandardCharsets.UTF_8);
			ourLog.info(responseContent);
			assertEquals(200, status.getStatusLine().getStatusCode());

			assertEquals("{\n" +
				"  \"name\":[{\n" +
				"    \"given\":[\"GIVEN1\",\"GIVEN2\"],\n" +
				"    \"family\":[\"FAMILY\"]\n" +
				"  }]\n" +
				"}", responseContent);
			assertThat(status.getFirstHeader(Constants.HEADER_CONTENT_TYPE).getValue(), Matchers.startsWith("application/json"));

		} finally {
			IOUtils.closeQuietly(status.getEntity().getContent());
		}

	}

	public static class DummyPatientResourceProvider implements IResourceProvider {

		@Override
		public Class<? extends IBaseResource> getResourceType() {
			return Patient.class;
		}

		@SuppressWarnings("rawtypes")
		@Search()
		public List search(
			@OptionalParam(name = Patient.SP_IDENTIFIER) TokenAndListParam theIdentifiers) {
			ArrayList<Patient> retVal = new ArrayList<>();

			for (int i = 0; i < 200; i++) {
				Patient patient = new Patient();
				patient.addName(new HumanName().setFamily("FAMILY"));
				patient.getIdElement().setValue("Patient/" + i);
				retVal.add((Patient) patient);
			}
			return retVal;
		}

	}

	private static class MyStorageServices implements IGraphQLStorageServices<Resource, Reference, Bundle> {
		@Override
		public ReferenceResolution<Resource> lookup(Object theAppInfo, Resource theContext, Reference theReference) throws FHIRException {
			ourLog.info("lookup from {} to {}", theContext.getIdElement().getValue(), theReference.getReference());
			return null;
		}

		@Override
		public Resource lookup(Object theAppInfo, String theType, String theId) throws FHIRException {
			ourLog.info("lookup {}/{}", theType, theId);

			if (theType.equals("Patient") && theId.equals("123")) {
				Patient p = new Patient();
				p.addName()
					.setFamily("FAMILY")
					.addGiven("GIVEN1")
					.addGiven("GIVEN2");
				p.addName()
					.addGiven("GivenOnly1")
					.addGiven("GivenOnly2");
				return p;
			}

			return null;
		}

		@Override
		public void listResources(Object theAppInfo, String theType, List<Argument> theSearchParams, List<Resource> theMatches) throws FHIRException {
			ourLog.info("listResources of {} - {}", theType, theSearchParams);

			if (theSearchParams.size() == 1) {
				String name = theSearchParams.get(0).getName();
				if ("name".equals(name)) {
					Patient p = new Patient();
					p.addName()
						.setFamily(theSearchParams.get(0).getValues().get(0).toString())
						.addGiven("GIVEN1")
						.addGiven("GIVEN2");
					p.addName()
						.addGiven("GivenOnly1")
						.addGiven("GivenOnly2");
					theMatches.add(p);

					p = new Patient();
					p.addName()
						.addGiven("GivenOnlyB1")
						.addGiven("GivenOnlyB2");
					theMatches.add(p);

				}
			}
		}

		@Override
		public Bundle search(Object theAppInfo, String theType, List<Argument> theSearchParams) throws FHIRException {
			ourLog.info("search on {} - {}", theType, theSearchParams);
			return null;
		}
	}
}
