package ch.fhnw.digi.demo;

import javax.jms.ConnectionFactory;

import org.springframework.boot.autoconfigure.jms.DefaultJmsListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.config.JmsListenerContainerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;

@Configuration
public class JmsConfig {

	// Wandelt Java-Objekte in JSON um und umgekehrt
	@Bean
	public MessageConverter jacksonJmsMessageConverter() {
		MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
		converter.setTargetType(MessageType.TEXT);
		converter.setTypeIdPropertyName("_type");
		return converter;
	}

	// JmsTemplate für Topics (group6.dispo.jobs.new, group6.dispo.jobs.assignments)
	// Aufträge werden im Topic "group6.dispo.jobs.new" publiziert, Auftragszuweisungen im Topic "group6.dispo.jobs.assignments"
	@Bean
	public JmsTemplate topicJmsTemplate(ConnectionFactory connectionFactory) {
		JmsTemplate template = new JmsTemplate(connectionFactory);
		template.setPubSubDomain(true);
		template.setMessageConverter(jacksonJmsMessageConverter());

		return template;
	}

	// Factory für Listening auf Queues (group6.dispo.jobs.requestAssignment)
	// Lauscht auf Antworten von Queues
	@Bean
	public JmsListenerContainerFactory<?> queueFactory(ConnectionFactory connectionFactory,
			DefaultJmsListenerContainerFactoryConfigurer configurer) {
		DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
		configurer.configure(factory, connectionFactory);
		factory.setPubSubDomain(false);
		factory.setMessageConverter(jacksonJmsMessageConverter());
		return factory;
	}
}

