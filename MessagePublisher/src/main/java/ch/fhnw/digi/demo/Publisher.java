package ch.fhnw.digi.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class Publisher {

	@Autowired
	private SimpleUi simpleUi;

	@Autowired
	@Qualifier("topicJmsTemplate")
	private JmsTemplate topicJmsTemplate;

	@Value("${channel.topic.newJobs}")
	private String newJobsTopic;

	@Value("${channel.topic.assignments}")
	private String assignmentsTopic;

	// Merkt sich welche Aufträge bereits vergeben sind (JobId -> ClientId)
	private final Map<String, String> assignedJobs = new ConcurrentHashMap<>();

	/**
	 * Wird von der GUI aufgerufen, wenn der Disponent einen neuen Auftrag erstellt.
	 * Veröffentlicht den Auftrag auf dem Haupt-Topic sowie auf den
	 * regionsspezifischen und typspezifischen Content-Based-Router Topics.
	 */
	public void publishJob(JobMessage job) {
		// 1. Auf Haupt-Topic veröffentlichen (alle Aufträge)
		topicJmsTemplate.convertAndSend(newJobsTopic, job);

		// 2. Content-Based Router: zusätzlich auf regionsspezifisches Topic
		topicJmsTemplate.convertAndSend(newJobsTopic + "." + job.getRegion(), job);

		// 3. Content-Based Router: zusätzlich auf typspezifisches Topic
		topicJmsTemplate.convertAndSend(newJobsTopic + "." + job.getJobType(), job);
	}

	/**
	 * Zuweisungsanfragen von der Queue empfangen.
	 * Statt automatisch zu entscheiden, wird die Anfrage an die GUI weitergeleitet,
	 * damit der Disponent manuell zuweisen oder ablehnen kann.
	 */
	@JmsListener(destination = "${channel.queue.requestAssignment}", containerFactory = "queueFactory")
	public void handleAssignmentRequest(JobRequestMessage request) {
		simpleUi.appendMessage("ANFRAGE eingegangen: " + request.getJobId() + " von " + request.getClientId());
		simpleUi.addPendingRequest(request);
	}

	/**
	 * Wird von der GUI aufgerufen, wenn der Disponent eine Entscheidung trifft.
	 */
	public void sendAssignmentDecision(JobRequestMessage request, boolean accepted) {
		if (accepted) {
			// Prüfen ob der Auftrag schon vergeben ist
			String previousClient = assignedJobs.putIfAbsent(request.getJobId(), request.getClientId());
			if (previousClient != null) {
				// Bereits vergeben – trotzdem Ablehnung senden
				accepted = false;
				simpleUi.appendMessage("HINWEIS: " + request.getJobId() + " war bereits an " + previousClient + " vergeben!");
			}
		}

		JobAssignmentMessage response = new JobAssignmentMessage(
				request.getJobId(), request.getClientId(), accepted);

		topicJmsTemplate.convertAndSend(assignmentsTopic, response);
	}
}
