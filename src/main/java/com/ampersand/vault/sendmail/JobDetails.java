package com.ampersand.vault.sendmail;

public class JobDetails {
	private String downloadId;
	private String recipient;
	private String body;
	private String subject;
	private String[] mailCc;
	private String[] mailBcc;

	private String authorization;

	
	public JobDetails(String downloadId, String recipient, String body, String subject,String[] mailCc, String[] mailBcc,String authorization) {
		this.downloadId = downloadId;
		this.recipient = recipient;
		this.body = body;
		this.subject = subject;
		this.authorization = authorization;
		this.mailCc = mailCc;
		this.mailBcc = mailBcc;
	}

	public String getDownloadId() {
		return downloadId;
	}

	public String getRecipient() {
		return recipient;
	}

	public String getBody() {
		return body;
	}

	public String getSubject() {
		return subject;
	}

	public String getAuthorization() {
		return authorization;
	}
	public String[] getmailCc() {
		return mailCc;
	}
	public String[] getmailBcc() {
		return mailBcc;
	}
}
