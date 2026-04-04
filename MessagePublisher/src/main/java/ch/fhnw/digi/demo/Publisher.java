package ch.fhnw.digi.demo;

import javax.annotation.PostConstruct;

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

	private static final String[] REGIONS = {"basel", "zürich", "bern"};
	private static final String[] JOB_TYPES = {"repair", "maintenance"};

	@PostConstruct
	void init() {
		// Publishing in separatem Thread, damit Spring-Context fertig starten kann
		Thread thread = new Thread(this::publishJobs);
		thread.setDaemon(true);
		thread.start();
	}

	private void publishJobs() {
		int counter = 0;

		// Kurz warten, bis Spring-Context vollständig gestartet ist (Alle 3 Sek. kurz warten)
		try { Thread.sleep(3000); } catch (InterruptedException e) { return; }

		while (true) {
			String region = REGIONS[counter % REGIONS.length];
			String jobType = JOB_TYPES[counter % JOB_TYPES.length];
			String jobId = "JOB-" + String.format("%04d", counter);

			JobMessage job = new JobMessage(jobId, jobType + " Auftrag #" + counter, region, jobType);

			// Auftrag auf Topic veröffentlichen
			topicJmsTemplate.convertAndSend(newJobsTopic, job);

			simpleUi.appendMessage("Veröffentlicht: " + job);
			counter++;

			// Alle 2 Sekunden einen neuen Auftrag
			try { Thread.sleep(2000); } catch (InterruptedException e) { break; }
		}
	}

	// Zuweisungsanfragen von der Queue empfangen und prüfen
	@JmsListener(destination = "${channel.queue.requestAssignment}", containerFactory = "queueFactory")
	public void handleAssignmentRequest(JobRequestMessage request) {

		// Prüfen ob der Auftrag schon vergeben ist
		// putIfAbsent gibt null zurück, wenn der Auftrag noch nicht vergeben war (und jetzt zugewiesen wird)
		// Ansonsten wird der zugeordnete ClientId zurückgegeben
		String previousClient = assignedJobs.putIfAbsent(request.getJobId(), request.getClientId());
		boolean accepted = (previousClient == null);

		JobAssignmentMessage response = new JobAssignmentMessage(
				request.getJobId(), request.getClientId(), accepted);

		topicJmsTemplate.convertAndSend(assignmentsTopic, response);

		if (accepted) {
			simpleUi.appendMessage("ZUGEWIESEN: " + request.getJobId() + " -> " + request.getClientId());
		} else {
			simpleUi.appendMessage("ABGELEHNT: " + request.getJobId() + " (bereits vergeben an " + previousClient + ")");
		}
	}
}
