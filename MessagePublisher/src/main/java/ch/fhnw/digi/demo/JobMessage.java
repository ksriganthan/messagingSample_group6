package ch.fhnw.digi.demo;

// Auftragsnachricht von Disposition an Clients (Publisher -> Subscriber/Topic)
public class JobMessage {

	private String jobId;
	private String description;
	private String region;
	private String jobType; // "repair" oder "maintenance"

	public JobMessage() {
	}

	public JobMessage(String jobId, String description, String region, String jobType) {
		this.jobId = jobId;
		this.description = description;
		this.region = region;
		this.jobType = jobType;
	}

	public String getJobId() {
		return jobId;
	}

	public void setJobId(String jobId) {
		this.jobId = jobId;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getRegion() {
		return region;
	}

	public void setRegion(String region) {
		this.region = region;
	}

	public String getJobType() {
		return jobType;
	}

	public void setJobType(String jobType) {
		this.jobType = jobType;
	}

	@Override
	public String toString() {
		return "Job{id='" + jobId + "', typ='" + jobType + "', region='" + region + "', beschreibung='" + description + "'}";
	}
}

