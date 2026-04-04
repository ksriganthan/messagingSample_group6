package ch.fhnw.digi.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

@Component
public class Receiver {

	@Autowired
	private SimpleUi simpleUi;

	@Autowired
	@Qualifier("queueJmsTemplate")
	private JmsTemplate queueJmsTemplate;

	@Value("${client.id:group6}")
	private String clientId;

	@Value("${client.region:}")
	private String filterRegion;

	@Value("${client.jobType:}")
	private String filterJobType;

	@Value("${channel.queue.requestAssignment}")
	private String requestAssignmentQueue;

	// Neue Aufträge vom Topic empfangen
	@JmsListener(destination = "${channel.topic.newJobs}", containerFactory = "topicFactory")
	public void receiveJob(JobMessage job) {

		// Region-Filter anwenden (leer = alle Regionen)
		if (filterRegion != null && !filterRegion.isEmpty()
				&& !filterRegion.equalsIgnoreCase(job.getRegion())) {
			return;
		}

		// JobType-Filter anwenden (leer = alle Typen)
		if (filterJobType != null && !filterJobType.isEmpty()
				&& !filterJobType.equalsIgnoreCase(job.getJobType())) {
			return;
		}

		simpleUi.appendMessage("Neuer Auftrag: " + job);

		// Automatisch Zuweisung anfragen
		JobRequestMessage request = new JobRequestMessage(job.getJobId(), clientId);
		queueJmsTemplate.convertAndSend(requestAssignmentQueue, request);

		simpleUi.appendMessage("Zuweisung angefragt fuer: " + job.getJobId());
	}

	// Zuweisungsbestätigungen vom Topic empfangen
	@JmsListener(destination = "${channel.topic.assignments}", containerFactory = "topicFactory")
	public void receiveAssignment(JobAssignmentMessage assignment) {

		// Nur Zuweisungen für unseren Client verarbeiten
		if (!clientId.equals(assignment.getClientId())) {
			return;
		}

		String status = assignment.isAssigned() ? "ZUGEWIESEN" : "ABGELEHNT";
		simpleUi.appendMessage("Auftrag " + assignment.getJobId() + ": " + status);
	}
}
