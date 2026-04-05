package ch.fhnw.digi.demo;

import javax.annotation.PostConstruct;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

@Component
public class SimpleUi extends JFrame {

	private static final long serialVersionUID = 1L;
	private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

	@Autowired
	private Publisher publisher;

	// --- Job-Erstellungs-Formular ---
	private JTextField descriptionField;
	private JComboBox<String> regionCombo;
	private JComboBox<String> typeCombo;
	private JLabel jobIdLabel;
	private int jobCounter = 0;

	// --- Anfragen-Tabelle ---
	private DefaultTableModel requestTableModel;
	private JTable requestTable;
	// Liste der pendenten Anfragen (Index = Tabellenzeile)
	private final List<JobRequestMessage> pendingRequests = new ArrayList<>();

	// --- Log ---
	private JTextArea logArea;
	private final ConcurrentLinkedDeque<String> messages = new ConcurrentLinkedDeque<>();

	@PostConstruct
	void init() {
		setSize(750, 700);
		setTitle("Disposition - Job Publisher (Gruppe 6)");
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		setLayout(new BorderLayout(5, 5));

		add(buildCreatePanel(), BorderLayout.NORTH);
		add(buildRequestPanel(), BorderLayout.CENTER);
		add(buildLogPanel(), BorderLayout.SOUTH);

		setVisible(true);
		updateJobId();
	}

	// ===================== Auftrag-Erstellen Panel =====================
	private JPanel buildCreatePanel() {
		JPanel panel = new JPanel(new GridBagLayout());
		panel.setBorder(BorderFactory.createTitledBorder(
				BorderFactory.createEtchedBorder(), " Neuen Auftrag erstellen ",
				TitledBorder.LEFT, TitledBorder.TOP));
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(4, 6, 4, 6);
		gbc.fill = GridBagConstraints.HORIZONTAL;

		// Job-ID
		gbc.gridx = 0; gbc.gridy = 0;
		panel.add(new JLabel("Job-ID:"), gbc);
		gbc.gridx = 1; gbc.gridwidth = 2;
		jobIdLabel = new JLabel("JOB-0000");
		jobIdLabel.setFont(jobIdLabel.getFont().deriveFont(Font.BOLD));
		panel.add(jobIdLabel, gbc);
		gbc.gridwidth = 1;

		// Beschreibung
		gbc.gridx = 0; gbc.gridy = 1;
		panel.add(new JLabel("Beschreibung:"), gbc);
		gbc.gridx = 1; gbc.gridwidth = 2; gbc.weightx = 1.0;
		descriptionField = new JTextField("Auftrag Beschreibung");
		panel.add(descriptionField, gbc);
		gbc.gridwidth = 1; gbc.weightx = 0;

		// Region
		gbc.gridx = 0; gbc.gridy = 2;
		panel.add(new JLabel("Region:"), gbc);
		gbc.gridx = 1;
		regionCombo = new JComboBox<>(new String[]{"basel", "zuerich", "bern"});
		panel.add(regionCombo, gbc);

		// Typ
		gbc.gridx = 0; gbc.gridy = 3;
		panel.add(new JLabel("Typ:"), gbc);
		gbc.gridx = 1;
		typeCombo = new JComboBox<>(new String[]{"repair", "maintenance"});
		panel.add(typeCombo, gbc);

		// Publish-Button
		gbc.gridx = 2; gbc.gridy = 2; gbc.gridheight = 2;
		gbc.fill = GridBagConstraints.BOTH;
		JButton publishBtn = new JButton("Veröffentlichen");
		publishBtn.setFont(publishBtn.getFont().deriveFont(Font.BOLD, 13f));
		publishBtn.addActionListener(e -> onPublishClicked());
		panel.add(publishBtn, gbc);

		return panel;
	}

	// ===================== Anfragen Panel =====================
	private JPanel buildRequestPanel() {
		JPanel panel = new JPanel(new BorderLayout(5, 5));
		panel.setBorder(BorderFactory.createTitledBorder(
				BorderFactory.createEtchedBorder(), " Eingehende Zuweisungsanfragen ",
				TitledBorder.LEFT, TitledBorder.TOP));

		requestTableModel = new DefaultTableModel(
				new String[]{"Job-ID", "Client-ID", "Zeitpunkt"}, 0) {
			@Override
			public boolean isCellEditable(int row, int column) {
				return false;
			}
		};
		requestTable = new JTable(requestTableModel);
		requestTable.setRowHeight(26);
		requestTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		requestTable.getColumnModel().getColumn(0).setPreferredWidth(120);
		requestTable.getColumnModel().getColumn(1).setPreferredWidth(180);
		requestTable.getColumnModel().getColumn(2).setPreferredWidth(80);

		JScrollPane scrollPane = new JScrollPane(requestTable);
		scrollPane.setPreferredSize(new Dimension(700, 180));
		panel.add(scrollPane, BorderLayout.CENTER);

		// Buttons unter der Tabelle
		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 5));

		JButton acceptBtn = new JButton("  Zuweisen  ");
		acceptBtn.setBackground(new Color(76, 175, 80));
		acceptBtn.setForeground(Color.WHITE);
		acceptBtn.setFont(acceptBtn.getFont().deriveFont(Font.BOLD, 13f));
		acceptBtn.setOpaque(true);
		acceptBtn.addActionListener(e -> onDecision(true));

		JButton rejectBtn = new JButton("  Ablehnen  ");
		rejectBtn.setBackground(new Color(244, 67, 54));
		rejectBtn.setForeground(Color.WHITE);
		rejectBtn.setFont(rejectBtn.getFont().deriveFont(Font.BOLD, 13f));
		rejectBtn.setOpaque(true);
		rejectBtn.addActionListener(e -> onDecision(false));

		buttonPanel.add(new JLabel("Zeile auswählen, dann:"));
		buttonPanel.add(acceptBtn);
		buttonPanel.add(rejectBtn);
		panel.add(buttonPanel, BorderLayout.SOUTH);

		return panel;
	}

	// ===================== Log Panel =====================
	private JPanel buildLogPanel() {
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBorder(BorderFactory.createTitledBorder(
				BorderFactory.createEtchedBorder(), " Protokoll ",
				TitledBorder.LEFT, TitledBorder.TOP));

		logArea = new JTextArea(10, 60);
		logArea.setEditable(false);
		logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
		JScrollPane scrollPane = new JScrollPane(logArea);
		scrollPane.setPreferredSize(new Dimension(700, 200));
		panel.add(scrollPane, BorderLayout.CENTER);

		return panel;
	}

	// ===================== Aktionen =====================

	private void onPublishClicked() {
		String jobId = "JOB-" + String.format("%04d", jobCounter);
		String description = descriptionField.getText().trim();
		String region = (String) regionCombo.getSelectedItem();
		String jobType = (String) typeCombo.getSelectedItem();

		if (description.isEmpty()) {
			JOptionPane.showMessageDialog(this, "Bitte Beschreibung eingeben.");
			return;
		}

		JobMessage job = new JobMessage(jobId, description, region, jobType);
		publisher.publishJob(job);

		appendMessage("VERÖFFENTLICHT: " + job);
		jobCounter++;
		updateJobId();
	}

	private void onDecision(boolean accepted) {
		int row = requestTable.getSelectedRow();
		if (row < 0 || row >= pendingRequests.size()) {
			JOptionPane.showMessageDialog(this, "Bitte zuerst eine Anfrage in der Tabelle auswählen.");
			return;
		}

		JobRequestMessage request = pendingRequests.remove(row);
		requestTableModel.removeRow(row);

		// sendAssignmentDecision gibt true zurück wenn tatsächlich zugewiesen,
		// false wenn abgelehnt (z.B. weil Job bereits an anderen Client vergeben)
		boolean actualResult = publisher.sendAssignmentDecision(request, accepted);

		if (actualResult) {
			appendMessage("ZUGEWIESEN: " + request.getJobId() + " -> " + request.getClientId());

			// Alle anderen Anfragen für denselben Job automatisch ablehnen
			autoRejectRemainingRequests(request.getJobId());
		} else {
			appendMessage("ABGELEHNT: " + request.getJobId() + " (Client: " + request.getClientId() + ")");
		}
	}

	/**
	 * Lehnt alle verbleibenden Anfragen für die gegebene JobId automatisch ab
	 * und entfernt sie aus der Tabelle.
	 */
	private void autoRejectRemainingRequests(String jobId) {
		// Rückwärts iterieren, damit Indizes beim Entfernen stimmen
		for (int i = pendingRequests.size() - 1; i >= 0; i--) {
			JobRequestMessage other = pendingRequests.get(i);
			if (other.getJobId().equals(jobId)) {
				pendingRequests.remove(i);
				requestTableModel.removeRow(i);
				// Ablehnung an den anderen Client senden
				publisher.sendAssignmentDecision(other, false);
				appendMessage("AUTO-ABGELEHNT: " + jobId + " (Client: " + other.getClientId() + " - Job bereits vergeben)");
			}
		}
	}

	private void updateJobId() {
		SwingUtilities.invokeLater(() ->
				jobIdLabel.setText("JOB-" + String.format("%04d", jobCounter)));
	}

	/** Wird vom Publisher aufgerufen, wenn eine Zuweisungsanfrage eingeht. */
	public void addPendingRequest(JobRequestMessage request) {
		SwingUtilities.invokeLater(() -> {
			pendingRequests.add(request);
			requestTableModel.addRow(new Object[]{
					request.getJobId(),
					request.getClientId(),
					LocalTime.now().format(TIME_FMT)
			});
		});
	}

	/** Log-Nachricht hinzufügen */
	public void appendMessage(String msg) {
		messages.addFirst("[" + LocalTime.now().format(TIME_FMT) + "] " + msg);
		while (messages.size() > 50) messages.removeLast();

		StringBuilder sb = new StringBuilder();
		for (String m : messages) {
			sb.append(m).append("\n");
		}
		SwingUtilities.invokeLater(() -> logArea.setText(sb.toString()));
	}
}
