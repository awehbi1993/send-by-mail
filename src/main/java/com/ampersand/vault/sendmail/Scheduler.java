package com.ampersand.vault.sendmail;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import jakarta.mail.internet.MimeMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import com.ampersand.vault.core.utilities.entities.DownloadEntry;
import com.ampersand.vault.core.utilities.service.AlfrescoRestApi;

public class Scheduler implements Runnable {
	private Logger logger = LoggerFactory.getLogger(Scheduler.class);

	private AlfrescoRestApi alfrescoRestApi;
	private Queue<JobDetails> pending = new ConcurrentLinkedQueue<>();
	private JavaMailSender mailSender;
	private String mailUsername;
	private String mailAttachmentPath;

	private static long SLEEP_TIME = 60 * 1000;
	private static int MAX_FAILED_COUNT = 3;

	private boolean stopped = false;

	public Scheduler(AlfrescoRestApi alfrescoRestApi, JavaMailSender mailSender, String mailUsername,
			String mailAttachmentPath) {
		this.alfrescoRestApi = alfrescoRestApi;
		this.mailSender = mailSender;
		this.mailUsername = mailUsername;
		this.mailAttachmentPath = mailAttachmentPath;

		new Thread(this).start();
	}

	public void addJob(JobDetails job) {
		pending.add(job);
	}

	@Override
	public void run() {
		while (!stopped) {
			JobDetails jd;
			if ((jd = pending.poll()) != null) {
				ResponseEntity<DownloadEntry> status = null;
				int failedCount = 0;

				do {
					try {
						status = alfrescoRestApi.getDownload(jd.getDownloadId(), null, jd.getAuthorization());
						logger.info(status.getBody().entry.status);
						if (status.getBody().entry.status.equalsIgnoreCase("DONE"))
							break;

						try {
							Thread.sleep(SLEEP_TIME);
						} catch (InterruptedException e) {
							// TODO: handle exception
						}
					} catch (Exception e) {
						failedCount++;
						if (failedCount >= MAX_FAILED_COUNT) {
							break;
						}
					}
				} while (true);

				try {
					byte[] downloadContent = alfrescoRestApi.getEmailContent(jd.getDownloadId(), jd.getAuthorization())
							.getBody();
					File dir = new File(mailAttachmentPath);
					if (!dir.exists()) {
						dir.mkdir();
					}
					String filename = jd.getDownloadId();
					File file = new File(mailAttachmentPath + "\\" + filename + ".zip");
					FileOutputStream outputStream = new FileOutputStream(file);
					outputStream.write(downloadContent);
					outputStream.close();
					MimeMessage message = mailSender.createMimeMessage();
					MimeMessageHelper helper = new MimeMessageHelper(message, true);

					helper.setFrom(mailUsername);
					helper.setTo(jd.getRecipient());
					helper.setText(jd.getBody());
					helper.setSubject(jd.getSubject());	
					if(jd.getmailCc() != null) helper.setCc(jd.getmailCc());
					if(jd.getmailBcc() != null) helper.setBcc(jd.getmailBcc());

					helper.addAttachment(filename + ".zip", file);

					mailSender.send(message);

				} catch (Exception e1) {
					// TODO Auto-generated catch blocyk
					e1.printStackTrace();
				} finally {
					cleanAttachment(jd);
				}

			} else {
				try {
					Thread.sleep(SLEEP_TIME);
				} catch (InterruptedException e) {
					// TODO: handle exception
				}
			}
		}
	}

	void cleanAttachment(JobDetails jd) {
		try {
			String filename = jd.getDownloadId();
			File file = new File(mailAttachmentPath + "\\" + filename + ".zip");
			if (file.exists())
				file.delete();
			alfrescoRestApi.cancelDownload(jd.getDownloadId(), jd.getAuthorization());
		} catch (Exception e1) {
		}
	}
}
