package com.solace.spring.stream.binder.provisioning;

import com.solace.spring.stream.binder.util.SolaceProvisioningUtil;
import com.solace.spring.stream.binder.properties.SolaceCommonProperties;
import com.solacesystems.jcsmp.EndpointProperties;
import com.solacesystems.jcsmp.JCSMPErrorResponseException;
import com.solacesystems.jcsmp.JCSMPErrorResponseSubcodeEx;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPSession;
import com.solacesystems.jcsmp.Queue;
import com.solacesystems.jcsmp.Topic;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.stream.binder.ExtendedConsumerProperties;
import org.springframework.cloud.stream.binder.ExtendedProducerProperties;
import com.solace.spring.stream.binder.properties.SolaceConsumerProperties;
import com.solace.spring.stream.binder.properties.SolaceProducerProperties;
import org.springframework.cloud.stream.provisioning.ConsumerDestination;
import org.springframework.cloud.stream.provisioning.ProducerDestination;
import org.springframework.cloud.stream.provisioning.ProvisioningException;
import org.springframework.cloud.stream.provisioning.ProvisioningProvider;

import java.util.HashMap;
import java.util.Map;

public class SolaceQueueProvisioner
		implements ProvisioningProvider<ExtendedConsumerProperties<SolaceConsumerProperties>,ExtendedProducerProperties<SolaceProducerProperties>> {

	private JCSMPSession jcsmpSession;
	private Map<String,String> queueToTopicBindings = new HashMap<>();

	private static final Log logger = LogFactory.getLog(SolaceQueueProvisioner.class);

	public SolaceQueueProvisioner(JCSMPSession jcsmpSession) {
		this.jcsmpSession = jcsmpSession;
	}

	@Override
	public ProducerDestination provisionProducerDestination(String name,
															ExtendedProducerProperties<SolaceProducerProperties> properties)
			throws ProvisioningException {

		String topicName = SolaceProvisioningUtil.getTopicName(name, properties.getExtension());

		for (String groupName : properties.getRequiredGroups()) {
			String queueName = SolaceProvisioningUtil.getQueueName(topicName, groupName, properties.getExtension());
			logger.info(String.format("Creating durable queue %s for required consumer group %s", queueName, groupName));
			Queue queue = provisionQueue(queueName, true, properties.getExtension());

			addSubscriptionToQueue(queue, topicName);
			queueToTopicBindings.put(queue.getName(), topicName);
		}

		return new SolaceProducerDestination(topicName);
	}

	@Override
	public ConsumerDestination provisionConsumerDestination(String name, String group,
															ExtendedConsumerProperties<SolaceConsumerProperties> properties)
			throws ProvisioningException {

		String topicName = SolaceProvisioningUtil.getTopicName(name, properties.getExtension());
		boolean isAnonQueue = SolaceProvisioningUtil.isAnonQueue(group);
		boolean isDurableQueue = SolaceProvisioningUtil.isDurableQueue(group, properties.getExtension());
		String queueName = SolaceProvisioningUtil.getQueueName(topicName, group, properties.getExtension(), isAnonQueue);

		logger.info(isAnonQueue ?
				String.format("Creating anonymous (temporary) queue %s", queueName) :
				String.format("Creating %s queue %s for consumer group %s", isDurableQueue ? "durable" : "temporary", queueName, group));
		Queue queue = provisionQueue(queueName, isDurableQueue, properties.getExtension());

		queueToTopicBindings.put(queue.getName(), topicName);
		return new SolaceConsumerDestination(queue.getName());
	}

	private Queue provisionQueue(String name, boolean isDurable, SolaceCommonProperties properties) throws ProvisioningException {
		Queue queue;
		if (isDurable) {
			try {
				queue = JCSMPFactory.onlyInstance().createQueue(name);
				EndpointProperties endpointProperties = SolaceProvisioningUtil.getEndpointProperties(properties);
				jcsmpSession.provision(queue, endpointProperties, JCSMPSession.FLAG_IGNORE_ALREADY_EXISTS);
			} catch (JCSMPException e) {
				String msg = String.format("Failed to provision durable queue %s", name);
				logger.error(msg, e);
				throw new ProvisioningException(msg, e);
			}
		} else {
			try {
				// EndpointProperties will be applied during consumer creation
				queue = jcsmpSession.createTemporaryQueue(name);
			} catch (JCSMPException e) {
				String msg = String.format("Failed to create temporary queue %s", name);
				logger.error(msg, e);
				throw new ProvisioningException(msg, e);
			}
		}

		return queue;
	}

	public void addSubscriptionToQueue(Queue queue, String topicName) {
		logger.info(String.format("Subscribing queue %s to topic %s", queue.getName(), topicName));
		try {
			Topic topic = JCSMPFactory.onlyInstance().createTopic(topicName);
			try {
				jcsmpSession.addSubscription(queue, topic, JCSMPSession.WAIT_FOR_CONFIRM);
			} catch (JCSMPErrorResponseException e) {
				if (e.getSubcodeEx() == JCSMPErrorResponseSubcodeEx.SUBSCRIPTION_ALREADY_PRESENT) {
					logger.warn(String.format(
							"Queue %s is already subscribed to topic %s, SUBSCRIPTION_ALREADY_PRESENT error will be ignored...",
							queue.getName(), topicName));
				} else {
					throw e;
				}
			}
		} catch (JCSMPException e) {
			String msg = String.format("Failed to add subscription of %s to queue %s", topicName, queue.getName());
			logger.error(msg, e);
			throw new ProvisioningException(msg, e);
		}
	}

	public String getBoundTopicNameForQueue(String queueName) {
		return queueToTopicBindings.get(queueName);
	}
}
