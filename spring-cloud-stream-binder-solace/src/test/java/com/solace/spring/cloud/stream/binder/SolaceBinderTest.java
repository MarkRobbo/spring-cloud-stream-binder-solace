package com.solace.spring.cloud.stream.binder;

import com.solace.spring.boot.autoconfigure.SolaceJavaAutoConfiguration;
import com.solace.spring.cloud.stream.binder.properties.SolaceConsumerProperties;
import com.solace.spring.cloud.stream.binder.properties.SolaceProducerProperties;
import com.solacesystems.jcsmp.InvalidPropertiesException;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPSession;
import com.solacesystems.jcsmp.SpringJCSMPFactory;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.ConfigFileApplicationContextInitializer;
import org.springframework.cloud.stream.binder.ExtendedConsumerProperties;
import org.springframework.cloud.stream.binder.ExtendedProducerProperties;
import org.springframework.cloud.stream.binder.PartitionCapableBinderTests;
import org.springframework.cloud.stream.binder.Spy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = SolaceJavaAutoConfiguration.class, initializers = ConfigFileApplicationContextInitializer.class)
public class SolaceBinderTest
		extends PartitionCapableBinderTests<SolaceTestBinder, ExtendedConsumerProperties<SolaceConsumerProperties>, ExtendedProducerProperties<SolaceProducerProperties>> {

	@Autowired
	private SpringJCSMPFactory springJCSMPFactory;

	@Value("${test.failOnConnectionException:false}")
	private Boolean failOnConnectionException;

	private static JCSMPException sessionConnectError;


	@Override
	protected boolean usesExplicitRouting() {
		return true;
	}

	@Override
	protected String getClassUnderTestName() {
		return this.getClass().getSimpleName();
	}

	@Override
	protected SolaceTestBinder getBinder() throws Exception {
		if (testBinder == null) {
			testBinder = new SolaceTestBinder(assumeAndGetActiveSession());
		}
		return testBinder;
	}

	@Override
	protected ExtendedConsumerProperties<SolaceConsumerProperties> createConsumerProperties() {
		return new ExtendedConsumerProperties<>(new SolaceConsumerProperties());
	}

	@Override
	protected ExtendedProducerProperties<SolaceProducerProperties> createProducerProperties() {
		return new ExtendedProducerProperties<>(new SolaceProducerProperties());
	}

	@Override
	public Spy spyOn(String name) {
		return null;
	}


	// NOT YET SUPPORTED ---------------------------------
	@Override
	public void testPartitionedModuleJava() {
		Assume.assumeTrue("Partitioning not currently supported", false);
	}

	@Override
	public void testPartitionedModuleSpEL() {
		Assume.assumeTrue("Partitioning not currently supported", false);
	}
	// ---------------------------------------------------


	private JCSMPSession assumeAndGetActiveSession() throws InvalidPropertiesException {
		handleConnectionError();

		JCSMPSession session = springJCSMPFactory.createSession();

		try {
			session.connect();
		} catch (JCSMPException e) {
			sessionConnectError = e;
			handleConnectionError();
		}

		return session;
	}

	private void handleConnectionError() {
		if (sessionConnectError == null) return;

		String connectMsg = "Failed to establish connection to a Solace message broker. Skipping test...";
		logger.warn(connectMsg);
		if (failOnConnectionException) {
			Assert.fail(connectMsg);
		}
		else {
			Assume.assumeNoException(connectMsg, sessionConnectError);
		}
	}
}
