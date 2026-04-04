package ch.fhnw.digi.demo;

import javax.jms.ConnectionFactory;

import org.springframework.beans.factory.annotation.Value;
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

	@Value("${client.id:group6}")
	private String clientId;

	// Umwandlung JSON <-> Java-Objekte für Nachrichten
	@Bean
	public MessageConverter jacksonJmsMessageConverter() {
		MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
		converter.setTargetType(MessageType.TEXT);
		converter.setTypeIdPropertyName("_type");
		return converter;
	}

	// JmsTemplate für Queues (group6.dispo.jobs.requestAssignment)
	// Sendet Anfrage per Queue
	@Bean
	public JmsTemplate queueJmsTemplate(ConnectionFactory connectionFactory) {
		JmsTemplate template = new JmsTemplate(connectionFactory);
		template.setPubSubDomain(false);
		template.setMessageConverter(jacksonJmsMessageConverter());
		return template;
	}

	// Factory für Listening auf Topics (dispo.jobs.new, dispo.jobs.assignments)
	// Lauscht auf Topic
	@Bean
	public JmsListenerContainerFactory<?> topicFactory(ConnectionFactory connectionFactory,
			DefaultJmsListenerContainerFactoryConfigurer configurer) {
		DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
		configurer.configure(factory, connectionFactory);
		factory.setPubSubDomain(true);
		factory.setMessageConverter(jacksonJmsMessageConverter());

		return factory;
	}
}

