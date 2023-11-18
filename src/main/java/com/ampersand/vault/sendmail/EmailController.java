package com.ampersand.vault.sendmail;

import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ampersand.vault.core.document.service.BrowserRestService;
import com.ampersand.vault.core.utilities.entities.DownloadBodyCreate;
import com.ampersand.vault.core.utilities.entities.DownloadEntry;
import com.ampersand.vault.core.utilities.service.AlfrescoRestApi;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.transaction.Transactional;

@RestController
@Transactional
public class EmailController {
	Logger logger = LoggerFactory.getLogger(EmailController.class);

	private AlfrescoRestApi alfrescoRestApi = null;
	private com.ampersand.vault.core.document.service.AlfrescoRestApi browserAlfrescoRestApi = null;
	private BrowserRestService browserRestService = null;
	@Autowired
	private JavaMailSender mailSender;
	@Value("${spring.mail.username}")
	private String mailUsername;
	@Value("${mail.attachment.path}")
	private String mailAttachmentPath;

	public EmailController(@Value("${cmis.server.url}") String serverUrl,
			@Value("${cmis.server.api_url}") String restApiUrl,
			@Value("${cmis.server.repositry}") String repositry, 
			@Value("${cmis.server.browser_url}") String browserUrl,
			@Value("${cmis.server.root_node_path}") String rootNodePath) {
		this.alfrescoRestApi = new AlfrescoRestApi(serverUrl + repositry, restApiUrl);
		this.browserAlfrescoRestApi = new com.ampersand.vault.core.document.service.AlfrescoRestApi( serverUrl + repositry, restApiUrl);
		this.browserRestService = new BrowserRestService(serverUrl + repositry, "", browserUrl, rootNodePath, browserAlfrescoRestApi);
	}

	@Bean
	@Scope("singleton")
	private Scheduler getScheduler() {
		return new Scheduler(alfrescoRestApi, mailSender, mailUsername, mailAttachmentPath);
	}

	@Bean
	@Scope("singleton")
	private DocumentScheduler getDocumentScheduler() {
		return new DocumentScheduler(alfrescoRestApi, browserRestService, mailSender, mailUsername, mailAttachmentPath);
	}

	@RequestMapping("/")
	public String index() {
		return "The server is online";
	}

	@Operation(description = "Send by mail")
	@ResponseStatus(value = HttpStatus.NO_CONTENT)
	@RequestMapping(value = "/send", method = RequestMethod.POST, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<String> send(
			@RequestParam("recipient") String recipient,
			@RequestParam ("body") String body, 
			@RequestParam("Subject") String subject,
			@RequestParam(name = "Cc", required = false) String[] mailCc,
			@RequestParam(name = "Bcc", required = false) String[] mailBcc,
			@RequestParam(name = "attachments", required = false) MultipartFile[] attachments) 
					throws Exception {
		if (attachments == null || attachments.length <= 0) {
			SimpleMailMessage message = new SimpleMailMessage();
			message.setFrom(mailUsername);
			message.setTo(recipient);
			message.setText(body);
			message.setSubject(subject);
			if(mailCc != null) message.setCc(mailCc);
			if(mailBcc != null) message.setBcc(mailBcc);


			mailSender.send(message);
		} else {
			MimeMessage message = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, true);
			helper.setFrom(mailUsername);
			helper.setTo(recipient);
			helper.setText(body);
			helper.setSubject(subject);
			if(mailCc != null) helper.setCc(mailCc);
			if(mailBcc != null) helper.setBcc(mailBcc);

			for (MultipartFile file : attachments) {
				helper.addAttachment(file.getOriginalFilename(), file);
			}

			mailSender.send(message);
		}

		return ResponseEntity.noContent().build();
	}

	@Operation(description = "Share as Attachment")
	@ResponseStatus(value = HttpStatus.NO_CONTENT)
	@RequestMapping(value = "/shareAsAttachment", method = RequestMethod.POST)
	public ResponseEntity<String> shareAsAttachment(
			@RequestParam(value = "folderId") String folderId,
			@RequestParam("recipient") String recipient, 
			@RequestParam("body") String body,
			@RequestParam(name = "Cc", required = false) String[] mailCc,
			@RequestParam(name = "Bcc", required = false) String[] mailBcc,
			@RequestParam("subject") String subject,
			HttpServletRequest request) throws Exception {
		String authorization = request.getHeader("Authorization");

		DownloadBodyCreate downloadBodyCreate = new DownloadBodyCreate();
		downloadBodyCreate.nodeIds = new String[1];
		downloadBodyCreate.nodeIds[0] = folderId;

		ResponseEntity<DownloadEntry> downloadcreated = alfrescoRestApi.createDownload(null, downloadBodyCreate,
				authorization);

		String downloadId = downloadcreated.getBody().entry.id;
		getScheduler().addJob(new JobDetails(downloadId, recipient, body, subject, mailCc,mailBcc, authorization));

		return null;
	}

	@Operation(description = "Share Document as Attachment")
	@ResponseStatus(value = HttpStatus.NO_CONTENT)
	@RequestMapping(value = "/shareDocumentAsAttachment", method = RequestMethod.POST)
	public ResponseEntity<String> shareDocumentAsAttachment(
			@RequestParam(value = "documentId") String documentId,
			@RequestParam("recipient") String recipient, @RequestParam("body") String body,
			@RequestParam(name = "cc", required = false) String[] mailCc,
			@RequestParam(name = "bcc", required = false) String[] mailBcc, @RequestParam("subject") String subject,
			HttpServletRequest request) throws Exception {
		String authorization = request.getHeader("Authorization");

		getDocumentScheduler().addJob(new JobDetails(documentId, recipient, body, subject, mailCc, mailBcc ,authorization));

		return null;
	}
}
