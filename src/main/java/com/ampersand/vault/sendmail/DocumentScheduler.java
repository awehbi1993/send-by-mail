package com.ampersand.vault.sendmail;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import jakarta.mail.internet.MimeMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import com.ampersand.vault.core.document.entities.DownloadBody;
import com.ampersand.vault.core.document.service.BrowserRestService;
import com.ampersand.vault.core.utilities.service.AlfrescoRestApi;

public class DocumentScheduler implements Runnable {
	private Logger logger = LoggerFactory.getLogger(DocumentScheduler.class);

	private BrowserRestService browserRestService;
	private Queue<JobDetails> pending = new ConcurrentLinkedQueue<>();
	private JavaMailSender mailSender;
	private String mailUsername;
	private String mailAttachmentPath;

	private static long SLEEP_TIME = 60 * 1000;

	public DocumentScheduler(AlfrescoRestApi alfrescoRestApi,BrowserRestService browserRestService, JavaMailSender mailSender, String mailUsername,
			String mailAttachmentPath) {
		this.browserRestService = browserRestService;
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
		JobDetails jd;
//		String ext ="";
		String filename = "";
		if ((jd = pending.poll()) != null) {						
			try {
				DownloadBody downloadDoc = browserRestService.downloadDocumentAsAttachment(jd.getDownloadId(), jd.getAuthorization()).getBody();
				
//				ext = downloadDoc.fileExtension;
				
				File dir = new File(mailAttachmentPath);
				if (!dir.exists()) {
					dir.mkdir();
				}

				filename = downloadDoc.name;//jd.getDownloadId().concat(ext) ;
				File file = new File(mailAttachmentPath + "\\" + filename);
				FileOutputStream outputStream = new FileOutputStream(file);
				outputStream.write(downloadDoc.contentBody);
				outputStream.close();
				MimeMessage message = mailSender.createMimeMessage();
				MimeMessageHelper helper = new MimeMessageHelper(message, true);

				helper.setFrom(mailUsername);
				helper.setTo(jd.getRecipient());
				helper.setText(jd.getBody());
				helper.setSubject(jd.getSubject());
				if(jd.getmailCc() != null) helper.setCc(jd.getmailCc());
				if(jd.getmailBcc() != null) helper.setBcc(jd.getmailBcc());

				helper.addAttachment(filename,file);

				mailSender.send(message);
				logger.info(filename);
			} catch (Exception e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
				logger.error(e1.toString());
			} finally {
				cleanAttachment(jd, filename);
			}

		} else {
			try {
				Thread.sleep(SLEEP_TIME);
			} catch (InterruptedException e) {
				// TODO: handle exception
			}
		}		
	}

	void cleanAttachment(JobDetails jd,String filename) {
		try {
//			String filename = jd.getDownloadId().concat(ext);
			File file = new File(mailAttachmentPath + "\\" + filename );
			if (file.exists())
				file.delete();
		} catch (Exception e1) {
		}
	}

}
