package com.solace.spring.stream.binder.properties;

public class SolaceProducerProperties extends SolaceCommonProperties {
	private boolean isQueueNameGroupOnly = false;

	public boolean isQueueNameGroupOnly() {
		return isQueueNameGroupOnly;
	}

	public SolaceProducerProperties setQueueNameGroupOnly(boolean queueNameGroupOnly) {
		isQueueNameGroupOnly = queueNameGroupOnly;
		return this;
	}
}
