package cz.metacentrum.perun.registrar.impl;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import cz.metacentrum.perun.audit.events.AuditEvent;
import cz.metacentrum.perun.audit.events.MailManagerEvents.InvitationSentEvent;
import cz.metacentrum.perun.audit.events.MailManagerEvents.MailForGroupIdAdded;
import cz.metacentrum.perun.audit.events.MailManagerEvents.MailForGroupIdRemoved;
import cz.metacentrum.perun.audit.events.MailManagerEvents.MailForGroupIdUpdated;
import cz.metacentrum.perun.audit.events.MailManagerEvents.MailForVoIdAdded;
import cz.metacentrum.perun.audit.events.MailManagerEvents.MailForVoIdRemoved;
import cz.metacentrum.perun.audit.events.MailManagerEvents.MailForVoIdUpdated;
import cz.metacentrum.perun.audit.events.MailManagerEvents.MailSending;
import cz.metacentrum.perun.audit.events.MailManagerEvents.MailSentForApplication;
import cz.metacentrum.perun.core.api.Attribute;
import cz.metacentrum.perun.core.api.AuthzResolver;
import cz.metacentrum.perun.core.api.BeansUtils;
import cz.metacentrum.perun.core.api.ExtSourcesManager;
import cz.metacentrum.perun.core.api.Group;
import cz.metacentrum.perun.core.api.Member;
import cz.metacentrum.perun.core.api.PerunBean;
import cz.metacentrum.perun.core.api.PerunClient;
import cz.metacentrum.perun.core.api.PerunPrincipal;
import cz.metacentrum.perun.core.api.PerunSession;
import cz.metacentrum.perun.core.api.RichUser;
import cz.metacentrum.perun.core.api.Role;
import cz.metacentrum.perun.core.api.RoleManagementRules;
import cz.metacentrum.perun.core.api.User;
import cz.metacentrum.perun.core.api.Vo;
import cz.metacentrum.perun.core.api.exceptions.AttributeNotExistsException;
import cz.metacentrum.perun.core.api.exceptions.ConsistencyErrorException;
import cz.metacentrum.perun.core.api.exceptions.GroupNotExistsException;
import cz.metacentrum.perun.core.api.exceptions.InternalErrorException;
import cz.metacentrum.perun.core.api.exceptions.InvalidHtmlInputException;
import cz.metacentrum.perun.core.api.exceptions.PerunException;
import cz.metacentrum.perun.core.api.exceptions.PrivilegeException;
import cz.metacentrum.perun.core.api.exceptions.RoleCannotBeManagedException;
import cz.metacentrum.perun.core.api.exceptions.UserNotExistsException;
import cz.metacentrum.perun.core.api.exceptions.VoNotExistsException;
import cz.metacentrum.perun.core.api.exceptions.WrongAttributeAssignmentException;
import cz.metacentrum.perun.core.bl.AttributesManagerBl;
import cz.metacentrum.perun.core.bl.GroupsManagerBl;
import cz.metacentrum.perun.core.bl.MembersManagerBl;
import cz.metacentrum.perun.core.bl.PerunBl;
import cz.metacentrum.perun.core.bl.UsersManagerBl;
import cz.metacentrum.perun.core.blImpl.AuthzResolverBlImpl;
import cz.metacentrum.perun.core.impl.HTMLParser;
import cz.metacentrum.perun.core.impl.PerunAppsConfig;
import cz.metacentrum.perun.core.impl.Utils;
import cz.metacentrum.perun.registrar.MailManager;
import cz.metacentrum.perun.registrar.RegistrarManager;
import cz.metacentrum.perun.registrar.exceptions.ApplicationMailAlreadyRemovedException;
import cz.metacentrum.perun.registrar.exceptions.ApplicationMailExistsException;
import cz.metacentrum.perun.registrar.exceptions.ApplicationMailNotExistsException;
import cz.metacentrum.perun.registrar.exceptions.ApplicationNotNewException;
import cz.metacentrum.perun.registrar.exceptions.FormNotExistsException;
import cz.metacentrum.perun.registrar.exceptions.RegistrarException;
import cz.metacentrum.perun.registrar.model.Application;
import cz.metacentrum.perun.registrar.model.Application.AppType;
import cz.metacentrum.perun.registrar.model.ApplicationForm;
import cz.metacentrum.perun.registrar.model.ApplicationFormItem;
import cz.metacentrum.perun.registrar.model.ApplicationFormItemData;
import cz.metacentrum.perun.registrar.model.ApplicationMail;
import cz.metacentrum.perun.registrar.model.ApplicationMail.MailText;
import cz.metacentrum.perun.registrar.model.ApplicationMail.MailType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcPerunTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.sql.DataSource;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static cz.metacentrum.perun.registrar.impl.RegistrarManagerImpl.URN_GROUP_FROM_EMAIL;
import static cz.metacentrum.perun.registrar.impl.RegistrarManagerImpl.URN_GROUP_FROM_NAME_EMAIL;
import static cz.metacentrum.perun.registrar.impl.RegistrarManagerImpl.URN_GROUP_HTML_MAIL_FOOTER;
import static cz.metacentrum.perun.registrar.impl.RegistrarManagerImpl.URN_GROUP_LANGUAGE_EMAIL;
import static cz.metacentrum.perun.registrar.impl.RegistrarManagerImpl.URN_GROUP_MAIL_FOOTER;
import static cz.metacentrum.perun.registrar.impl.RegistrarManagerImpl.URN_GROUP_REGISTRAR_URL;
import static cz.metacentrum.perun.registrar.impl.RegistrarManagerImpl.URN_GROUP_TO_EMAIL;
import static cz.metacentrum.perun.registrar.impl.RegistrarManagerImpl.URN_VO_FROM_EMAIL;
import static cz.metacentrum.perun.registrar.impl.RegistrarManagerImpl.URN_VO_FROM_NAME_EMAIL;
import static cz.metacentrum.perun.registrar.impl.RegistrarManagerImpl.URN_VO_HTML_MAIL_FOOTER;
import static cz.metacentrum.perun.registrar.impl.RegistrarManagerImpl.URN_VO_LANGUAGE_EMAIL;
import static cz.metacentrum.perun.registrar.impl.RegistrarManagerImpl.URN_VO_MAIL_FOOTER;
import static cz.metacentrum.perun.registrar.impl.RegistrarManagerImpl.URN_VO_REGISTRAR_URL;
import static cz.metacentrum.perun.registrar.impl.RegistrarManagerImpl.URN_VO_TO_EMAIL;

public class MailManagerImpl implements MailManager {

	private static final Logger log = LoggerFactory.getLogger(MailManagerImpl.class);

	private static final String MAILS_SELECT_BY_FORM_ID = "select id,app_type,form_id,mail_type,send from application_mails where form_id=?";
	private static final String MAILS_SELECT_BY_PARAMS = "select id,app_type,form_id,mail_type,send from application_mails where form_id=? and app_type=? and mail_type=?";
	private static final String MAIL_TEXTS_SELECT_BY_MAIL_ID= "select locale,htmlFormat,subject,text from application_mail_texts where mail_id=?";

	static final String URN_USER_PREFERRED_MAIL = "urn:perun:user:attribute-def:def:preferredMail";
	private static final String URN_USER_PHONE = "urn:perun:user:attribute-def:def:phone";
	private static final String URN_USER_PREFERRED_LANGUAGE = "urn:perun:user:attribute-def:def:preferredLanguage";
	private static final String URN_USER_DISPLAY_NAME = "urn:perun:user:attribute-def:core:displayName";
	private static final String URN_USER_LAST_NAME = "urn:perun:user:attribute-def:core:lastName";
	private static final String URN_USER_FIRST_NAME = "urn:perun:user:attribute-def:core:firstName";
	private static final String URN_MEMBER_MAIL = "urn:perun:member:attribute-def:def:mail";
	private static final String URN_MEMBER_EXPIRATION = "urn:perun:member:attribute-def:def:membershipExpiration";
	private static final String URN_MEMBER_PHONE = "urn:perun:member:attribute-def:def:phone";
	private static final String CN = "cn";
	private static final String DISPLAY_NAME = "displayName";

	private static final String EMPTY_STRING = "";
	private static final String LANG_EN = "en";
	private static final String HMAC_SHA256 = "HmacSHA256";

	private static final String FIELD_VO_NAME = "{voName}";
	private static final String FIELD_GROUP_NAME = "{groupName}";
	private static final String FIELD_DISPLAY_NAME = "{displayName}";
	private static final String FIELD_INVITATION_LINK = "{invitationLink}";
	private static final String FIELD_MAIL_FOOTER = "{mailFooter}";
	private static final String FIELD_HTML_MAIL_FOOTER = "{htmlMailFooter}";
	private static final String FIELD_APP_ID = "{appId}";
	private static final String FIELD_ACTOR = "{actor}";
	private static final String FIELD_EXT_SOURCE = "{extSource}";
	private static final String FIELD_CUSTOM_MESSAGE = "{customMessage}";
	private static final String FIELD_AUTO_APPROVE_ERROR = "{autoApproveError}";
	private static final String FIELD_FIRST_NAME = "{firstName}";
	private static final String FIELD_LAST_NAME = "{lastName}";
	private static final String FIELD_ERRORS = "{errors}";
	private static final String FIELD_MEMBERSHIP_EXPIRATION = "{membershipExpiration}";
	private static final String FIELD_MAIL = "{mail}";
	private static final String FIELD_PHONE = "{phone}";
	private static final String FIELD_PERUN_GUI_URL = "{perunGuiUrl}";
	private static final String FIELD_APP_GUI_URL = "{appGuiUrl}";
	private static final String FIELD_APP_DETAIL_URL = "{appDetailUrl}";
	private static final String FIELD_VALIDATION_LINK = "{validationLink}";
	private static final String FIELD_REDIRECT_URL = "{redirectUrl}";

	private static final Pattern FROM_APP_PATTERN = Pattern.compile("\\{fromApp-([^}]+)}");
	private static final String GROUP = "Group";
	private static final String VO = "Vo";

	@Autowired PerunBl perun;
	@Autowired RegistrarManager registrarManager;
	@Autowired private Properties registrarProperties;
	private PerunSession registrarSession;
	private JdbcPerunTemplate jdbc;
	private JavaMailSender mailSender;
	private AttributesManagerBl attrManager;
	private MembersManagerBl membersManager;
	private UsersManagerBl usersManager;
	private GroupsManagerBl groupsManager;

	// Spring setters

	public void setDataSource(DataSource dataSource) {
		this.jdbc =  new JdbcPerunTemplate(dataSource);
		this.jdbc.setQueryTimeout(BeansUtils.getCoreConfig().getQueryTimeout());
	}

	/**
	 * Init method, instantiate PerunSession
	 *
	 */
	protected void initialize() {
		// gets session for a system principal "perunRegistrar"
		final PerunPrincipal pp = new PerunPrincipal("perunRegistrar",
				ExtSourcesManager.EXTSOURCE_NAME_INTERNAL,
				ExtSourcesManager.EXTSOURCE_INTERNAL);

		this.registrarSession = perun.getPerunSession(pp, new PerunClient());
		this.attrManager = perun.getAttributesManagerBl();
		this.membersManager = perun.getMembersManagerBl();
		this.usersManager = perun.getUsersManagerBl();
		this.groupsManager = perun.getGroupsManagerBl();
		this.mailSender = BeansUtils.getDefaultMailSender();
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public Integer addMail(PerunSession sess, ApplicationForm form, ApplicationMail mail) throws ApplicationMailExistsException, PrivilegeException, InvalidHtmlInputException {

		//Authorization
		if (form.getGroup() != null) {
			if (!AuthzResolver.authorizedInternal(sess, "group-addMail_ApplicationForm_ApplicationMail_policy", Arrays.asList(form.getGroup(), form.getVo()))) {
				throw new PrivilegeException(sess, "addMail");
			}
		} else {
			if (!AuthzResolver.authorizedInternal(sess, "vo-addMail_ApplicationForm_ApplicationMail_policy", Collections.singletonList(form.getVo()))) {
				throw new PrivilegeException(sess, "addMail");
			}
		}

		int id = Utils.getNewId(jdbc, "APPLICATION_MAILS_ID_SEQ");
		mail.setId(id);

		try {
			jdbc.update("insert into application_mails(id, form_id, app_type, mail_type, send) values (?,?,?,?,?)",
				mail.getId(), form.getId(), mail.getAppType().toString(), mail.getMailType().toString(), mail.getSend());
		} catch (DuplicateKeyException e) {
			throw new ApplicationMailExistsException("Application mail already exists.", mail);
		}

		for (Locale loc : mail.getMessage().keySet()) {
			try {
				jdbc.update("insert into application_mail_texts(mail_id,locale,htmlFormat,subject,text) values (?,?,?,?,?)",
						mail.getId(), loc.toString(), false, mail.getMessage(loc).getSubject(), mail.getMessage(loc).getText());
			} catch (DuplicateKeyException e) {
				throw new ApplicationMailExistsException("Application mail already exists.", mail);
			}
		}

		for (Locale loc : mail.getHtmlMessage().keySet()) {
			// Check if html mail contains invalid tags
			String inputSubject = mail.getHtmlMessage(loc).getSubject();
			String inputText = mail.getHtmlMessage(loc).getText();

			if (inputSubject != null && inputText != null) {
				// Sanitize HTML (remove unsafe tags, attributes, styles
				HTMLParser parser = new HTMLParser()
					.sanitizeHTML(inputSubject)
					.checkEscapedHTML();
				if (!parser.isInputValid()) {
					throw new InvalidHtmlInputException("HTML Subject contains unsafe HTML tags or styles. Remove them and try again.", parser.getEscaped());
				}
				mail.getHtmlMessage(loc).setSubject(parser.getEscapedHTML());

				HTMLParser parser2 = new HTMLParser()
					.sanitizeHTML(inputText)
					.checkEscapedHTML();
				if (!parser2.isInputValid()) {
					throw new InvalidHtmlInputException("HTML Text contains unsafe HTML tags or styles. Remove them and try again.", parser.getEscaped());
				}
				mail.getHtmlMessage(loc).setText(parser2.getEscapedHTML());
			}

			try {
				jdbc.update("insert into application_mail_texts(mail_id,locale,htmlFormat,subject,text) values (?,?,?,?,?)",
					mail.getId(), loc.toString(), true, mail.getHtmlMessage(loc).getSubject(), mail.getHtmlMessage(loc).getText());
			} catch (DuplicateKeyException e) {
				throw new ApplicationMailExistsException("Application mail already exists.", mail);
			}
		}

		log.info("[MAIL MANAGER] Mail notification definition created: {}", mail);
		if (form.getGroup() != null) {
			perun.getAuditer().log(sess, new MailForGroupIdAdded(mail,form.getGroup()));
		} else {
			perun.getAuditer().log(sess, new MailForVoIdAdded(mail, form.getVo()));
		}

		return id;
	}

	@Override
	public void deleteMailById(PerunSession sess, ApplicationForm form, Integer id) throws ApplicationMailAlreadyRemovedException, PrivilegeException, ApplicationMailNotExistsException {

		//Authorization
		if (form.getGroup() != null) {
			if (!AuthzResolver.authorizedInternal(sess, "group-deleteMailById_ApplicationForm_Integer_policy", Arrays.asList(form.getGroup(), form.getVo()))) {
				throw new PrivilegeException(sess, "deleteMailById");
			}
		} else {
			if (!AuthzResolver.authorizedInternal(sess, "vo-deleteMailById_ApplicationForm_Integer_policy", Collections.singletonList(form.getVo()))) {
				throw new PrivilegeException(sess, "deleteMailById");
			}
		}

		ApplicationMail mail = getMailById(sess, id);

		int result = jdbc.update("delete from application_mails where id=?", id);
		if (result == 0) throw new ApplicationMailAlreadyRemovedException("Mail notification with id="+id+" doesn't exists!");
		if (result == 1) log.info("[MAIL MANAGER] Mail notification with id={} deleted", id);
		if (result > 1) throw new ConsistencyErrorException("There is more than one mail notification with id="+id);

		if (form.getGroup() != null) {
			perun.getAuditer().log(sess, new MailForGroupIdRemoved(mail, form.getGroup()));
		} else {
			perun.getAuditer().log(sess, new MailForVoIdRemoved(mail, form.getVo()));
		}
	}

	@Override
	public ApplicationMail getMailById(PerunSession sess, Integer id) throws ApplicationMailNotExistsException {
		// TODO authz
		ApplicationMail mail;

		// get mail def
		try {
			String query = "select id,app_type,form_id,mail_type,send from application_mails where id=?";
			List<ApplicationMail> mails = jdbc.query(query, (resultSet, arg1) -> new ApplicationMail(
						resultSet.getInt("id"),
						AppType.valueOf(resultSet.getString("app_type")),
						resultSet.getInt("form_id"),
						MailType.valueOf(resultSet.getString("mail_type")),
						resultSet.getBoolean("send")
					), id);
			// set
			if (mails.size() > 1) {
				log.error("[MAIL MANAGER] Wrong number of mail definitions returned by unique params, expected 1 but was: {}.", mails.size());
				throw new ConsistencyErrorException("Wrong number of mail definitions returned by unique params, expected 1 but was: "+mails.size());
			}
			mail = mails.get(0);
		} catch (EmptyResultDataAccessException ex) {
			throw new ApplicationMailNotExistsException("Mail definition with ID="+id+" doesn't exists.");
		}

		List<MailText> texts;
		try {
			texts = jdbc.query(MAIL_TEXTS_SELECT_BY_MAIL_ID, (resultSet, arg1) -> new MailText(
					new Locale(resultSet.getString("locale")),
					resultSet.getBoolean("htmlFormat"),
					resultSet.getString("subject"),
					resultSet.getString("text")), mail.getId()
			);
		} catch (EmptyResultDataAccessException ex) {
			// if no texts it's error
			log.error("[MAIL MANAGER] Mail does not contain any text message.", ex);
			return mail;
		}
		for (MailText text : texts) {
			// fill localized messages
			if (text.getHtmlFormat()) {
				mail.getHtmlMessage().put(text.getLocale(), text);
			} else {
				mail.getMessage().put(text.getLocale(), text);
			}
		}

		return mail;
	}

	@Override
	@Transactional(rollbackFor=Exception.class)
	public void updateMailById(PerunSession sess, ApplicationMail mail) throws FormNotExistsException, ApplicationMailNotExistsException, PrivilegeException, InvalidHtmlInputException {
		ApplicationForm form = registrarManager.getFormById(sess, mail.getFormId());

		//Authorization
		if (form.getGroup() != null) {
			if (!AuthzResolver.authorizedInternal(sess, "group-updateMailById_ApplicationMail_policy", Arrays.asList(form.getGroup(), form.getVo()))) {
				throw new PrivilegeException(sess, "updateMailById");
			}
		} else {
			if (!AuthzResolver.authorizedInternal(sess, "vo-updateMailById_ApplicationMail_policy", Collections.singletonList(form.getVo()))) {
				throw new PrivilegeException(sess, "updateMailById");
			}
		}

		int numberOfExistences = jdbc.queryForInt("select count(1) from application_mails where id=?", mail.getId());
		if (numberOfExistences < 1) throw new ApplicationMailNotExistsException("Application mail does not exist.", mail);
		if (numberOfExistences > 1) throw new ConsistencyErrorException("There is more than one mail with id = " + mail.getId());

		// update sending (enabled / disabled)
		jdbc.update("update application_mails set send=? where id=?", mail.getSend(), mail.getId());

		// update texts (easy way = delete and new insert)
		jdbc.update("delete from application_mail_texts where mail_id=?", mail.getId());

		for (Locale loc : mail.getMessage().keySet()) {
			MailText text = mail.getMessage(loc);
			jdbc.update("insert into application_mail_texts(mail_id,locale,htmlFormat,subject,text) values (?,?,?,?,?)",
					mail.getId(), loc.toString(), false, text.getSubject(), text.getText());
		}

		for (Locale loc : mail.getHtmlMessage().keySet()) {
			MailText htmlMessage = mail.getHtmlMessage(loc);
			// Check if html text contains invalid tags
			String inputSubject = htmlMessage.getSubject();
			String inputText = htmlMessage.getText();

			if (inputSubject != null && inputText != null) {
				// Check if html text contains invalid tags (subject and text)
				HTMLParser parser = new HTMLParser()
					.sanitizeHTML(inputSubject)
					.checkEscapedHTML();
				if (!parser.isInputValid()) {
					throw new InvalidHtmlInputException("HTML Subject contains unsafe HTML tags or styles. Remove them and try again.", parser.getEscaped());
				}
				htmlMessage.setSubject(parser.getEscapedHTML());

				HTMLParser parser2 = new HTMLParser()
                    .sanitizeHTML(inputText)
                    .checkEscapedHTML();
				if (!parser2.isInputValid()) {
					throw new InvalidHtmlInputException("HTML Text contains unsafe HTML tags or styles. Remove them and try again.", parser.getEscaped());
				}
				htmlMessage.setText(parser2.getEscapedHTML());
			}

			jdbc.update("insert into application_mail_texts(mail_id,locale,htmlFormat,subject,text) values (?,?,?,?,?)",
				mail.getId(), loc.toString(), true, htmlMessage.getSubject(), htmlMessage.getText());
		}

		if (form.getGroup() != null) {
			perun.getAuditer().log(sess, new MailForGroupIdUpdated(mail,form.getGroup()));
		} else {
			perun.getAuditer().log(sess, new MailForVoIdUpdated(mail, form.getVo()));
		}
	}

	@Override
	public void setSendingEnabled(PerunSession sess, List<ApplicationMail> mails, boolean enabled) throws ApplicationMailNotExistsException, PrivilegeException, FormNotExistsException {
		if (mails == null) { throw new InternalErrorException("Mails definitions to update can't be null"); }

		for (ApplicationMail mail : mails) {
			ApplicationForm form = registrarManager.getFormById(sess, mail.getFormId());

			//Authorization
			if (form.getGroup() != null) {
				if (!AuthzResolver.authorizedInternal(sess, "group-setSendingEnabled_ApplicationMail_policy", Arrays.asList(form.getGroup(), form.getVo()))) {
					throw new PrivilegeException(sess, "setSendingEnabled");
				}
			} else {
				if (!AuthzResolver.authorizedInternal(sess, "vo-setSendingEnabled_ApplicationMail_policy", Collections.singletonList(form.getVo()))) {
					throw new PrivilegeException(sess, "setSendingEnabled");
				}
			}

			// update sending (enabled / disabled)
			try {
				int existence = jdbc.update("update application_mails set send=? where id=?", enabled, mail.getId());
				if (existence < 1) throw new ApplicationMailNotExistsException("Application mail does not exist.", mail);
			} catch (RuntimeException e) {
				throw new InternalErrorException(e);
			}

			perun.getAuditer().log(sess, new MailSending(mail, enabled));
		}
	}

	@Override
	public List<ApplicationMail> getApplicationMails(PerunSession sess, ApplicationForm form) {
		List<ApplicationMail> mails = jdbc.query(MAILS_SELECT_BY_FORM_ID, (resultSet, arg1) -> new ApplicationMail(resultSet.getInt("id"),
				AppType.valueOf(resultSet.getString("app_type")),
				resultSet.getInt("form_id"), MailType.valueOf(resultSet.getString("mail_type")),
				resultSet.getBoolean("send")), form.getId());
		for (ApplicationMail mail : mails) {
			List<MailText> texts = jdbc.query(MAIL_TEXTS_SELECT_BY_MAIL_ID, (resultSet, arg1) -> new MailText(
					new Locale(resultSet.getString("locale")),
					resultSet.getBoolean("htmlFormat"),
					resultSet.getString("subject"),
					resultSet.getString("text")), mail.getId()
			);
			for (MailText text : texts) {
				// fil localized messages
				if (text.getHtmlFormat()) {
					mail.getHtmlMessage().put(text.getLocale(), text);
				} else {
					mail.getMessage().put(text.getLocale(), text);
				}
			}
		}

		return mails;
	}

	@Override
	public void copyMailsFromVoToVo(PerunSession sess, Vo fromVo, Vo toVo) throws PerunException {
		perun.getVosManagerBl().checkVoExists(sess, fromVo);
		perun.getVosManagerBl().checkVoExists(sess, toVo);

		//Authorization
		if (!AuthzResolver.authorizedInternal(sess, "source-copyMailsFromVoToVo_Vo_Vo_policy", fromVo) ||
			!AuthzResolver.authorizedInternal(sess, "destination-copyMailsFromVoToVo_Vo_Vo_policy", toVo)) {
			throw new PrivilegeException(sess, "copyMailsFromVoToVo");
		}

		ApplicationForm formFrom = registrarManager.getFormForVo(fromVo);
		ApplicationForm formTo = registrarManager.getFormForVo(toVo);
		copyApplicationMails(sess, formFrom, formTo);
	}

	@Override
	public void copyMailsFromVoToGroup(PerunSession sess, Vo fromVo, Group toGroup, boolean reverse) throws PerunException {
		perun.getVosManagerBl().checkVoExists(sess, fromVo);
		perun.getGroupsManagerBl().checkGroupExists(sess, toGroup);


		if (reverse) {
			//Authorization
			if (!AuthzResolver.authorizedInternal(sess, "source-copyMailsFromVoToGroup_Vo_Group_boolean_policy", toGroup) ||
				!AuthzResolver.authorizedInternal(sess, "destination-copyMailsFromVoToGroup_Vo_Group_boolean_policy", fromVo)) {
				throw new PrivilegeException(sess, "copyMailsFromVoToGroup");
			}
			// copy notifications from Group to VO
			ApplicationForm voForm = registrarManager.getFormForVo(fromVo);
			ApplicationForm groupForm = registrarManager.getFormForGroup(toGroup);
			copyApplicationMails(sess, groupForm, voForm);
		} else {
			//Authorization
			if (!AuthzResolver.authorizedInternal(sess, "source-copyMailsFromVoToGroup_Vo_Group_boolean_policy", fromVo) ||
				!AuthzResolver.authorizedInternal(sess, "destination-copyMailsFromVoToGroup_Vo_Group_boolean_policy", toGroup)) {
				throw new PrivilegeException(sess, "copyMailsFromVoToGroup");
			}
			// copy notifications from VO to Group
			ApplicationForm voForm = registrarManager.getFormForVo(fromVo);
			ApplicationForm groupForm = registrarManager.getFormForGroup(toGroup);
			copyApplicationMails(sess, voForm, groupForm);
		}
	}

	@Override
	public void copyMailsFromGroupToGroup(PerunSession sess, Group fromGroup, Group toGroup) throws PerunException {
		perun.getGroupsManagerBl().checkGroupExists(sess, fromGroup);
		perun.getGroupsManagerBl().checkGroupExists(sess, toGroup);

		//Authorization
		if (!AuthzResolver.authorizedInternal(sess, "source-copyMailsFromGroupToGroup_Group_Group_policy", fromGroup) ||
			!AuthzResolver.authorizedInternal(sess, "destination-copyMailsFromGroupToGroup_Group_Group_policy", toGroup)) {
			throw new PrivilegeException(sess, "copyMailsFromGroupToGroup");
		}

		ApplicationForm formFrom = registrarManager.getFormForGroup(fromGroup);
		ApplicationForm formTo = registrarManager.getFormForGroup(toGroup);
		copyApplicationMails(sess, formFrom, formTo);
	}

	@Override
	public void sendMessage(Application app, MailType mailType, String reason, List<Exception> exceptions) {
		try {
			// get form
			ApplicationForm form = getForm(app);

			// get mail definition
			ApplicationMail mail = getMailByParams(form.getId(), app.getType(), mailType);
			if (mail == null) {
				log.error("[MAIL MANAGER] Mail not sent. Definition (or mail text) for: {} do not exists for " +
						"VO: {} and Group: {}", mailType.toString(), app.getVo(), app.getGroup());
				return; // mail not found
			} else if (!mail.getSend()) {
				log.info("[MAIL MANAGER] Mail not sent. Disabled by VO admin for: {} / appID: {} / {} / {}"
						, mail.getMailType(), app.getId(), app.getVo(), app.getGroup());
				return; // sending this mail has been disabled by VO admin
			}
			// get app data
			List<ApplicationFormItemData> data = registrarManager.getApplicationDataById(registrarSession, app.getId());

			// different behavior based on mail type
			switch (mail.getMailType()) {
				case APP_CREATED_USER:
				case APPROVABLE_GROUP_APP_USER:
				case APP_APPROVED_USER:
				case APP_REJECTED_USER:
					sendUserMessage(app, mail, data, reason, exceptions, mail.getMailType());
					break;
				case MAIL_VALIDATION:
					mailValidation(app, mail, data, reason, exceptions);
					break;
				case APP_CREATED_VO_ADMIN:
					appCreatedVoAdmin(app, mail, data, reason, exceptions);
					break;
				case APP_ERROR_VO_ADMIN:
					appErrorVoAdmin(app, mail, data, reason, exceptions);
					break;
				default:
					log.error("[MAIL MANAGER] Sending mail type: {} is not supported.", mail.getMailType());
					break;
			}
		} catch (Exception ex) {
			// catch all exceptions and log to: perun-registrar.log
			log.error("[MAIL MANAGER] Exception thrown when sending email.", ex);
		}
	}

	@Override
	public void sendMessage(PerunSession sess, Application app, MailType mailType, String reason) throws PerunException {
		if (MailType.USER_INVITE.equals(mailType)) {
			throw new RegistrarException("USER_INVITE notification can't be sent this way. Use sendInvitation() instead.");
		}

		//Authorization
		if (app.getGroup() != null) {
			if (!AuthzResolver.selfAuthorizedForApplication(sess, app) &&
				!AuthzResolver.authorizedInternal(sess, "group-sendMessage_Application_MailType_String_policy", Arrays.asList(app.getGroup(), app.getVo()))) {
				throw new PrivilegeException(sess, "sendMessage");
			}
		} else {
			if (!AuthzResolver.selfAuthorizedForApplication(sess, app) &&
				!AuthzResolver.authorizedInternal(sess, "vo-sendMessage_Application_MailType_String_policy", Collections.singletonList(app.getVo()))) {
				throw new PrivilegeException(sess, "sendMessage");
			}
		}

		ApplicationForm form = getForm(app);

		ApplicationMail mail = getMailByParams(form.getId(), app.getType(), mailType);
		if (mail == null) throw new RegistrarException("Notification template for "+mailType+" for application " + app.getId() + " is not defined.");
		if (!mail.getSend()) throw new RegistrarException("Sending of notification "+mailType+" for application " + app.getId() + " is disabled.");

		if (!(AuthzResolver.hasRole(sess.getPerunPrincipal(), Role.PERUNADMIN) || AuthzResolver.hasRole(sess.getPerunPrincipal(), Role.PERUNADMINBA))) {
			if (MailType.APP_ERROR_VO_ADMIN.equals(mailType)) {
				throw new RegistrarException("APP_ERROR_VO_ADMIN notification can't be sent this way, since it's bound to each approval process. Try to approve application " + app.getId() + "  once again to receive this message.");
			}

			switch (mailType) {
				case APP_CREATED_USER:
				case APPROVABLE_GROUP_APP_USER:
				case APP_CREATED_VO_ADMIN: {
					if (app.getState().equals(Application.AppState.NEW) || app.getState().equals(Application.AppState.VERIFIED)) {
						sendMessage(app, mailType, null, null);
					} else {
						throw new RegistrarException("Application " + app.getId() + " must be in state NEW or VERIFIED to allow sending of "+mailType+" notification.");
					}
				} break;
				case MAIL_VALIDATION: {
					if (app.getState().equals(Application.AppState.NEW)) {
						sendMessage(app, mailType, null, null);
					} else {
						throw new ApplicationNotNewException("Application " + app.getId() + " must be in state NEW to allow sending of "+mailType+" notification.", app.getState().toString());
					}
				} break;
				case APP_APPROVED_USER: {
					if (Application.AppState.APPROVED.equals(app.getState())) {
						sendMessage(app, mailType, null, null);
					} else {
						throw new RegistrarException("Application " + app.getId() + " must be in state APPROVED to allow sending of "+mailType+" notification.");
					}
				} break;
				case APP_REJECTED_USER: {
					if (Application.AppState.REJECTED.equals(app.getState())) {
						sendMessage(app, mailType, reason, null);
					} else {
						throw new RegistrarException("Application " + app.getId() + " must be in state REJECTED to allow sending of "+mailType+" notification.");
					}
				} break;
			}
		} else {
			// perun admin can always be sent any message with an exception to the USER_INVITE
			sendMessage(app, mailType, reason, null);
		}
		perun.getAuditer().log(sess, new MailSentForApplication(mailType, app.getId()));
	}

	@Override
	public void sendMessages(PerunSession sess, List<Application> applications, MailType mailType, String reason) throws PerunException {
		for (Application application : applications) {
			sendMessage(sess, application, mailType, reason);
		}
	}

	@Override
	public void sendInvitation(PerunSession sess, Vo vo, Group group, String name, String email, String language) throws PerunException {
		perun.getVosManagerBl().checkVoExists(sess, vo);

		if (email == null || email.isEmpty()) {
			throw new RegistrarException("You must provide non-empty email of person you are inviting.");
		}

		//Authorization
		if (group != null) {
			perun.getGroupsManagerBl().checkGroupExists(sess, group);
			if (!AuthzResolver.authorizedInternal(sess, "group-sendInvitation_Vo_Group_String_String_String_policy", Arrays.asList(vo, group))) {
				throw new PrivilegeException(sess, "sendInvitation");
			}
		} else {
			if (!AuthzResolver.authorizedInternal(sess, "vo-sendInvitation_Vo_Group_String_String_String_policy", Collections.singletonList(vo))) {
				throw new PrivilegeException(sess, "sendInvitation");
			}
		}

		Application app = getFakeApplication(vo, group);
		MimeMessage message;
		try {
			message = getInvitationMessage(vo, group, language, email, app, name, null);
		} catch (MessagingException e) {
			throw new RegistrarException("[MAIL MANAGER] Exception thrown when getting invitation message", e);
		}

		sendInvitationMail(sess, vo, group, email, language, message, app);
	}

	@Override
	public Map<String, String> sendInvitationsFromCsv(PerunSession sess, Vo vo, Group group, List<String> data, String language) throws GroupNotExistsException, PrivilegeException, VoNotExistsException, RegistrarException {
		perun.getVosManagerBl().checkVoExists(sess, vo);
		Map<String, String> result = new HashMap<>();

		//Authorization
		if (group != null) {
			perun.getGroupsManagerBl().checkGroupExists(sess, group);
			if (!AuthzResolver.authorizedInternal(sess, "group-sendInvitation_Vo_Group_String_String_String_policy", Arrays.asList(vo, group))) {
				throw new PrivilegeException(sess, "sendInvitation");
			}
		} else {
			if (!AuthzResolver.authorizedInternal(sess, "vo-sendInvitation_Vo_Group_String_String_String_policy", Collections.singletonList(vo))) {
				throw new PrivilegeException(sess, "sendInvitation");
			}
		}

		// Fail fast checks
		List<List<String>> parsedFile;
		try {
			parsedFile = parseInvitationCsv(data);
			getForm(vo, group);
		} catch (IOException e) {
			throw new RegistrarException("[MAIL MANAGER] Invalid csv format", e);
		} catch (FormNotExistsException e) {
			throw new RegistrarException("[MAIL MANAGER] Form does not exist", e);
		}
		// trim all values of leading/trailing white spaces
		parsedFile = parsedFile.stream().map((list) -> list.stream().map(String::trim).toList()).toList();
		checkInvitationData(parsedFile);


		for (List<String> parsedRow : parsedFile) {
			if (parsedRow.size() == 0) {
				continue;
			}

			String email = parsedRow.get(0);
			String name = parsedRow.size() == 2 ? parsedRow.get(1) : null;

			Application app = getFakeApplication(vo, group);
			MimeMessage message;
			try {
				message = getInvitationMessage(vo, group, language, email, app, name, null);
				sendInvitationMail(sess, vo, group, email, language, message, app);
				result.put(email, "OK");
			} catch (MessagingException | RegistrarException | FormNotExistsException e) {
				result.put(email, "ERROR: " + e);
			}
		}

		return result;
	}

	/**
	 * Parse semicolon ';' separated String into list of Strings
	 * @param data list of row data
	 * @return list of list of row values
	 */
	private List<List<String>> parseInvitationCsv(List<String> data) throws IOException {
		CsvMapper csvMapper = new CsvMapper();
		csvMapper.enable(CsvParser.Feature.WRAP_AS_ARRAY);
		MappingIterator<List<String>> rows = csvMapper.readerFor(List.class)
			.with(CsvSchema.emptySchema().withColumnSeparator(';'))
			.readValues(String.join("\n", data));
		return rows.readAll();
	}

	/**
	 * Checks format of data and throws exception if invalid
	 * @param parsedFile
	 * @throws RegistrarException- if parsed file contains invalid values
	 */
	private void checkInvitationData(List<List<String>> parsedFile) throws RegistrarException {
		Set<String> emails = new HashSet<>();
		for (List<String> parsedRow : parsedFile) {
			if (parsedRow.size() == 0) {
				continue;
			}

			if (parsedRow.size() > 2) {
				throw new RegistrarException("ERROR: Too many values: " + parsedRow);
			}

			String email = parsedRow.get(0);
			if (!Utils.emailPattern.matcher(email).matches()) {
				throw new RegistrarException("ERROR: Invalid email: " + email);
			}

			if (!emails.add(email)) {
				throw new RegistrarException("ERROR: duplicated email found: " + email);
			}
		}
	}

	@Override
	public void sendInvitation(PerunSession sess, Vo vo, Group group, User user) throws PerunException {
		perun.getVosManagerBl().checkVoExists(sess, vo);
		if (user == null) throw new RegistrarException("Missing user to send notification to.");

		//Authorization
		if (group != null) {
			perun.getGroupsManagerBl().checkGroupExists(sess, group);
			if (!AuthzResolver.authorizedInternal(sess, "group-sendInvitation_Vo_Group_User_policy", Arrays.asList(vo, group, user))) {
				throw new PrivilegeException(sess, "sendInvitation");
			}
		} else {
			if (!AuthzResolver.authorizedInternal(sess, "vo-sendInvitation_Vo_Group_User_policy", Arrays.asList(vo, user))) {
				throw new PrivilegeException(sess, "sendInvitation");
			}
		}

		try {
			Member m = membersManager.getMemberByUser(sess, vo, user);
			// is a member, is invited to group ?
			if (group != null) {
				List<Group> g = groupsManager.getMemberGroups(sess, m);
				if (g.contains(group)) {
					// user is member of group - can't invite him
					throw new RegistrarException("User to invite is already member of your group: "+group.getShortName());
				}
			} else {
				throw new RegistrarException("User to invite is already member of your VO:"+vo.getShortName());
			}
		} catch (Exception ex) {
			log.error("[MAIL MANAGER] Exception throw when getting member by {} from {}: {}", user, vo.toString(), ex);
		}

		String email = EMPTY_STRING;
		try {
			Attribute a = attrManager.getAttribute(registrarSession, user, URN_USER_PREFERRED_MAIL);
			if (a != null && a.getValue() != null) {
				email = BeansUtils.attributeValueToString(a);
			}
		} catch (Exception ex) {
			log.error("[MAIL MANAGER] Exception thrown when getting preferred language of notification for Group={}: {}", group, ex);
		}

		String language = LANG_EN;
		language = getLanguageForUser(user, language);

		Application app = getFakeApplication(vo, group);

		MimeMessage message = null;
		try {
			message = getInvitationMessage(vo, group, language, email, app, null, user);
		} catch (MessagingException e) {
			throw new RegistrarException("[MAIL MANAGER] Exception thrown when getting invitation message", e);
		}

		sendInvitationMail(sess, vo, group, email, language, message, app);
	}

	@Override
	public String getMessageAuthenticationCode(String input) {
		if (input == null) throw new NullPointerException("input must not be null");
		try {
			Mac mac = Mac.getInstance(HMAC_SHA256);
			SecretKeySpec keySpec = new SecretKeySpec(getPropertyFromConfiguration("secretKey")
				.getBytes(StandardCharsets.UTF_8),HMAC_SHA256);
			mac.init(keySpec);
			byte[] macbytes = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
			return new BigInteger(macbytes).toString(Character.MAX_RADIX);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Gets particular property from perun-registrar-lib.properties file.
	 *
	 * @param propertyName name of the property
	 * @return value of the property
	 */
	@Override
	public String getPropertyFromConfiguration(String propertyName) {
		if (propertyName != null) {
			try {
				String result = registrarProperties.getProperty(propertyName);
				return result != null ? result : EMPTY_STRING;
			} catch (Exception e) {
				log.error("[MAIL MANAGER] Exception when searching through perun-registrar-lib.properties file", e);
			}
		}

		return EMPTY_STRING;
	}

	@Override
	public Boolean invitationFormExists(PerunSession sess, Vo vo, Group group) throws VoNotExistsException, GroupNotExistsException {
		Utils.checkPerunSession(sess);

		perun.getVosManagerBl().checkVoExists(sess, vo);
		if (group != null) {
			perun.getGroupsManagerBl().checkGroupExists(sess, group);
		}

		try {
			ApplicationForm form = getForm(vo, group);
			getMail(form, AppType.INITIAL, MailType.USER_INVITE);
		} catch (FormNotExistsException | RegistrarException e) {
			return false;
		}

		return true;
	}

	public Boolean isInvitationEnabled(PerunSession sess, Vo vo, Group group) throws PerunException {
		// check that invitation form and invitation notification exist
		if (!invitationFormExists(sess, vo, group)) {
			return false;
		}

		// check that invitation form can be submitted
		ApplicationForm form = getForm(vo, group);
		List<ApplicationFormItem> applicationItems = registrarManager.getFormItems(sess, form, AppType.INITIAL);
		return applicationItems.stream().anyMatch(item -> item.getType().equals(ApplicationFormItem.Type.AUTO_SUBMIT_BUTTON) || item.getType().equals(ApplicationFormItem.Type.SUBMIT_BUTTON));
	}

	/**
	 * Retrieve mail definition from db by params.
	 * Mail contains all texts.
	 * If mail not exists, or no texts exists null is returned.
	 *
	 * @param formId relation to VO form
	 * @param appType application type
	 * @param mailType mail type
	 * @return mail if definition exists or null
	 */
	private ApplicationMail getMailByParams(Integer formId, AppType appType, MailType mailType) {
		ApplicationMail mail;

		// We want to use the initial notifications for the embedded applications
		if (appType == AppType.EMBEDDED) {
			appType = AppType.INITIAL;
		}

		// get mail def
		try {
			List<ApplicationMail> mails = jdbc.query(MAILS_SELECT_BY_PARAMS, (resultSet, arg1) -> new ApplicationMail(resultSet.getInt("id"),
					AppType.valueOf(resultSet.getString("app_type")),
					resultSet.getInt("form_id"), MailType.valueOf(resultSet.getString("mail_type")),
					resultSet.getBoolean("send")), formId, appType.toString(), mailType.toString());
			// set
			if (mails.size() != 1) {
				log.error("[MAIL MANAGER] Wrong number of mail definitions returned by unique params, expected 1 but was: {}", mails.size());
				return null;
			}
			mail = mails.get(0);
		} catch (EmptyResultDataAccessException ex) {
			return null;
		}

		List<MailText> texts;
		try {
			texts = jdbc.query(MAIL_TEXTS_SELECT_BY_MAIL_ID, (resultSet, arg1) -> new MailText(new Locale(resultSet.getString("locale")), resultSet.getBoolean("htmlFormat"),resultSet.getString("subject"), resultSet.getString("text")), mail.getId());
		} catch (EmptyResultDataAccessException ex) {
			// if no texts it's error"HmacSHA256"
			log.error("[MAIL MANAGER] Mail do not contains any text message.", ex);
			return null;
		}

		for (MailText text : texts) {
			// fil localized messages
			if (text.getHtmlFormat()) {
				mail.getHtmlMessage().put(text.getLocale(), text);
			} else {
				mail.getMessage().put(text.getLocale(), text);
			}
		}

		return mail;
	}

	/**
	 * Return preferred Locale from application
	 * (return EN if not found)
	 *
	 * @param data application data
	 * @return Preferred locale resolved from application data
	 */
	private String getLanguageFromAppData(Application app, List<ApplicationFormItemData> data) {
		String language = LANG_EN;

		// if user present - get preferred language
		if (app.getUser() != null) {
			try {
				User u = usersManager.getUserById(registrarSession, app.getUser().getId());
				Attribute a = attrManager.getAttribute(registrarSession, u, URN_USER_PREFERRED_LANGUAGE);
				if (a != null && a.getValue() != null) {
					language = BeansUtils.attributeValueToString(a);
				}
			} catch (Exception ex) {
				log.error("[MAIL MANAGER] Exception thrown when getting preferred language for {}: {}", app.getUser(), ex);
			}
		}

		// if preferred language specified on application - rewrite
		for (ApplicationFormItemData item : data) {
			if (item.getFormItem() != null) {
				if (URN_USER_PREFERRED_LANGUAGE.equalsIgnoreCase(item.getFormItem().getPerunDestinationAttribute())) {
					if (item.getValue() == null || item.getValue().isEmpty()) {
						return language; // return default
					} else {
						return item.getValue(); // or return value
					}
				}
			}
		}
		// return default
		return language;
	}

	/**
	 * Set users mail as TO param for mail message.
	 *
	 * Default value is empty (mail won't be sent).
	 *
	 * Mail is taken from first founded form item of type VALIDATED_MAIL.
	 * If none found and user exists, it's taken from
	 * user's attribute: preferredMail
	 *
	 * @param message message to set TO param
	 * @param app application
	 * @param data application data
	 */
	private void setUsersMailAsTo(MimeMessage message, Application app, List<ApplicationFormItemData> data) throws MessagingException {
		setRecipient(message, null);

		try {
			// get TO param from VALIDATED_EMAIL form items (it's best fit)
			for (ApplicationFormItemData d : data) {
				ApplicationFormItem item = d.getFormItem();
				String value = d.getValue();
				if (ApplicationFormItem.Type.VALIDATED_EMAIL.equals(item.getType())) {
					if (value != null && !value.isEmpty()) {
						setRecipient(message, value);
						return;// use first mail address
					}
				}
			}
			// get TO param from other form items related to "user - preferredMail"
			for (ApplicationFormItemData d : data) {
				ApplicationFormItem item = d.getFormItem();
				String value = d.getValue();
				if (URN_USER_PREFERRED_MAIL.equalsIgnoreCase(item.getPerunDestinationAttribute())) {
					if (value != null && !value.isEmpty()) {
						setRecipient(message, value);
						return;// use first mail address
					}
				}
			}
			// get TO param from other form items related to "member - mail"
			for (ApplicationFormItemData d : data) {
				ApplicationFormItem item = d.getFormItem();
				String value = d.getValue();
				if (URN_MEMBER_MAIL.equalsIgnoreCase(item.getPerunDestinationAttribute())) {
					if (value != null && !value.isEmpty()) {
						setRecipient(message, value);
						return;// use first mail address
					}
				}
			}
			// get TO param from user if not present on application form
			if (app.getUser() != null) {
				User u = usersManager.getUserById(registrarSession, app.getUser().getId());
				Attribute a = attrManager.getAttribute(registrarSession, u, URN_USER_PREFERRED_MAIL);
				if (a != null && a.getValue() != null) {
					String possibleTo = BeansUtils.attributeValueToString(a);
					if (possibleTo != null && !possibleTo.trim().isEmpty()) {
						setRecipient(message, possibleTo);
					}
				}
			}
		} catch (Exception ex) {
			// we don't care about exceptions - we have backup address (empty = mail not sent)
			log.error("[MAIL MANAGER] Exception thrown when getting users mail address for application: {}", app);
		}
	}

	/**
	 * Sets proper values "FROM" and "REPLY-TO" to mail message.
	 * FROM is constant per perun instance, REPLY-TO can be modified by "fromEmail" attribute of VO or GROUP.
	 *
	 * If group attribute not set and is group application, get vo attribute as backup.
	 * If any attribute not set, BACKUP_FROM address is used.
	 *
	 * @param message message to set param FROM
	 * @param app application to decide if it's VO or Group application
	 */
	private void setFromMailAddress(MimeMessage message, Application app) {
		String fromMail = getPropertyFromConfiguration("backupFrom");
		String fromName = getPropertyFromConfiguration("backupFromName");
		String replyToMail = fromMail;
		String replyToName = fromName;

		if (!Objects.equals(getPropertyFromConfiguration("replyTo"), EMPTY_STRING)) {
			replyToMail = getPropertyFromConfiguration("replyTo");
			replyToName = getPropertyFromConfiguration("replyToName");
		}

		// get proper value from attribute
		try {
			Attribute attrSenderName = getMailFromVoAndGroupAttrs(app, URN_VO_FROM_NAME_EMAIL, URN_GROUP_FROM_NAME_EMAIL);
			Attribute attrSenderEmail = getMailFromVoAndGroupAttrs(app, URN_VO_FROM_EMAIL, URN_GROUP_FROM_EMAIL);

			if (attrSenderName != null && attrSenderName.getValue() != null) {
				String possibleReplyTo = BeansUtils.attributeValueToString(attrSenderName);
				if (possibleReplyTo != null && !possibleReplyTo.trim().isEmpty()) {
					replyToName = possibleReplyTo;
				}
			}

			if (attrSenderEmail != null && attrSenderEmail.getValue() != null) {
				String possibleReplyToMail = BeansUtils.attributeValueToString(attrSenderEmail);
				if (possibleReplyToMail != null && !possibleReplyToMail.trim().isEmpty()) {
					if (possibleReplyToMail.contains("\"")) {
						// "name whatever" <mail@server.com>
						String[] parts = possibleReplyToMail.split("\\s<");
						if (parts.length != 2) throw new InternalErrorException("Failed to parse complex mail address for reply-to: "+possibleReplyToMail);
						replyToName = parts[0].replace("\"", "");
						replyToMail = parts[1].replace(">", "");
					} else {
						replyToMail = possibleReplyToMail;
					}
				}
			}
			setFromAndReplyTo(message, fromName, fromMail, replyToName, replyToMail);
		} catch (Exception ex) {
			// we don't care about exceptions here - we have backup TO/FROM address
			if (app.getGroup() == null) {
				log.error("[MAIL MANAGER] Exception thrown when getting FROM email from an attribute {}. Ex: {}", URN_VO_FROM_EMAIL, ex);
			} else {
				log.error("[MAIL MANAGER] Exception thrown when getting FROM email from an attribute {}. Ex: {}", URN_GROUP_FROM_EMAIL, ex);
			}
		}
	}

	private void setFromAndReplyTo(MimeMessage message, String fromName, String fromMail, String replyToName, String replayToMail) throws UnsupportedEncodingException, MessagingException {
		if (fromName != null) {
			message.setFrom(new InternetAddress(fromMail, fromName));
		} else {
			message.setFrom(new InternetAddress(fromMail));
		}
		if (replyToName != null) {
			message.setReplyTo(new InternetAddress[]{new InternetAddress(replayToMail, replyToName)});
		} else {
			message.setReplyTo(new InternetAddress[]{new InternetAddress(replayToMail)});
		}
	}

	/**
	 * Get proper values "TO" for mail message based on VO or GROUP attribute "toEmail".
	 * <p>
	 * If group attribute is not set and Application is a group application, notify all corresponding group admins
	 * (admin roles configured to receive GROUP notifications).
	 * In case no such admins are set, try the same procedure on parent group (if exists)
	 * When reaches the top-level group and no recipients have been identified, repeat the same procedure on VO level (without cascading).
	 * When no recipient is identified on the VO level or some error happens, BACKUP_TO address will be used.
	 *
	 * @param app application to decide if it's VO or Group application
	 * @return Set of mail addresses to send mail to
	 */
	private Set<String> getToMailAddresses(Application app) {
		Set<String> result = new HashSet<>();
		try {
			// Check group and its parent group hierarchy first
			Group currentGroup = app.getGroup();
			while (currentGroup != null) {
				result = getToEmailsGroupLevel(currentGroup);
				if (!result.isEmpty()) {
					break;
				}
				currentGroup = getParentGroupForMailAddresses(currentGroup);
			}
			// VO application or fallback to VO level
			if (app.getGroup() == null || result.isEmpty()) {
				result = getToEmailsVoLevel(app.getVo());
			}
		} catch (Exception ex) {
			// we don't care about exceptions here - we have backup TO/FROM address
			log.error("[MAIL MANAGER] Exception thrown when getting TO email from an attribute {}. Ex: {}",
					app.getGroup() == null ? URN_VO_TO_EMAIL : URN_GROUP_TO_EMAIL, ex);
			// set backup
			result = new HashSet<>();
			result.add(getPropertyFromConfiguration("backupTo"));
		}
		return result;
	}

	private Group getParentGroupForMailAddresses(Group group) {
		if (group != null && group.getParentGroupId() != null) {
			try {
				return groupsManager.getGroupById(registrarSession, group.getParentGroupId());
			} catch (GroupNotExistsException e) {
				log.error("[MAIL MANAGER] Inconsistency detected - parent (in hierarchy) group with ID '{}' of group '{}' does not exist",
					group.getParentGroupId(), group);
			}
		}
		return null;
	}

	/**
	 * Returns list of roles, which should be notified for object's notification type
	 * @return list of role names
	 */
	private List<String> getNotificationReceiverRoles(String object) {
		List<RoleManagementRules> allManagementRules = AuthzResolver.getAllRolesManagementRules();
		return allManagementRules.stream().filter(r -> r.getReceiveNotifications().contains(object)).map(RoleManagementRules::getRoleName).toList();
	}

	/**
	 * Substitute common strings in mail text for INVITATIONS based on passed params
	 *
	 * Substituted strings are:
	 *
	 * {voName} - full vo name
	 * {groupName} - group short name
	 * {displayName} - users display name returned from federation
	 *
	 * {invitationLink-[authz]} - link to VO's/group's application form
	 * {appGuiUrl-[authz]} - url to application GUI for user to see applications state
	 * {perunGuiUrl-[authz]} - url to perun GUI (user detail)
	 *
	 * {mailFooter} - common VO's footer
	 * {htmlMailFooter} - common VO's HTML footer
	 *
	 * @param vo vo this template belongs to
	 * @param group group this template belongs to
	 * @param user User to get name from, if null, param 'name' is used instead.
	 * @param name An optional name of user (for anonymous, used when user==null).
	 * @param mailText Original mail text template
	 * @param isAlternativePlainText if the text will be used as alternative plain text to an HTML text
	 */
	private String substituteCommonStringsForInvite(Vo vo, Group group, User user, String name, String mailText, boolean isAlternativePlainText) {
		// replace voName
		if (mailText.contains(FIELD_VO_NAME)) {
			mailText = replaceNullSafe(mailText, FIELD_VO_NAME, vo.getName());
		}

		// replace groupName
		if (mailText.contains(FIELD_GROUP_NAME)) {
			if (group != null) {
				mailText = replaceNullSafe(mailText, FIELD_GROUP_NAME, group.getShortName());
			} else {
				mailText = replaceNullSafe(mailText, FIELD_GROUP_NAME, EMPTY_STRING);
			}
		}

		// replace name of user
		if (mailText.contains(FIELD_DISPLAY_NAME)) {
			if (user != null) {
				mailText = replaceNullSafe(mailText, FIELD_DISPLAY_NAME, user.getDisplayName());
			} else if (name != null && !name.isEmpty()) {
				mailText = replaceNullSafe(mailText, FIELD_DISPLAY_NAME, name);
			} else {
				mailText = replaceNullSafe(mailText, FIELD_DISPLAY_NAME, EMPTY_STRING);
			}
		}

		// replace invitation link
		if (mailText.contains(FIELD_INVITATION_LINK)) {
			mailText = replaceNullSafe(mailText, FIELD_INVITATION_LINK, buildInviteURL(vo, group));
		}

		// replace invitation link
		if (mailText.contains("{invitationLink-")) {
			Pattern pattern = Pattern.compile("\\{invitationLink-[^}]+}");
			Matcher m = pattern.matcher(mailText);
			while (m.find()) {
				// whole "{invitationLink-something}"
				String toSubstitute = m.group(0);

				// new login value to replace in text
				String newValue = EMPTY_STRING;

				Pattern namespacePattern = Pattern.compile("-(.*?)}");
				Matcher m2 = namespacePattern.matcher(toSubstitute);
				while (m2.find()) {
					// only namespace "fed", "cert",...
					String namespace = m2.group(1);
					newValue = buildInviteURL(vo, group, namespace);
				}
				// substitute {invitationLink-authz} with actual value or empty string
				mailText = replaceNullSafe(mailText, toSubstitute, newValue);
			}
		}

		mailText = replacePerunGuiUrl(mailText, vo, group);
		mailText = replaceAppGuiUrl(mailText, vo, group);

		mailText = replaceFooter(FIELD_MAIL_FOOTER, vo, group, mailText, true);
		mailText = replaceFooter(FIELD_HTML_MAIL_FOOTER, vo, group, mailText, isAlternativePlainText);

		return mailText;
	}

	/**
	 * Replaces the footerTag in the mailText.
	 *
	 * @param footerTag either {mailFooter} or {htmlMailFooter}
	 * @param vo
	 * @param group
	 * @param mailText
	 * @param onlyPlainTextFooter if the footer must be plain text (if {htmlMailFooter} is used and
	 *                            onlyPlainTextFooter is true, then plain text footer is used instead of the HTML footer)
	 * @return
	 */
	private String replaceFooter(String footerTag, Vo vo, Group group, String mailText, boolean onlyPlainTextFooter) {
		if (mailText.contains(footerTag)) {
			String footer = EMPTY_STRING;
			// get proper value from attribute
			try {
				Attribute attribute;
				if (group != null) {
					if (onlyPlainTextFooter) {
						attribute = attrManager.getAttribute(registrarSession, group, URN_GROUP_MAIL_FOOTER);
						if (attribute == null || attribute.getValue() == null) {
							attribute = attrManager.getAttribute(registrarSession, vo, URN_VO_MAIL_FOOTER);
						}
					} else {
						attribute = attrManager.getAttribute(registrarSession, group, URN_GROUP_HTML_MAIL_FOOTER);
						if (attribute == null || attribute.getValue() == null) {
							attribute = attrManager.getAttribute(registrarSession, vo, URN_VO_HTML_MAIL_FOOTER);
						}
					}
				} else {
					if (onlyPlainTextFooter) {
						attribute = attrManager.getAttribute(registrarSession, vo, URN_VO_MAIL_FOOTER);
					} else {
						attribute = attrManager.getAttribute(registrarSession, vo, URN_VO_HTML_MAIL_FOOTER);
					}
				}
				if (attribute != null && attribute.getValue() != null) {
					footer = BeansUtils.attributeValueToString(attribute);
				}
			} catch (Exception ex) {
				// we don't care about exceptions here
				log.error("[MAIL MANAGER] Exception thrown when getting VO's footer for email from the attribute.", ex);
			}
			// replace by the footer or empty
			mailText = replaceNullSafe(mailText, footerTag, footer);
		}

		return mailText;
	}

	/**
	 * Replace the link for mail invitation not concerning authz type
	 *
	 * @param vo vo to get invite link for
	 * @param group group if is for group application
	 * @return full URL to application form
	 */
	private String buildInviteURL(Vo vo, Group group) {
		return buildInviteURL(vo, group,  EMPTY_STRING);
	}

	private String buildInviteURL(Vo vo, Group group, String namespace) {
		String url = getPerunUrl(vo, group);
		if (!StringUtils.hasText(url)) {
			return EMPTY_STRING;
		}

		Map<String, String> params = new HashMap<>();
		params.put("vo", vo.getShortName());
		if (group != null) {
			params.put("group", group.getName());
		}
		return buildUrl(url, params, namespace, "registrar");
	}

	/**
	 * Substitute common strings in mail text by data provided by
	 * application, application data and perun itself.
	 *
	 * Substituted strings are:
	 *
	 * {voName} - full vo name
	 * {groupName} - group short name
	 * {displayName} - user's display name returned from federation if present on form
	 * {firstName} - first name of user if present on form as separate form item
	 * {lastName} - last name of user if present on form as separate form item
	 * {appId} - application id
	 * {actor} - login in external system used when submitting application
	 * {extSource} - external system used for authentication when submitting application
	 * {appGuiUrl} - url to application GUI for user to see applications state
	 *
	 * {appGuiUrl-[authz]} - url to application GUI for user to see applications state
	 * {perunGuiUrl-[authz]} - url to perun GUI (user detail)
	 * {appDetailUrl-[authz]} - link for VO admin to approve / reject application
	 *
	 * {logins} - list of all logins from application
	 * {membershipExpiration} - membership expiration date
	 * {mail} - user preferred mail submitted on application or stored in a system
	 * {phone} - user phone submitted on application or stored in a system
	 *
	 * {customMessage} - message passed by the admin to mail (e.g. reason of application reject)
	 * {autoApproveError} - error that caused automatic approval failure
	 * {errors} - include errors, which occurred when processing registrar actions
	 * (e.g. login reservation errors passed to mail for VO admin)
	 *
	 * (if possible links are for: Kerberos, Federation and Certificate authz)
	 *
	 * @param app Application to substitute strings for (get VO etc.)
	 * @param data ApplicationData needed for substitution (displayName etc.)
	 * @param mailText String to substitute parts of
	 * @param reason Custom message passed by vo admin
	 * @param exceptions list of exceptions thrown when processing registrar actions
	 * @param isAlternativePlainText if the text will be used as alternative plain text to an HTML text
	 * @return modified text
	 */
	private String substituteCommonStrings(Application app,
										   List<ApplicationFormItemData> data,
										   String mailText,
										   String reason,
										   List<Exception> exceptions,
										   boolean isAlternativePlainText)
	{
		LinkedHashMap<String, String> additionalAttributes = BeansUtils.stringToMapOfAttributes(app.getFedInfo());
		PerunPrincipal applicationPrincipal = new PerunPrincipal(app, additionalAttributes);

		mailText = replaceAppId(mailText, app);
		mailText = replaceActor(mailText, app);
		mailText = replaceExtSource(mailText, app);
		mailText = replaceVoName(mailText, app);
		mailText = replaceGroupName(mailText, app);
		mailText = replaceCustomMessage(mailText, reason);
		mailText = replaceAutoApproveErrors(mailText, exceptions);
		mailText = replaceFromAppItems(mailText, data);
		mailText = replaceDisplayName(mailText, app, data, applicationPrincipal);
		mailText = replaceFirstName(mailText, app, data, applicationPrincipal);
		mailText = replaceLastName(mailText, app, data, applicationPrincipal);
		mailText = replaceErrors(mailText, exceptions);
		mailText = replaceLogins(mailText, app, data);
		mailText = replaceAppDetailUrl(mailText, app.getId(), app.getVo(), app.getGroup());
		mailText = replaceAppGuiUrl(mailText, app.getVo(), app.getGroup());
		mailText = replacePerunGuiUrl(mailText, app.getVo(), app.getGroup());
		mailText = replaceExpiration(mailText, app);
		mailText = replaceMail(mailText, app, data);
		mailText = replacePhone(mailText, app, data);

		mailText = replaceFooter(FIELD_MAIL_FOOTER, app.getVo(), app.getGroup(), mailText, true);
		mailText = replaceFooter(FIELD_HTML_MAIL_FOOTER, app.getVo(), app.getGroup(), mailText, isAlternativePlainText);

		return mailText;
	}

	private String replaceAppId(String mailText, Application app) {
		if (!mailText.contains(FIELD_APP_ID)) {
			return mailText;
		}
		return replaceInt(mailText, FIELD_APP_ID, app.getId());
	}

	private String replaceActor(String mailText, Application app) {
		if (!mailText.contains(FIELD_ACTOR)) {
			return mailText;
		}
		return replaceNullSafe(mailText, FIELD_ACTOR, app.getCreatedBy());
	}

	private String replaceExtSource(String mailText, Application app) {
		if (!mailText.contains(FIELD_EXT_SOURCE)) {
			return mailText;
		}
		return replaceNullSafe(mailText, FIELD_EXT_SOURCE, app.getExtSourceName());
	}

	private String replaceVoName(String mailText, Application app) {
		if (!mailText.contains(FIELD_VO_NAME)) {
			return mailText;
		}
		return replaceNullSafe(mailText, FIELD_VO_NAME, app.getVo().getName());
	}

	private String replaceGroupName(String mailText, Application app) {
		if (!mailText.contains(FIELD_GROUP_NAME)) {
			return mailText;
		}
		String gName = EMPTY_STRING;
		if (app.getGroup() != null) {
			gName = app.getGroup().getShortName();
		}
		return replaceNullSafe(mailText, FIELD_GROUP_NAME, gName);
	}

	private String replaceCustomMessage(String mailText, String reason) {
		if (!mailText.contains(FIELD_CUSTOM_MESSAGE)) {
			return mailText;
		}
		return replaceNullSafe(mailText, FIELD_CUSTOM_MESSAGE, reason);
	}

	private String replaceAutoApproveErrors(String mailText, List<Exception> exceptions) {
		if (!mailText.contains(FIELD_AUTO_APPROVE_ERROR)) {
			return mailText;
		}
		String msg = EMPTY_STRING;
		if (exceptions != null && !exceptions.isEmpty()) {
			msg = exceptions.stream().map(Throwable::getMessage).collect(Collectors.joining(", "));
		}
		return replaceNullSafe(mailText, FIELD_AUTO_APPROVE_ERROR, msg);
	}

	private String replaceFromAppItems(String mailText, List<ApplicationFormItemData> data) {
		Matcher matcher = FROM_APP_PATTERN.matcher(mailText);
		while (matcher.find()) {
			String itemName = matcher.group(1);

			for (ApplicationFormItemData item : data) {
				if (itemName.equals(item.getShortname())) {
					mailText = replaceNullSafe(mailText, "{fromApp-" + itemName +"}", item.getValue());
					break;
				}
			}
		}
		return mailText;
	}

	private String replaceDisplayName(String mailText, Application app, List<ApplicationFormItemData> data, PerunPrincipal applicationPrincipal) {
		if (!mailText.contains(FIELD_DISPLAY_NAME)) {
			return mailText;
		}
		String nameText = EMPTY_STRING; // backup
		ApplicationFormItemData replacementItem = data.stream()
			.filter(d -> hasValueAndReplacement(d, List.of(URN_USER_DISPLAY_NAME), List.of(CN, DISPLAY_NAME)))
			.findFirst()
			.orElse(null);
		if (replacementItem != null) {
			nameText = replacementItem.getValue();
		}

		if (!StringUtils.hasText(nameText)) {
			User user = getUserFromAppOrByExtSource(app, applicationPrincipal);
			if (user != null) {
				nameText = user.getDisplayName();
			}
		}

		return replaceNullSafe(mailText, FIELD_DISPLAY_NAME, nameText);
	}

	private String replaceFirstName(String mailText, Application app, List<ApplicationFormItemData> data, PerunPrincipal applicationPrincipal) {
		if (!mailText.contains(FIELD_FIRST_NAME)) {
			return mailText;
		}

		String nameText = EMPTY_STRING; // backup
		ApplicationFormItemData replacementItem = data.stream()
			.filter(d -> hasValueAndReplacement(d, List.of(URN_USER_FIRST_NAME), Collections.emptyList()))
			.findFirst()
			.orElse(null);
		if (replacementItem != null) {
			nameText = replacementItem.getValue();
		}

		if (!StringUtils.hasText(nameText)) {
			User user = getUserFromAppOrByExtSource(app, applicationPrincipal);
			if (user != null) {
				nameText = user.getFirstName();
			}
		}

		return replaceNullSafe(mailText, FIELD_FIRST_NAME, nameText);
	}

	private String replaceLastName(String mailText, Application app, List<ApplicationFormItemData> data, PerunPrincipal applicationPrincipal) {
		if (!mailText.contains(FIELD_LAST_NAME)) {
			return mailText;
		}
		String nameText = EMPTY_STRING; // backup
		ApplicationFormItemData replacementItem = data.stream()
			.filter(d -> hasValueAndReplacement(d, List.of(URN_USER_LAST_NAME), Collections.emptyList()))
			.findFirst()
			.orElse(null);
		if (replacementItem != null) {
			nameText = replacementItem.getValue();
		}

		if (!StringUtils.hasText(nameText)) {
			User user = getUserFromAppOrByExtSource(app, applicationPrincipal);
			if (user != null) {
				nameText = user.getLastName();
			}
		}
		return replaceNullSafe(mailText, FIELD_LAST_NAME, nameText);
	}

	private String replaceErrors(String mailText, List<Exception> exceptions) {
		if (!mailText.contains(FIELD_ERRORS)) {
			return mailText;
		}
		String errorText = EMPTY_STRING;
		if (exceptions != null && !exceptions.isEmpty()) {
			errorText = exceptions.stream().map(Throwable::toString).collect(Collectors.joining("\n\n"));
		}
		return replaceNullSafe(mailText, FIELD_ERRORS, errorText);
	}

	private String replaceLogins(String mailText, Application app, List<ApplicationFormItemData> data) {
		if (!mailText.contains("{login-")) {
			return mailText;
		}

		Pattern pattern = Pattern.compile("\\{login-[^}]+}");
		Matcher m = pattern.matcher(mailText);
		while (m.find()) {
			// whole "{login-something}"
			String toSubstitute = m.group(0);

			// new login value to replace in text
			Pattern namespacePattern = Pattern.compile("-(.*?)}");
			Matcher m2 = namespacePattern.matcher(toSubstitute);
			while (m2.find()) {
				// only namespace "meta", "egi-ui",...
				String namespace = m2.group(1);
				// if user exists, try to get login from attribute instead of application
				// since we do not allow to overwrite login by application
				String userLogin = getLoginFromUser(app, namespace);
				if (!StringUtils.hasText(userLogin)) {
					userLogin = getLoginFromApplication(data, namespace);
				}
				mailText = replaceNullSafe(mailText, toSubstitute, userLogin);
			}
		}
		return mailText;
	}

	private String replaceExpiration(String mailText, Application app) {
		if (!mailText.contains(FIELD_MEMBERSHIP_EXPIRATION)) {
			return mailText;
		}
		String expiration = EMPTY_STRING;
		if (app.getUser() != null) {
			try {
				User u = usersManager.getUserById(registrarSession, app.getUser().getId());
				Member m = membersManager.getMemberByUser(registrarSession, app.getVo(), u);
				Attribute a = attrManager.getAttribute(registrarSession, m, URN_MEMBER_EXPIRATION);
				if (a != null && a.getValue() != null) {
					expiration = a.valueAsString();
				}
			} catch (Exception ex) {
				log.error("[MAIL MANAGER] Error thrown when getting membership expiration param for mail.", ex);
			}
		}
		// replace by date or empty
		return replaceNullSafe(mailText, FIELD_MEMBERSHIP_EXPIRATION, expiration);
	}

	private String replaceMail(String mailText, Application app, List<ApplicationFormItemData> data) {
		if (!mailText.contains(FIELD_MAIL)) {
			return mailText;
		}
		String mail = EMPTY_STRING;
		if (app.getUser() != null) {
			try {
				mail = getValueFromStringUserAttribute(app.getUser().getId(), URN_USER_PREFERRED_MAIL);
			} catch (Exception ex) {
				log.error("[MAIL MANAGER] Error thrown when getting preferred mail param for mail.", ex);
			}
		} else {
			mail = getValueFromAppData(data, List.of(URN_MEMBER_MAIL), Collections.emptyList());
			if (!StringUtils.hasText(mail)) {
				mail = getValueFromAppData(data, List.of(URN_USER_PREFERRED_MAIL), Collections.emptyList());
			}
		}

		return replaceNullSafe(mailText, FIELD_MAIL, mail);
	}

	private String replacePhone(String mailText, Application app, List<ApplicationFormItemData> data) {
		if (!mailText.contains(FIELD_PHONE)) {
			return mailText;
		}
		String phone = EMPTY_STRING;
		if (app.getUser() != null) {
			try {
				phone = getValueFromStringUserAttribute(app.getUser().getId(), URN_USER_PHONE);
			} catch (Exception ex) {
				log.error("[MAIL MANAGER] Error thrown when getting phone param for mail.", ex);
			}
		} else {
			phone = getValueFromAppData(data, List.of(URN_MEMBER_PHONE), Collections.emptyList());
			if (!StringUtils.hasText(phone)) {
				phone = getValueFromAppData(data, List.of(URN_USER_PHONE), Collections.emptyList());
			}
		}

		return replaceNullSafe(mailText, FIELD_PHONE, phone);
	}

	private String getLoginFromUser(Application app, String namespace) {
		// if user exists, try to get login from attribute instead of application
		// since we do not allow to overwrite login by application
		try {
			if (app.getUser() != null) {
				List<Attribute> logins = attrManager.getLogins(registrarSession, app.getUser());
				Attribute a = logins.stream()
					.filter(
						attr -> namespace.equalsIgnoreCase(attr.getFriendlyNameParameter())
							&& attr.getValue() != null
					)
					.findFirst()
					.orElse(null);
				if (a != null && a.getValue() != null) {
					return a.valueAsString();
				}
			}
		} catch (Exception ex) {
			log.error("[MAIL MANAGER] Error thrown when replacing login in namespace \"{}\" for mail. {}",
				namespace, ex);
		}
		return null;
	}

	private String getLoginFromApplication(List<ApplicationFormItemData> data, String namespace) {
		// if user not known -> search through form items to get login
		ApplicationFormItemData itemData = data.stream()
			.filter(d ->
				StringUtils.hasText(d.getValue())
					&& d.getFormItem() != null
					&& ApplicationFormItem.Type.USERNAME == d.getFormItem().getType()
					&& d.getFormItem().getPerunDestinationAttribute() != null
					&& d.getFormItem().getPerunDestinationAttribute().contains("login-namespace:" + namespace)
			)
			.findFirst()
			.orElse(null);
		if (itemData != null) {
			return itemData.getValue();
		}
		return null;
	}

	private User getUserFromAppOrByExtSource(Application app, PerunPrincipal applicationPrincipal) {
		User user = app.getUser();
		if (user == null) {
			try {
				user = usersManager.getUserByExtSourceInformation(registrarSession, applicationPrincipal);
			} catch (Exception ex) {
				// user not found is ok
			}
		}
		return user;
	}

	private String getValueFromStringUserAttribute(int userId, String attrUrn)
		throws WrongAttributeAssignmentException, AttributeNotExistsException, UserNotExistsException
	{
		User u = usersManager.getUserById(registrarSession, userId);
		Attribute a = attrManager.getAttribute(registrarSession, u, attrUrn);
		if (a != null && a.getValue() != null) {
			return a.valueAsString();
		}
		return null;
	}

	private String getValueFromAppData(List<ApplicationFormItemData> data,
									   List<String> targetAttributes,
									   List<String> federationAttributes)
	{
		ApplicationFormItemData item = data.stream()
			.filter(d -> hasValueAndReplacement(d, targetAttributes, federationAttributes))
			.findFirst()
			.orElse(null);
		if (item != null) {
			return item.getValue();
		}
		return null;
	}

	private boolean hasValueAndReplacement(ApplicationFormItemData d,
										   List<String> targetAttributes,
										   List<String> federationAttributes)
	{
		if (d == null || d.getFormItem() == null || !StringUtils.hasText(d.getValue())) {
			return false;
		}

		ApplicationFormItem formItem = d.getFormItem();

		if (targetAttributes == null) {
			targetAttributes = new ArrayList<>();
		}

		String destinationAttribute = formItem.getPerunDestinationAttribute();
		if (StringUtils.hasText(destinationAttribute) && targetAttributes.contains(destinationAttribute)) {
			return true;
		}

		if (federationAttributes == null) {
			federationAttributes = new ArrayList<>();
		}
		String federationAttribute = formItem.getFederationAttribute();
		return StringUtils.hasText(federationAttribute) && federationAttributes.contains(federationAttribute);
	}

	private String replaceInt(String message, String key, Integer replacement) {
		return replaceNullSafe(message, key, replacement + EMPTY_STRING);
	}

	private String replaceNullSafe(String message, String key, String replacement) {
		if (!StringUtils.hasText(replacement)) {
			replacement = EMPTY_STRING;
		}
		message = message.replace(key, replacement);
		return message;
	}

	/**
	 * Return base URL of Perun instance taken from VO/Group attribute. If not set,
	 * value of branded, old gui domain is used. Otherwise, value of config property "perunUrl" is used.
	 * If can't determine, then empty string is returned.
	 *
	 * e.g. https://perun.cesnet.cz
	 *
	 * @param vo vo to get link for
	 * @param group to get link for
	 * @return Base url or empty string.
	 */
	private String getPerunUrl(Vo vo, Group group) {
		String result = getPropertyFromConfiguration("perunUrl");

		PerunAppsConfig.Brand voBrand = PerunAppsConfig.getBrandContainingVo(vo.getShortName());
		if (voBrand != null && !voBrand.getName().equals("default")) {
			result = voBrand.getOldGuiDomain();
		}
		try {
			if (group != null) {
				Attribute a = attrManager.getAttribute(registrarSession, group, URN_GROUP_REGISTRAR_URL);
				if (a != null && a.getValue() != null && !((String)a.getValue()).isEmpty()) {
					result = (String)a.getValue();
				} else {
					// take it from the VO if not on group settings
					Attribute a2 = attrManager.getAttribute(registrarSession, vo, URN_VO_REGISTRAR_URL);
					if (a2 != null && a2.getValue() != null && !((String)a2.getValue()).isEmpty()) {
						result = (String)a2.getValue();
					}
				}
			} else {
				// take it from the VO
				Attribute a2 = attrManager.getAttribute(registrarSession, vo, URN_VO_REGISTRAR_URL);
				if (a2 != null && a2.getValue() != null && !((String)a2.getValue()).isEmpty()) {
					result = (String)a2.getValue();
				}
			}
		} catch (Exception ex) {
			if (group != null) {
				log.error("[MAIL MANAGER] Exception when getting perun instance link for {} : {}", group, ex);
			} else {
				log.error("[MAIL MANAGER] Exception when getting perun instance link for {} : {}", vo, ex);
			}
		}

		return result;
	}

	private String replacePerunGuiUrl(String mailText, Vo vo, Group group) {
		// replace perun GUI links
		if (mailText.contains(FIELD_PERUN_GUI_URL)) {
			String text = getPerunUrl(vo, group);
			if (StringUtils.hasText(text)) {
				text = buildUrl(text, Map.of(), "gui");
			}
			mailText = replaceNullSafe(mailText, FIELD_PERUN_GUI_URL, text);
		}

		// replace perun GUI app link
		if (mailText.contains("{perunGuiUrl-")) {
			Pattern pattern = Pattern.compile("\\{perunGuiUrl-[^}]+}");
			Matcher m = pattern.matcher(mailText);
			while (m.find()) {

				// whole "{perunGuiUrl-something}"
				String toSubstitute = m.group(0);

				// new login value to replace in text
				String newValue = EMPTY_STRING;

				Pattern namespacePattern = Pattern.compile("-(.*?)}");
				Matcher m2 = namespacePattern.matcher(toSubstitute);
				while (m2.find()) {
					// only namespace "fed", "cert",...
					String namespace = m2.group(1);

					if (namespace.equals("newGUI")) {
						newValue = PerunAppsConfig.getBrandContainingVo(vo.getShortName()).getNewApps().getAdmin();
					} else {
						newValue = getPerunUrl(vo, group);
						if (StringUtils.hasText(newValue)) {
							newValue = buildUrl(newValue, Map.of(), "gui");
						}
					}
				}
				// substitute {appGuiUrl-authz} with actual value or empty string
				mailText = replaceNullSafe(mailText, toSubstitute, newValue);
			}
		}

		return mailText;
	}

	private String replaceAppGuiUrl(String mailText, Vo vo, Group group) {
		// replace perun application GUI link with list of applications
		if (mailText.contains(FIELD_APP_GUI_URL)) {
			// new backup
			String text = getPerunUrl(vo, group);
			if (StringUtils.hasText(text)) {
				Map<String, String> params = new HashMap<>();
				params.put("vo", vo.getShortName());
				params.put("page", "apps");
				if (group != null) {
					params.put("group", group.getName());
				}
				text = buildUrl(mailText, params, "registrar");
			}
			mailText = replaceNullSafe(mailText, FIELD_APP_GUI_URL, text);
		}

		// replace registrar GUI link
		if (mailText.contains("{appGuiUrl-")) {
			Pattern pattern = Pattern.compile("\\{appGuiUrl-[^}]+}");
			Matcher m = pattern.matcher(mailText);
			while (m.find()) {
				// whole "{appGuiUrl-something}"
				String toSubstitute = m.group(0);

				// new login value to replace in text
				String newValue = EMPTY_STRING;

				Pattern namespacePattern = Pattern.compile("-(.*?)}");
				Matcher m2 = namespacePattern.matcher(toSubstitute);
				while (m2.find()) {
					// only namespace "fed", "cert",...
					String namespace = m2.group(1);

					newValue = getPerunUrl(vo, group);
					if (StringUtils.hasText(newValue)) {
						Map<String, String> params = new HashMap<>();
						params.put("vo", vo.getShortName());
						params.put("page", "apps");
						if (group != null) {
							params.put("group", group.getName());
						}
						newValue = buildUrl(mailText, params, namespace, "registrar");
					}
				}
				// substitute {appGuiUrl-authz} with actual value or empty string
				mailText = replaceNullSafe(mailText, toSubstitute, newValue);
			}
		}

		return mailText;
	}

	private String replaceAppDetailUrl(String mailText, int appId, Vo vo, Group group) {
		// replace appDetail for VO admins
		if (mailText.contains(FIELD_APP_DETAIL_URL)) {
			String text = getPerunUrl(vo, group);
			if (StringUtils.hasText(text)) {
				if (!text.endsWith("/")) {
					text += "/";
				}
				text += "gui/?vo/appdetail?id="+appId;
				// FIXME - buildUrl() handles only valid URL and not our fake "anchor" url for navigating in OLD GUI
				//text = buildUrl(text, Map.of("id", String.valueOf(appId)), "gui/?vo/appdetail");
			}
			mailText = replaceNullSafe(mailText, FIELD_APP_DETAIL_URL, text);
		}

		// replace perun app link
		if (mailText.contains("{appDetailUrl-")) {

			Pattern pattern = Pattern.compile("\\{appDetailUrl-[^}]+}");
			Matcher m = pattern.matcher(mailText);
			while (m.find()) {
				// whole "{appDetailUrl-something}"
				String toSubstitute = m.group(0);

				// new login value to replace in text
				String newValue = EMPTY_STRING;

				Pattern namespacePattern = Pattern.compile("-(.*?)}");
				Matcher m2 = namespacePattern.matcher(toSubstitute);
				while (m2.find()) {
					// only namespace "fed", "cert",...
					String namespace = m2.group(1);

					if (namespace.equals("newGUI")) {
						newValue = PerunAppsConfig.getBrandContainingVo(vo.getShortName()).getNewApps().getAdmin();
						if (StringUtils.hasText(newValue)) {
							List<String> pathComponents = new ArrayList<>();
							pathComponents.add("organizations");
							pathComponents.add(String.valueOf(vo.getId()));
							if (group != null) {
								pathComponents.add("groups");
								pathComponents.add(String.valueOf(group.getId()));
							}
							pathComponents.add("applications");
							pathComponents.add(String.valueOf(appId));
							newValue = buildUrl(newValue, Map.of(), pathComponents);
						}
					} else {
						newValue = getPerunUrl(vo, group);
						if (StringUtils.hasText(newValue)) {
							if (!newValue.endsWith("/")) newValue += "/";
							newValue += namespace + "/gui/";
							newValue += "?vo/appdetail?id="+appId;
							// FIXME - buildUrl() handles only valid URL and not our fake "anchor" url for navigating in OLD GUI
							//newValue = buildUrl(newValue, Map.of("vo/appdetail?id", String.valueOf(appId)), namespace, "gui");
						}
					}
				}

				// substitute {appDetailUrl-authz} with actual value or empty string
				mailText = replaceNullSafe(mailText, toSubstitute, newValue);
			}
		}

		return mailText;
	}

	/**
	 * Get parts of URL, which are considered federative and must have "?" in URLs and not "#".
	 *
	 * @return Array of federative URLs
	 */
	private ArrayList<String> getFedAuthz() {
		ArrayList<String> fedAuthz = new ArrayList<>();
		fedAuthz.add("fed");
		String fedString = getPropertyFromConfiguration("fedAuthz");
		if (fedString != null && !fedString.isEmpty()) {
			String[] array = fedString.split(",");
			for (String s : array) {
				fedAuthz.add(s.trim());
			}
		}

		return fedAuthz;
	}

	private static String buildUrl(String base, Map<String, String> params, List<String> pathComponents) {
		if (pathComponents == null) {
			return buildUrl(base, params);
		} else {
			return buildUrl(base, params, pathComponents.toArray(new String[0]));
		}
	}

	private static String buildUrl(String base, Map<String, String> params, String... pathComponents) {
		StringBuilder sb = new StringBuilder(base);
		if (!base.endsWith("/")) {
			sb.append("/");
		}
		Arrays.stream(pathComponents)
			.map(String::trim)// remove spaces
			.filter(StringUtils::hasText) // filter out empty strings
			.map(s -> s.endsWith("/") ? s : s + "/") // all path components need to end with "/"
			.forEach(sb::append); //append all path components

		if (params != null) {
			sb.append("?");
			final StringJoiner sj = new StringJoiner("&");
			params.forEach(
				(key, value) -> sj.add(
					new StringBuilder().append(key).append("=").append(getUrlEncodedString(value))
				)
			);
			sb.append(sj);
		}
		return sb.toString();
	}

	/**
	 * Return URL encoded String in UTF-8. If not possible, return original string.
	 *
	 * @param s String to encode
	 * @return URL Encoded string
	 */
	private static String getUrlEncodedString(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}

	private void copyApplicationMails(PerunSession sess, ApplicationForm formFrom, ApplicationForm formTo) throws PerunException {
		List<ApplicationMail> mails = getApplicationMails(sess, formFrom);
		for (ApplicationMail mail : mails) {
			// to start transaction
			try {
				registrarManager.getMailManager().addMail(sess, formTo, mail);
			} catch (DuplicateKeyException ex) {
				log.info("[MAIL MANAGER] Mail notification of type {}/{} skipped while copying (was already present).",
					mail.getMailType(), mail.getAppType());
			}
		}
	}

	private void sendUserMessage(Application app, ApplicationMail mail, List<ApplicationFormItemData> data, String reason, List<Exception> exceptions, MailType type) throws MessagingException {
		MimeMessage message = getUserMessage(app, mail, data, reason, exceptions);

		try {
			// send mail
			mailSender.send(message);
			log.info("[MAIL MANAGER] Sending mail: {} to: {} / appID: {} / {} / {}",
					type, message.getAllRecipients(), app.getId(), app.getVo(), app.getGroup());
		} catch (MailException | MessagingException ex) {
			log.error("[MAIL MANAGER] Sending mail: {} failed because of exception.", type, ex);
		}
	}

	private void appCreatedVoAdmin(Application app, ApplicationMail mail, List<ApplicationFormItemData> data, String reason, List<Exception> exceptions) throws MessagingException {
		MimeMessage message = getAdminMessage(app, mail, data, reason, exceptions);

		// send a message to all VO or Group admins
		Set<String> toEmail = getToMailAddresses(app);
		for (String email : toEmail) {
			setRecipient(message, email);
			try {
				mailSender.send(message);
				log.info("[MAIL MANAGER] Sending mail: APP_CREATED_VO_ADMIN to: {} / appID: {} / {} / {}",
					message.getAllRecipients(), app.getId(), app.getVo(), app.getGroup());
			} catch (MailException | MessagingException ex) {
				log.error("[MAIL MANAGER] Sending mail: APP_CREATED_VO_ADMIN failed because of exception.", ex);
			}
		}
	}

	private void mailValidation(Application app, ApplicationMail mail, List<ApplicationFormItemData> data, String reason, List<Exception> exceptions) throws MessagingException {
		MimeMessage message = mailSender.createMimeMessage();
		// set FROM
		setFromMailAddress(message, app);

		// set TO
		message.setRecipient(Message.RecipientType.TO, null); // empty = not sent

		// get language
		Locale lang = new Locale(getLanguageFromAppData(app, data));
		// get localized subject and text
		String mailText = getMailText(mail, lang, app, data, reason, exceptions);
		if (containsHtmlMessage(mail, lang)) {
			String alternativePlainText = getMailAlternativePlainText(mail, lang, app, data, reason, exceptions);
			setHtmlMessageWithAltPlainTextMessage(message, alternativePlainText, mailText);
		} else {
			message.setText(mailText);
		}
		String mailSubject = getMailSubject(mail, lang, app, data, reason, exceptions);
		message.setSubject(mailSubject);

		// send to all emails, which needs to be validated
		for (ApplicationFormItemData d : data) {
			ApplicationFormItem item = d.getFormItem();
			String value = d.getValue();
			// if mail field and not validated
			int loa = 0;
			try {
				loa = Integer.parseInt(d.getAssuranceLevel());
			} catch (NumberFormatException ex) {
				// ignore
			}
			if (ApplicationFormItem.Type.VALIDATED_EMAIL.equals(item.getType()) && loa < 1) {
				if (value != null && !value.isEmpty()) {
					// set TO
					message.setRecipients(Message.RecipientType.TO, new InternetAddress[] {new InternetAddress(value)});

					mailText = replaceValidationLinkAndRedirectUrl(app, d, mailText);

					// set replaced text
					if (containsHtmlMessage(mail, lang)) {
						String alternativePlainText = getMailAlternativePlainText(mail, lang, app, data, reason, exceptions);
						alternativePlainText = replaceValidationLinkAndRedirectUrl(app, d, alternativePlainText);
						setHtmlMessageWithAltPlainTextMessage(message, alternativePlainText, mailText);
					} else {
						message.setText(mailText);
					}

					try {
						mailSender.send(message);
						log.info("[MAIL MANAGER] Sending mail: MAIL_VALIDATION to: {} / appID: {} / {} / {}",
							message.getAllRecipients(), app.getId(), app.getVo(), app.getGroup());
					} catch (MailException ex) {
						log.error("[MAIL MANAGER] Sending mail: MAIL_VALIDATION failed because of exception.", ex);
					}

				} else {
					log.error("[MAIL MANAGER] Sending mail: MAIL_VALIDATION failed. Not valid value of VALIDATED_MAIL field: {}", value);
				}
			}
		}
	}

	private String replaceValidationLinkAndRedirectUrl(Application app, ApplicationFormItemData d, String mailText) {
		// get validation link params
		String i = Integer.toString(d.getId(), Character.MAX_RADIX);
		String m = getMessageAuthenticationCode(i);

		// replace new validation link
		if (mailText.contains("{validationLink-")) {
			Pattern pattern = Pattern.compile("\\{validationLink-[^}]+}");
			Matcher matcher = pattern.matcher(mailText);
			while (matcher.find()) {
				// whole "{validationLink-something}"
				String toSubstitute = matcher.group(0);

				// new login value to replace in text
				String newValue = EMPTY_STRING;

				Pattern namespacePattern = Pattern.compile("-(.*?)}");
				Matcher m2 = namespacePattern.matcher(toSubstitute);
				while (m2.find()) {
					// only namespace "fed", "cert",...
					String namespace = m2.group(1);

					newValue = getPerunUrl(app.getVo(), app.getGroup());

					if (newValue != null && !newValue.isEmpty()) {
						Map<String, String> params = new HashMap<>();
						params.put("vo", app.getVo().getShortName());
						if (app.getGroup() != null) {
							params.put("group", app.getGroup().getShortName());
						}
						params.put("i", i);
						params.put("m", m);
						newValue = buildUrl(newValue, params, namespace, "registrar");
					}
				}
				// substitute {validationLink-authz} with actual value or empty string
				mailText = replaceNullSafe(mailText, toSubstitute, newValue);
			}
		}

		if (mailText.contains(FIELD_VALIDATION_LINK)) {
			// new backup if validation URL is missing
			String url = getPerunUrl(app.getVo(), app.getGroup());
			if (StringUtils.hasText(url)) {
				Map<String, String> params = new HashMap<>();
				params.put("vo", app.getVo().getShortName());
				if (app.getGroup() != null) {
					params.put("group", app.getGroup().getName());
				}
				params.put("i", i);
				params.put("m", m);
				url = buildUrl(url, params, "registrar");

				// replace validation link
				mailText = replaceNullSafe(mailText, FIELD_VALIDATION_LINK, url);
			}
		}

		if (mailText.contains(FIELD_REDIRECT_URL)) {
			String redirectURL = BeansUtils.stringToMapOfAttributes(app.getFedInfo()).get("redirectURL");

			mailText = replaceNullSafe(mailText, FIELD_REDIRECT_URL, redirectURL != null ? "&target=" + getUrlEncodedString(redirectURL) : EMPTY_STRING);
		}

		return mailText;
	}

	private void appErrorVoAdmin(Application app, ApplicationMail mail, List<ApplicationFormItemData> data, String reason, List<Exception> exceptions) throws MessagingException {
		MimeMessage message = getAdminMessage(app, mail, data, reason, exceptions);

		// send a message to all VO or Group admins
		Set<String> toEmail = getToMailAddresses(app);

		for (String email : toEmail) {
			setRecipient(message, email);
			try {
				mailSender.send(message);
				log.info("[MAIL MANAGER] Sending mail: APP_ERROR_VO_ADMIN to: {} / appID: {} / {} / {}",
						message.getAllRecipients(), app.getId(), app.getVo(), app.getGroup());
			} catch (MailException ex) {
				log.error("[MAIL MANAGER] Sending mail: APP_ERROR_VO_ADMIN failed because of exception.", ex);
			}
		}
	}

	private void setRecipient(MimeMessage message, String email) throws MessagingException {
		message.setRecipient(Message.RecipientType.TO, null);
		if (email != null) {
			message.setRecipient(Message.RecipientType.TO, new InternetAddress(email));
		}
	}


	private String getMailText(ApplicationMail mail, Locale lang, Application app, List<ApplicationFormItemData> data,
							   String reason, List<Exception> exceptions) {
		String mailText = EMPTY_STRING;

		MailText htmlMt = mail.getHtmlMessage(lang);
		MailText mt = mail.getMessage(lang);

		if (htmlMt.getText() != null && !htmlMt.getText().isBlank()) {
			mailText = htmlMt.getText();
			mailText = substituteCommonStrings(app, data, mailText, reason, exceptions, false);
		} else if (mt.getText() != null && !mt.getText().isEmpty()) {
			mailText = mt.getText();
			mailText = substituteCommonStrings(app, data, mailText, reason, exceptions, false);
		}

		return mailText;
	}

	private String getMailAlternativePlainText(ApplicationMail mail, Locale lang, Application app, List<ApplicationFormItemData> data,
							   String reason, List<Exception> exceptions) {
		String mailText = EMPTY_STRING;
		MailText mt = mail.getMessage(lang);

		if (mt.getText() != null && !mt.getText().isEmpty()) {
			mailText = mt.getText();
			mailText = substituteCommonStrings(app, data, mailText, reason, exceptions, true);
		}

		return mailText;
	}

	private String getMailSubject(ApplicationMail mail, Locale lang, Application app, List<ApplicationFormItemData> data,
								  String reason, List<Exception> exceptions) {
		String mailSubject = EMPTY_STRING;

		MailText htmlMt = mail.getHtmlMessage(lang);
		MailText mt = mail.getMessage(lang);

		if (htmlMt.getSubject() != null && !htmlMt.getSubject().isBlank()) {
			mailSubject = htmlMt.getSubject();
			mailSubject = substituteCommonStrings(app, data, mailSubject, reason, exceptions, false);
		} else if (mt.getSubject() != null && !mt.getSubject().isEmpty()) {
			mailSubject = mt.getSubject();
			mailSubject = substituteCommonStrings(app, data, mailSubject, reason, exceptions, false);
		}

		// substitute common strings
		return mailSubject;
	}

	private String getMailSubjectInvitation(ApplicationMail mail, Locale lang, Vo vo, Group group, User user, String name) {
		String mailSubject = EMPTY_STRING;

		MailText htmlMt = mail.getHtmlMessage(lang);
		MailText mt = mail.getMessage(lang);

		if (htmlMt.getSubject() != null && !htmlMt.getSubject().isBlank()) {
			mailSubject = htmlMt.getSubject();
			mailSubject = substituteCommonStringsForInvite(vo, group, user, name, mailSubject, false);

		} else if (mt.getSubject() != null && !mt.getSubject().isEmpty()) {
			mailSubject = mt.getSubject();
			mailSubject = substituteCommonStringsForInvite(vo, group, user, name, mailSubject, false);
		}

		return mailSubject;
	}

	private String getMailTextInvitation(ApplicationMail mail, Locale lang, Vo vo, Group group, User user, String name) {
		String mailText = EMPTY_STRING;

		MailText htmlMt = mail.getHtmlMessage(lang);
		MailText mt = mail.getMessage(lang);

		if (htmlMt.getText() != null && !htmlMt.getText().isBlank()) {
			mailText = htmlMt.getText();
			mailText = substituteCommonStringsForInvite(vo, group, user, name, mailText, false);
		} else if (mt.getText() != null && !mt.getText().isEmpty()) {
			mailText = mt.getText();
			mailText = substituteCommonStringsForInvite(vo, group, user, name, mailText, false);
		}

		return mailText;
	}

	private String getMailAlternativePlainTextInvitation(ApplicationMail mail, Locale lang, Vo vo, Group group, User user, String name) {
		String mailText = EMPTY_STRING;
		MailText mt = mail.getMessage(lang);

		if (mt.getText() != null && !mt.getText().isEmpty()) {
			mailText = mt.getText();
			mailText = substituteCommonStringsForInvite(vo, group, user, name, mailText, true);
		}

		return mailText;
	}

	private ApplicationForm getForm(Application app) throws FormNotExistsException {
		return getForm(app.getVo(), app.getGroup());
	}

	private ApplicationForm getForm(Vo vo, Group group) throws FormNotExistsException {
		if (group != null) {
			return registrarManager.getFormForGroup(group);
		} else {
			return registrarManager.getFormForVo(vo);
		}
	}

	private ApplicationMail getMail(ApplicationForm form, AppType appType, MailType mailType) throws RegistrarException {
		ApplicationMail mail = getMailByParams(form.getId(), appType, mailType);
		if (mail == null) {
			throw new RegistrarException("You don't have invitation e-mail template defined.");
		} else if (!mail.getSend()) {
			throw new RegistrarException("Sending of invitations has been disabled.");
		}

		return mail;
	}

	/**
	 * Send invitation email to one user
	 */
	private void sendInvitationMail(PerunSession sess, Vo vo, Group group, String email, String language,
									MimeMessage message, Application app) throws RegistrarException {
		try {
			if (!isInvitationEnabled(sess, vo, group)) {
				log.error("[MAIL MANAGER] Invite user: Application form is not setup correctly or invitation notification does not exist.");
				throw new RegistrarException("Unable to invite user - application form is not setup correctly or invitation notification does not exist.");
			}
		} catch (PerunException ex) {
			// ignore - this shouldn't happen thanks to the same authorization check earlier
		}

		try {
			mailSender.send(message);
			User sendingUser = sess.getPerunPrincipal().getUser();
			AuditEvent event = new InvitationSentEvent(sendingUser, email, language, group, vo);
			sess.getPerun().getAuditer().log(sess, event);
			log.info("[MAIL MANAGER] Sending mail: USER_INVITE to: {} / {} / {}",
					message.getAllRecipients(), app.getVo(), app.getGroup());
		} catch (MailException | MessagingException ex) {
			log.error("[MAIL MANAGER] Sending mail: USER_INVITE failed because of exception.", ex);
			throw new RegistrarException("Unable to send e-mail.", ex);
		}
	}

	/**
	 * Get lang preferred by USER. If value of attribute is null or empty, defaultLanguage (passed as param) is returned.
	 */
	private String getLanguageForUser(User user, String defaultLanguage) {
		String language = defaultLanguage;
		try {
			Attribute a = attrManager.getAttribute(registrarSession, user, URN_USER_PREFERRED_LANGUAGE);
			if (a != null && a.getValue() != null) {
				String possibleLang = BeansUtils.attributeValueToString(a);
				if (possibleLang != null && !possibleLang.trim().isEmpty()) {
					language = possibleLang;
				}
			}
		} catch (Exception ex) {
			log.error("[MAIL MANAGER] Exception thrown when getting preferred language for USER={}: {}", user, ex);
		}

		return language;
	}

	/**
	 * Get language from VO and GROUP attributes. If possible, returns lang from GROUP, then tries VO. If both are null
	 * or empty, defaultLanguage (passed as param) is returned.
	 */
	private String getLanguageFromVoAndGroupAttrs(Vo vo, Group group, String defaultLanguage) {
		String language = defaultLanguage;

		if (group == null) {
			try {
				language = getLanguageForVo(vo, defaultLanguage);
			} catch (Exception ex) {
				log.error("[MAIL MANAGER] Exception thrown when getting preferred language of notification for VO={}: {}", vo, ex);
			}
		} else {
			try {
				language = getLanguageForGroup(group, defaultLanguage);
			} catch (Exception ex) {
				log.error("[MAIL MANAGER] Exception thrown when getting preferred language of notification for Group={}: {}", group, ex);
			}
		}

		return language;
	}

	/**
	 * Get language from a GROUP attribute. If value is null or empty, defaultLanguage (passed as param) is returned.
	 */
	private String getLanguageForGroup(Group group, String defaultLanguage) throws AttributeNotExistsException, WrongAttributeAssignmentException {
		Attribute a = attrManager.getAttribute(registrarSession, group, URN_GROUP_LANGUAGE_EMAIL);
		return getLocaleFromAttr(a, defaultLanguage);
	}

	/**
	 * Get language from a VO attribute. If value is null or empty, defaultLanguage (passed as param) is returned.
	 */
	private String getLanguageForVo(Vo vo, String defaultLanguage) throws AttributeNotExistsException, WrongAttributeAssignmentException {
		Attribute a = attrManager.getAttribute(registrarSession, vo, URN_VO_LANGUAGE_EMAIL);
		return getLocaleFromAttr(a, defaultLanguage);
	}

	/**
	 * Get language from an attribute. If value is null or empty, defaultLanguage (passed as param) is returned.
	 */
	private String getLocaleFromAttr(Attribute a, String defaultLanguage) {
		String language = defaultLanguage;

		if (a != null && a.getValue() != null) {
			String possibleLang = BeansUtils.attributeValueToString(a);
			if (possibleLang != null && !possibleLang.trim().isEmpty()) {
				language = possibleLang;
			}
		}

		return language;
	}

	/**
	 * Get FROM field as email attribute from VO and GROUP. If group is NULL or doesn't have the attribute, VO is used as fallback.
	 */
	private Attribute getMailFromVoAndGroupAttrs(Application app, String voEmailAttr, String groupEmailAttr)
			throws AttributeNotExistsException, WrongAttributeAssignmentException {
		Attribute attrSenderEmail;

		if (app.getGroup() == null) {
			attrSenderEmail = attrManager.getAttribute(registrarSession, app.getVo(), voEmailAttr);
		} else {
			attrSenderEmail = attrManager.getAttribute(registrarSession, app.getGroup(), groupEmailAttr);
			// use VO as backup
			if (attrSenderEmail == null || attrSenderEmail.getValue() == null) {
				attrSenderEmail = attrManager.getAttribute(registrarSession, app.getVo(), voEmailAttr);
			}
		}

		return attrSenderEmail;
	}

	/**
	 * Initialize MimeMessage that will be sent to manager(admin). Initialization takes care of following:
	 * - set FROM, set TEXT, set SUBJECT
	 */
	private MimeMessage getAdminMessage(Application app, ApplicationMail mail, List<ApplicationFormItemData> data, String reason, List<Exception> exceptions) throws MessagingException {
		MimeMessage message = mailSender.createMimeMessage();

		// set FROM
		setFromMailAddress(message, app);

		// set language independent on user's preferred language.
		String language = LANG_EN;
		language = getLanguageFromVoAndGroupAttrs(app.getVo(), app.getGroup(), language);
		Locale lang = new Locale(language);

		// get localized subject and text
		String mailText = getMailText(mail, lang, app, data, reason, exceptions);
		if (containsHtmlMessage(mail, lang)) {
			String alternativePlainText = getMailAlternativePlainText(mail, lang, app, data, reason, exceptions);
			setHtmlMessageWithAltPlainTextMessage(message, alternativePlainText, mailText);
		} else {
			message.setText(mailText);
		}
		String mailSubject = getMailSubject(mail, lang, app, data, reason, exceptions);
		message.setSubject(mailSubject);

		return message;
	}

	/**
	 * Initialize MimeMessage that will be sent to user. Initialization takes care of following:
	 * - set FROM, set TO, set TEXT, set SUBJECT
	 */
	private MimeMessage getUserMessage(Application app, ApplicationMail mail, List<ApplicationFormItemData> data, String reason, List<Exception> exceptions) throws MessagingException {
		MimeMessage message = mailSender.createMimeMessage();
		// set FROM
		setFromMailAddress(message, app);

		// set TO
		setUsersMailAsTo(message, app, data);

		// get language
		Locale lang = new Locale(getLanguageFromAppData(app, data));

		// get localized subject and text
		String mailText = getMailText(mail, lang, app, data, reason, exceptions);
		if (containsHtmlMessage(mail, lang)) {
			String alternativePlainText = getMailAlternativePlainText(mail, lang, app, data, reason, exceptions);
			setHtmlMessageWithAltPlainTextMessage(message, alternativePlainText, mailText);
		} else {
			message.setText(mailText);
		}
		String mailSubject = getMailSubject(mail, lang, app, data, reason, exceptions);
		message.setSubject(mailSubject);

		return message;
	}

	/**
	 * Create fake application. Can be used to set FROM field.
	 */
	private Application getFakeApplication(Vo vo, Group group) {
		Application app = new Application();
		app.setVo(vo);
		app.setGroup(group);

		return app;
	}

	/**
	 * Initialize MimeMessage for invitation, that will be sent to user. Initialization takes care of following:
	 * - set FROM, set TO, set TEXT, set SUBJECT
	 */
	private MimeMessage getInvitationMessage(Vo vo, Group group, String language, String to, Application app, String name, User user)
			throws FormNotExistsException, RegistrarException, MessagingException {
		if (language == null) {
			language = LANG_EN;
			language = getLanguageFromVoAndGroupAttrs(vo, group, language);
		}

		ApplicationForm form = getForm(vo, group);
		ApplicationMail mail = getMail(form, AppType.INITIAL, MailType.USER_INVITE);
		MimeMessage message = mailSender.createMimeMessage();

		setFromMailAddress(message, app);
		setRecipient(message, to);

		// get language
		Locale lang = new Locale(language);
		// get localized subject and text
		String mailText = getMailTextInvitation(mail, lang, vo, group, user, name);
		if (containsHtmlMessage(mail, lang)) {
			String alternativePlainText = getMailAlternativePlainTextInvitation(mail, lang, vo, group, user, name);
			setHtmlMessageWithAltPlainTextMessage(message, alternativePlainText, mailText);
		} else {
			message.setText(mailText);
		}
		String mailSubject = getMailSubjectInvitation(mail, lang, vo, group, user, name);
		message.setSubject(mailSubject);

		return message;
	}

	private boolean containsHtmlMessage(ApplicationMail mail, Locale lang) {
		MailText plainMt = mail.getMessage(lang);
		MailText htmlMt = mail.getHtmlMessage(lang);
		return (htmlMt.getText() != null && !htmlMt.getText().isBlank())
			|| (plainMt.getText() != null && plainMt.getText().contains(FIELD_HTML_MAIL_FOOTER));
	}

	/**
	 * Prepares html message with alternative plain text message (for clients which are not able to display html message)
	 * @param message email message
	 * @param alternativePlainText alternative plain text
	 * @param mailText main html text
	 * @throws MessagingException
	 */
	private void setHtmlMessageWithAltPlainTextMessage (MimeMessage message, String alternativePlainText, String mailText) throws MessagingException {
		final MimeBodyPart textPart = new MimeBodyPart();
		textPart.setContent(alternativePlainText, "text/plain; charset=utf-8");
		final MimeBodyPart htmlPart = new MimeBodyPart();
		htmlPart.setContent(mailText, "text/html; charset=utf-8");
		final Multipart mp = new MimeMultipart("alternative");
		mp.addBodyPart(textPart);
		mp.addBodyPart(htmlPart);
		message.setContent(mp);
	}

	/**
	 * Get notification emails for given VO. If toEmail (URN_VO_TO_EMAIL) has no value set,
	 * all configured roles for receiving VO notifications are used.
	 *
	 * @param vo Virtual Organization for which the lookup is performed. If null, empty result will be returned.
	 * @return List of emails used for sending notifications (can be empty).
	 */
	private Set<String> getToEmailsVoLevel(Vo vo)
		throws AttributeNotExistsException, WrongAttributeAssignmentException
	{
		if (vo == null) {
			return new HashSet<>();
		}
		Attribute toEmailAttr = attrManager.getAttribute(registrarSession, vo, URN_VO_TO_EMAIL);
		Set<String> emails = getToEmailsFromAttribute(toEmailAttr);
		if (emails.isEmpty()) {
			emails = getToEmailsViaRoles(VO, vo);
		}
		return emails;
	}

	/**
	 * Get notification emails for given Group. If toEmail (URN_GROUP_TO_EMAIL) has no value set,
	 * all configured roles for receiving GROUP notifications are used.
	 *
	 * @param group Group for which the lookup is performed. If null, empty result will be returned.
	 * @return List of emails used for sending notifications (can be empty).
	 */
	private Set<String> getToEmailsGroupLevel(Group group)
		throws AttributeNotExistsException, WrongAttributeAssignmentException
	{
		if (group == null) {
			return new HashSet<>();
		}
		Attribute toEmailAttr = attrManager.getAttribute(registrarSession, group, URN_GROUP_TO_EMAIL);
		Set<String> emails = getToEmailsFromAttribute(toEmailAttr);
		if (emails.isEmpty()) {
			emails = getToEmailsViaRoles(GROUP, group);
		}
		return emails;
	}

	/**
	 * Get "TO" email addresses of notification recipient(s) from the attribute
	 * @param toEmailAttr Attribute with the recipients as value
	 * @return Set of identified emails. Can be empty if attr has no non-blank item in array value or no value at all.
	 */
	private Set<String> getToEmailsFromAttribute(Attribute toEmailAttr) {
		// if there are any pre-defined VO emailTo attributes, use them
		Set<String> emails = new HashSet<>();
		if (toEmailAttr != null && toEmailAttr.getValue() != null) {
			ArrayList<String> value = toEmailAttr.valueAsList();
			for (String adr : value) {
				if (StringUtils.hasText(adr)) {
					emails.add(adr);
				}
			}
		}
		return emails;
	}

	/**
	 * Get "TO" emails from notification receiver roles of the specified object.
	 * @param object String specification of the object
	 * @param objectInstance instance of the object
	 * @return Set of identified emails. Can be empty if not roles or users in the role have been identified,
	 * or they do not have email address set.
	 * @throws WrongAttributeAssignmentException
	 * @throws AttributeNotExistsException
	 */
	private Set<String> getToEmailsViaRoles(String object, PerunBean objectInstance)
		throws WrongAttributeAssignmentException, AttributeNotExistsException
	{
		Set<String> emails = new HashSet<>();
		try {
			List<String> rolesToNotify = getNotificationReceiverRoles(object);
			List<RichUser> admins = new ArrayList<>();
			for (String role : rolesToNotify) {
				admins.addAll(AuthzResolverBlImpl.getRichAdmins(registrarSession, objectInstance, role));
			}
			emails.addAll(getUserPreferredMails(registrarSession, admins));
			return emails;
		} catch (RoleCannotBeManagedException e) {
			throw new InternalErrorException(e);
		}
	}

	/**
	 * Get preferred email for each user.
	 *
	 * @param sess
	 * @param users
	 * @return list of preferred emails for given users.
	 * @throws AttributeNotExistsException
	 * @throws WrongAttributeAssignmentException
	 */
	private List<String> getUserPreferredMails(PerunSession sess, List<RichUser> users)
		throws AttributeNotExistsException, WrongAttributeAssignmentException {
		List<String> emails = new ArrayList<>();

		for (User user : users) {
			Attribute attrPreferredMail = attrManager.getAttribute(sess, user, URN_USER_PREFERRED_MAIL);

			if (attrPreferredMail != null && attrPreferredMail.getValue() != null) {
				emails.add(attrPreferredMail.valueAsString());
			}
		}

		return emails;
	}

}
