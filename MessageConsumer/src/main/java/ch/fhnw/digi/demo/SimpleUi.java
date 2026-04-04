package ch.fhnw.digi.demo;

import javax.annotation.PostConstruct;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentLinkedDeque;

@Component
public class SimpleUi extends JFrame {

	private static final long serialVersionUID = 1L;
	private JTextArea logArea;
	private final ConcurrentLinkedDeque<String> messages = new ConcurrentLinkedDeque<>();
	private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

	@Value("${client.id:group6}")
	private String clientId;

	@Value("${client.region:}")
	private String filterRegion;

	@Value("${client.jobType:}")
	private String filterJobType;

	@PostConstruct
	void init() {
		String regionDisplay = (filterRegion == null || filterRegion.isEmpty()) ? "alle" : filterRegion;
		String jobTypeDisplay = (filterJobType == null || filterJobType.isEmpty()) ? "alle" : filterJobType;
		setSize(550, 400);
		setTitle("Auftrags-Client [" + clientId + "] - Region: " + regionDisplay + " | Typ: " + jobTypeDisplay);
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		logArea = new JTextArea();
		logArea.setEditable(false);
		JScrollPane scrollPane = new JScrollPane(logArea);
		getContentPane().add(scrollPane);
		setVisible(true);
	}

	public void appendMessage(String msg) {
		messages.addFirst("[" + LocalTime.now().format(TIME_FMT) + "] " + msg);
		while (messages.size() > 50) messages.removeLast();

		String regionDisplay = (filterRegion == null || filterRegion.isEmpty()) ? "alle" : filterRegion;
		String jobTypeDisplay = (filterJobType == null || filterJobType.isEmpty()) ? "alle" : filterJobType;
		StringBuilder sb = new StringBuilder("Client läuft... Region: " + regionDisplay + " | Typ: " + jobTypeDisplay + "\n\n");
		for (String m : messages) {
			sb.append(m).append("\n");
		}
		sb.append("\nFenster schliessen zum Beenden");
		logArea.setText(sb.toString());
	}
}
