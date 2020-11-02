package cz.metacentrum.perun.registrar;

import cz.metacentrum.perun.audit.events.AuditEvent;
import cz.metacentrum.perun.audit.events.ExpirationNotifScheduler.SponsorshipExpirationInAMonth;
import cz.metacentrum.perun.audit.events.ExpirationNotifScheduler.SponsorshipExpirationInDays;
import cz.metacentrum.perun.core.api.Attribute;
import cz.metacentrum.perun.core.api.AttributeDefinition;
import cz.metacentrum.perun.core.api.AttributesManager;
import cz.metacentrum.perun.core.api.Candidate;
import cz.metacentrum.perun.core.api.EnrichedSponsorship;
import cz.metacentrum.perun.core.api.ExtSource;
import cz.metacentrum.perun.core.api.ExtSourcesManager;
import cz.metacentrum.perun.core.api.Group;
import cz.metacentrum.perun.core.api.Member;
import cz.metacentrum.perun.core.api.MemberGroupStatus;
import cz.metacentrum.perun.core.api.Role;
import cz.metacentrum.perun.core.api.Sponsorship;
import cz.metacentrum.perun.core.api.Status;
import cz.metacentrum.perun.core.api.User;
import cz.metacentrum.perun.core.api.UserExtSource;
import cz.metacentrum.perun.core.api.Vo;
import cz.metacentrum.perun.core.api.exceptions.AttributeNotExistsException;
import cz.metacentrum.perun.core.api.exceptions.GroupExistsException;
import cz.metacentrum.perun.core.api.exceptions.SponsorshipDoesNotExistException;
import cz.metacentrum.perun.core.blImpl.AuthzResolverBlImpl;
import cz.metacentrum.perun.core.impl.Auditer;
import cz.metacentrum.perun.core.impl.Compatibility;
import cz.metacentrum.perun.registrar.impl.ExpirationNotifScheduler;
import cz.metacentrum.perun.registrar.model.Application;
import cz.metacentrum.perun.registrar.model.ApplicationForm;
import cz.metacentrum.perun.registrar.model.ApplicationFormItemData;
import cz.metacentrum.perun.registrar.model.ApplicationFormItemWithPrefilledValue;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcPerunTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static cz.metacentrum.perun.core.implApi.modules.attributes.AbstractMembershipExpirationRulesModule.autoExtensionExtSources;
import static cz.metacentrum.perun.core.implApi.modules.attributes.AbstractMembershipExpirationRulesModule.autoExtensionLastLoginPeriod;
import static cz.metacentrum.perun.core.implApi.modules.attributes.AbstractMembershipExpirationRulesModule.membershipPeriodKeyName;
import static cz.metacentrum.perun.registrar.model.Application.AppType.INITIAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for Synchronizer component.
 *
 * @author Pavel Zlámal <zlamal@cesnet.cz>
 */
public class ExpirationNotifSchedulerTest extends RegistrarBaseIntegrationTest {

	private final static String CLASS_NAME = "ExpirationNotifSchedulerTest.";

	private final static String EXPIRATION_URN = "urn:perun:member:attribute-def:def:membershipExpiration";
	private final static String GROUP_EXPIRATION_URN = "urn:perun:member_group:attribute-def:def:groupMembershipExpiration";
	private final static String VO_MEMBERSHIP_RULES_URN = "urn:perun:vo:attribute-def:def:membershipExpirationRules";
	private final static String MEMBER_EXPIRATION_URN = "urn:perun:member:attribute-def:def:membershipExpiration";
	private static final String VO_APP_EXP_RULES = "urn:perun:vo:attribute-def:def:applicationExpirationRules";
	private static final String GROUP_APP_EXP_RULES = "urn:perun:group:attribute-def:def:applicationExpirationRules";
	private ExtSource extSource = new ExtSource(0, "testExtSource", ExtSourcesManager.EXTSOURCE_INTERNAL);
	private Vo vo = new Vo(0, "SynchronizerTestVo", "SyncTestVo");

	private ExpirationNotifScheduler scheduler;
	private ExpirationNotifScheduler spyScheduler;

	private Auditer auditerMock = mock(Auditer.class);

	private JdbcPerunTemplate jdbc;

	public ExpirationNotifScheduler getScheduler() {
		return scheduler;
	}

	@Autowired
	public void setScheduler(ExpirationNotifScheduler scheduler) {
		this.scheduler = scheduler;
		this.spyScheduler = spy(scheduler);
	}

	@Before
	public void setUp() throws Exception {
		jdbc = (JdbcPerunTemplate) ReflectionTestUtils.getField(scheduler, "jdbc");
		setUpExtSource();
		setUpVo();

		//set up member expiration attribute
		try {
			perun.getAttributesManager().getAttributeDefinition(session, EXPIRATION_URN);
		} catch (AttributeNotExistsException ex) {
			setUpMembershipExpirationAttribute();
		}

		//set up member group expiration attribute

		try {
			perun.getAttributesManager().getAttributeDefinition(session, GROUP_EXPIRATION_URN);
		} catch (AttributeNotExistsException ex) {
			setUpGroupMembershipExpirationAttribute();
		}

		ReflectionTestUtils.setField(spyScheduler.getPerun(), "auditer", auditerMock);
	}

	@After
	public void tearDown() {
		Mockito.reset(auditerMock, spyScheduler);
		validateMockitoUsage();
	}

	@Test
	public void checkMembersState() throws Exception {
		System.out.println(CLASS_NAME + "checkMembersState");

		// setup expiration date
		String today = LocalDate.now().toString();

		String tomorrow = LocalDate.now().plusDays(1).toString();

		String yesterday = LocalDate.now().minusDays(1).toString();

		Member member1 = setUpMember();
		Member member2 = setUpMember();
		Member member3 = setUpMember();
		Member member4 = setUpMember();
		Member member5 = setUpMember();
		Member member6 = setUpMember();
		Member member7 = setUpMember();
		Member member8 = setUpMember();
		Member member9 = setUpMember();
		Member member10 = setUpMember();
		Member member11 = setUpMember();
		Member member12 = setUpMember();
		Member member13 = setUpMember();
		Member member14 = setUpMember();
		Member member15 = setUpMember();

		// set expiration for today
		Attribute expiration = new Attribute(perun.getAttributesManager().getAttributeDefinition(session, EXPIRATION_URN));
		expiration.setValue(today);
		perun.getAttributesManager().setAttribute(session, member1, expiration);
		perun.getAttributesManager().setAttribute(session, member2, expiration);
		perun.getAttributesManager().setAttribute(session, member3, expiration);
		perun.getAttributesManager().setAttribute(session, member4, expiration);
		perun.getAttributesManager().setAttribute(session, member5, expiration);

		// set tomorrow expiration
		Attribute expirationTomorrow = new Attribute(perun.getAttributesManager().getAttributeDefinition(session, EXPIRATION_URN));
		expirationTomorrow.setValue(tomorrow);
		perun.getAttributesManager().setAttribute(session, member6, expirationTomorrow);
		perun.getAttributesManager().setAttribute(session, member7, expirationTomorrow);
		perun.getAttributesManager().setAttribute(session, member8, expirationTomorrow);
		perun.getAttributesManager().setAttribute(session, member9, expirationTomorrow);
		perun.getAttributesManager().setAttribute(session, member10, expirationTomorrow);

		// set yesterday expiration
		Attribute expirationYesterday = new Attribute(perun.getAttributesManager().getAttributeDefinition(session, EXPIRATION_URN));
		expirationYesterday.setValue(yesterday);
		perun.getAttributesManager().setAttribute(session, member11, expirationYesterday);
		perun.getAttributesManager().setAttribute(session, member12, expirationYesterday);
		perun.getAttributesManager().setAttribute(session, member13, expirationYesterday);
		perun.getAttributesManager().setAttribute(session, member14, expirationYesterday);
		perun.getAttributesManager().setAttribute(session, member15, expirationYesterday);

		// set status synchronizer should switch
		perun.getMembersManager().setStatus(session, member1, Status.VALID);   // expiration today
		perun.getMembersManager().setStatus(session, member6, Status.EXPIRED); // expiration tomorrow
		perun.getMembersManager().setStatus(session, member11, Status.VALID);  // expiration yesterday

		// set statuses synchronizer should ignore
		perun.getMembersManager().setStatus(session, member2, Status.DISABLED);
		perun.getMembersManager().setStatus(session, member3, Status.INVALID);
		perun.getMembersManager().setStatus(session, member7, Status.DISABLED);
		perun.getMembersManager().setStatus(session, member8, Status.INVALID);
		perun.getMembersManager().setStatus(session, member12, Status.DISABLED);
		perun.getMembersManager().setStatus(session, member13, Status.INVALID);

		// set status synchronizer should keep
		perun.getMembersManager().setStatus(session, member5, Status.EXPIRED);  // expiration today
		perun.getMembersManager().setStatus(session, member10, Status.VALID);   // expiration tomorrow
		perun.getMembersManager().setStatus(session, member15, Status.EXPIRED); // expiration yesterday

		// start switching members based on their state
		scheduler.checkMembersState();

		// check results
		Member returnedMember1 = perun.getMembersManager().getMemberById(session, member1.getId());
		assertEquals("Member1 should be expired now (from valid)!", returnedMember1.getStatus(), Status.EXPIRED);

		Member returnedMember2 = perun.getMembersManager().getMemberById(session, member2.getId());
		assertEquals("Member2 should be kept disabled!", returnedMember2.getStatus(), Status.DISABLED);

		Member returnedMember3 = perun.getMembersManager().getMemberById(session, member3.getId());
		assertEquals("Member3 should be kept invalid!", returnedMember3.getStatus(), Status.INVALID);

		Member returnedMember5 = perun.getMembersManager().getMemberById(session, member5.getId());
		assertEquals("Member5 should be kept expired!", returnedMember5.getStatus(), Status.EXPIRED);

		Member returnedMember6 = perun.getMembersManager().getMemberById(session, member6.getId());
		assertEquals("Member6 should be valid now (from expired)!", returnedMember6.getStatus(), Status.VALID);

		Member returnedMember7 = perun.getMembersManager().getMemberById(session, member7.getId());
		assertEquals("Member7 should be kept disabled!", returnedMember7.getStatus(), Status.DISABLED);

		Member returnedMember8 = perun.getMembersManager().getMemberById(session, member8.getId());
		assertEquals("Member8 should be kept invalid!", returnedMember8.getStatus(), Status.INVALID);

		Member returnedMember10 = perun.getMembersManager().getMemberById(session, member10.getId());
		assertEquals("Member10 should be kept valid!", returnedMember10.getStatus(), Status.VALID);

		Member returnedMember11 = perun.getMembersManager().getMemberById(session, member11.getId());
		assertEquals("Member11 should be expired now (from valid)!", returnedMember11.getStatus(), Status.EXPIRED);

		Member returnedMember12 = perun.getMembersManager().getMemberById(session, member12.getId());
		assertEquals("Member12 should be kept disabled!", returnedMember12.getStatus(), Status.DISABLED);

		Member returnedMember13 = perun.getMembersManager().getMemberById(session, member13.getId());
		assertEquals("Member13 should be kept invalid!", returnedMember13.getStatus(), Status.INVALID);

		Member returnedMember15 = perun.getMembersManager().getMemberById(session, member15.getId());
		assertEquals("Member15 should be kept expired!", returnedMember15.getStatus(), Status.EXPIRED);

	}

	@Test
	public void checkVoApplicationShouldBeAutoRejected() throws Exception {
		System.out.println(CLASS_NAME + "checkVoApplicationShouldBeAutoRejected");

		Application submitApp = setUpAndSubmitAppForPotentialAutoRejection(70, null, VO_APP_EXP_RULES);

		ReflectionTestUtils.invokeMethod(spyScheduler, "voApplicationsAutoRejection", Collections.singletonList(vo));

		// check results
		Application returnedApp = registrarManager.getApplicationById(session, submitApp.getId());
		assertEquals("Application should be rejected.", returnedApp.getState(), Application.AppState.REJECTED);
	}

	@Test
	public void checkVoApplicationShouldNotBeAutoRejected() throws Exception {
		System.out.println(CLASS_NAME + "checkVoApplicationShouldNotBeAutoRejected");

		Application submitApp = setUpAndSubmitAppForPotentialAutoRejection(50, null, VO_APP_EXP_RULES);

		ReflectionTestUtils.invokeMethod(spyScheduler, "voApplicationsAutoRejection", Collections.singletonList(vo));

		// check results
		Application returnedApp = registrarManager.getApplicationById(session, submitApp.getId());
		assertEquals("Application shouldn't be rejected.", returnedApp.getState(), Application.AppState.VERIFIED);
	}

	@Test
	public void checkGroupApplicationShouldBeAutoRejected() throws Exception {
		System.out.println(CLASS_NAME + "checkGroupApplicationShouldBeAutoRejected");

		Group group = deleteApplicationFormAndCreateGroup();

		Application submitApp = setUpAndSubmitAppForPotentialAutoRejection(70, group, GROUP_APP_EXP_RULES);

		ReflectionTestUtils.invokeMethod(spyScheduler, "groupApplicationsAutoRejection", Collections.singletonList(vo));

		// check results
		Application returnedApp = registrarManager.getApplicationById(session, submitApp.getId());
		assertEquals("Application should be rejected.", returnedApp.getState(), Application.AppState.REJECTED);
	}
	@Test
	public void checkGroupApplicationShouldNotBeAutoRejected() throws Exception {
		System.out.println(CLASS_NAME + "checkGroupApplicationShouldNotBeAutoRejected");

		Group group = deleteApplicationFormAndCreateGroup();

		Application submitApp = setUpAndSubmitAppForPotentialAutoRejection(50, group, GROUP_APP_EXP_RULES);

		ReflectionTestUtils.invokeMethod(spyScheduler, "groupApplicationsAutoRejection", Collections.singletonList(vo));

		// check results
		Application returnedApp = registrarManager.getApplicationById(session, submitApp.getId());
		assertEquals("Application should be rejected.", returnedApp.getState(), Application.AppState.VERIFIED);
	}


	@Test
	public void checkMembersGroupStateShouldBeValidatedToday() throws Exception {
		System.out.println(CLASS_NAME + "checkMembersGroupStateShouldBeValidatedToday");

		// setup expiration date
		String tomorrow = LocalDate.now().plusDays(1).toString();

		// set up member in group
		Member member1 = setUpMember();
		Group group = setUpGroup();
		perun.getGroupsManagerBl().addMember(session, group, member1);

		// set group expiration for tomorrow
		Attribute expiration = new Attribute(perun.getAttributesManager().getAttributeDefinition(session, GROUP_EXPIRATION_URN));
		expiration.setValue(tomorrow);
		perun.getAttributesManager().setAttribute(session, member1, group, expiration);

		perun.getGroupsManagerBl().expireMemberInGroup(session, member1, group);

		// Check init state
		MemberGroupStatus initMemberGroupStatus = perun.getGroupsManagerBl().getDirectMemberGroupStatus(session, member1, group);
		assertEquals("Member should be set to expired state before testing!", MemberGroupStatus.EXPIRED, initMemberGroupStatus);

		scheduler.checkMembersState();

		// Check if state was switched
		MemberGroupStatus memberGroupStatus = perun.getGroupsManagerBl().getDirectMemberGroupStatus(session, member1, group);
		assertEquals("Member should be valid now (from expired)!", MemberGroupStatus.VALID, memberGroupStatus);
	}

	@Test
	public void checkMembersGroupStateShouldBeValidatedTodayDoesNotAffectOthers() throws Exception {
		System.out.println(CLASS_NAME + "checkMembersGroupStateShouldBeValidatedToday");

		// setup expiration date
		String tomorrow = LocalDate.now().plusDays(1).toString();

		// set up member in group
		Member member1 = setUpMember();
		Member member2 = setUpMember();
		Group group = setUpGroup();
		perun.getGroupsManagerBl().addMember(session, group, member1);
		perun.getGroupsManagerBl().addMember(session, group, member2);

		// set group expiration for tomorrow
		Attribute m1Expiration = new Attribute(perun.getAttributesManager().getAttributeDefinition(session, GROUP_EXPIRATION_URN));
		m1Expiration.setValue(tomorrow);
		perun.getAttributesManager().setAttribute(session, member1, group, m1Expiration);

		// set group expiration for yesterday
		String yesterday = LocalDate.now().minusDays(1).toString();

		Attribute m2Expiration = new Attribute(perun.getAttributesManager().getAttributeDefinition(session, GROUP_EXPIRATION_URN));
		m2Expiration.setValue(yesterday);
		perun.getAttributesManager().setAttribute(session, member2, group, m2Expiration);

		perun.getGroupsManagerBl().expireMemberInGroup(session, member1, group);
		perun.getGroupsManagerBl().expireMemberInGroup(session, member2, group);

		scheduler.checkMembersState();

		// Check if state was not switched
		MemberGroupStatus memberGroupStatus = perun.getGroupsManagerBl().getDirectMemberGroupStatus(session, member2, group);
		assertEquals("Member should not be validated!", MemberGroupStatus.EXPIRED, memberGroupStatus);
	}

	@Test
	public void checkMembersGroupStateShouldExpireToday() throws Exception {
		System.out.println(CLASS_NAME + "checkMembersGroupStateShouldExpireToday");

		// setup expiration date to today
		String today = LocalDate.now().toString();

		// set up member in group
		Member member1 = setUpMember();
		Group group = setUpGroup();
		perun.getGroupsManagerBl().addMember(session, group, member1);

		// set group expiration for today
		Attribute expiration = new Attribute(perun.getAttributesManager().getAttributeDefinition(session, GROUP_EXPIRATION_URN));
		expiration.setValue(today);
		perun.getAttributesManager().setAttribute(session, member1, group, expiration);

		scheduler.checkMembersState();

		MemberGroupStatus memberGroupStatus = perun.getGroupsManagerBl().getDirectMemberGroupStatus(session, member1, group);

		assertEquals("Member should be expired now (from valid)!", MemberGroupStatus.EXPIRED, memberGroupStatus);
	}

	@Test
	public void checkMembersGroupStateShouldExpireTodayDoesNotAffectOthers() throws Exception {
		System.out.println(CLASS_NAME + "checkMembersGroupStateShouldExpireToday");

		// setup expiration date to today
		String today = LocalDate.now().toString();

		// set up member in group
		// set up member in group
		Member member1 = setUpMember();
		Member member2 = setUpMember();
		Group group = setUpGroup();
		perun.getGroupsManagerBl().addMember(session, group, member1);
		perun.getGroupsManagerBl().addMember(session, group, member2);

		// set group expiration for today
		Attribute m1Expiration = new Attribute(perun.getAttributesManager().getAttributeDefinition(session, GROUP_EXPIRATION_URN));
		m1Expiration.setValue(today);
		perun.getAttributesManager().setAttribute(session, member1, group, m1Expiration);

		// set group expiration for tomorrow
		String tomorrow = LocalDate.now().plusDays(1).toString();
		Attribute m2Expiration = new Attribute(perun.getAttributesManager().getAttributeDefinition(session, GROUP_EXPIRATION_URN));
		m1Expiration.setValue(tomorrow);
		perun.getAttributesManager().setAttribute(session, member2, group, m2Expiration);

		scheduler.checkMembersState();

		MemberGroupStatus memberGroupStatus = perun.getGroupsManagerBl().getDirectMemberGroupStatus(session, member2, group);

		assertEquals("Member should not be expired!", MemberGroupStatus.VALID, memberGroupStatus);
	}

	@Test
	public void testSponsorshipExpirationIsAudited1DayBefore() throws Exception {
		System.out.println(CLASS_NAME + "testSponsorshipExpirationIsAudited1DayBefore");
		testSponsorshipExpirationIsAuditedInDays(1);
	}

	@Test
	public void testSponsorshipExpirationIsAudited7DaysBefore() throws Exception {
		System.out.println(CLASS_NAME + "testSponsorshipExpirationIsAudited7DaysBefore");
		testSponsorshipExpirationIsAuditedInDays(7);
	}

	@Test
	public void testSponsorshipExpirationIsAudited14DaysBefore() throws Exception {
		System.out.println(CLASS_NAME + "testSponsorshipExpirationIsAudited14DaysBefore");
		testSponsorshipExpirationIsAuditedInDays(14);
	}

	@Test
	public void testSponsorshipExpirationIsAuditedAMonthBefore() throws Exception {
		System.out.println(CLASS_NAME + "testSponsorshipExpirationIsAuditedAMonthBefore");

		LocalDate today = LocalDate.of(2020, 2, 2);
		when(spyScheduler.getCurrentLocalDate())
				.thenReturn(today);

		Member member = setUpMember();
		User sponsor = perun.getUsersManagerBl().getUserByMember(session, setUpMember());
		AuthzResolverBlImpl.setRole(session, sponsor, vo, Role.SPONSOR);

		LocalDate nextDay = today.plusDays(28);
		perun.getMembersManagerBl().setSponsorshipForMember(session, member, sponsor, nextDay);

		ReflectionTestUtils.invokeMethod(spyScheduler, "auditSponsorshipExpirations");

		EnrichedSponsorship es = new EnrichedSponsorship();
		es.setSponsoredMember(perun.getMembersManagerBl().getMemberById(session, member.getId()));
		es.setSponsor(perun.getUsersManagerBl().getUserById(session, sponsor.getId()));
		AuditEvent expectedEvent = new SponsorshipExpirationInAMonth(es);

		verify(auditerMock).log(any(), eq(expectedEvent));
	}

	@Test
	public void testSponsorshipExpires() throws Exception {
		System.out.println(CLASS_NAME + "testSponsorshipExpires");

		LocalDate today = LocalDate.of(2020, 2, 2);
		when(spyScheduler.getCurrentLocalDate())
				.thenReturn(today);

		Member member = setUpMember();
		User sponsor = perun.getUsersManagerBl().getUserByMember(session, setUpMember());
		AuthzResolverBlImpl.setRole(session, sponsor, vo, Role.SPONSOR);

		LocalDate lastMonth = today.minusMonths(1);
		perun.getMembersManagerBl().setSponsorshipForMember(session, member, sponsor, lastMonth);

		Sponsorship sponsorshipBefore = perun.getMembersManagerBl().getSponsorship(session, member, sponsor);
		assertThat(sponsorshipBefore.isActive());

		ReflectionTestUtils.invokeMethod(spyScheduler, "expireSponsorships");

		assertThatExceptionOfType(SponsorshipDoesNotExistException.class)
				.isThrownBy(() -> perun.getMembersManagerBl().getSponsorship(session, member, sponsor));
	}

	@Test
	public void testSponsorshipDoesntExpireToday() throws Exception {
		System.out.println(CLASS_NAME + "testSponsorshipDoesntExpireToday");

		LocalDate today = LocalDate.of(2020, 2, 2);
		when(spyScheduler.getCurrentLocalDate())
				.thenReturn(today);

		Member member = setUpMember();
		User sponsor = perun.getUsersManagerBl().getUserByMember(session, setUpMember());
		AuthzResolverBlImpl.setRole(session, sponsor, vo, Role.SPONSOR);

		perun.getMembersManagerBl().setSponsorshipForMember(session, member, sponsor, today);

		ReflectionTestUtils.invokeMethod(spyScheduler, "expireSponsorships");

		Sponsorship sponsorship = perun.getMembersManagerBl().getSponsorship(session, member, sponsor);
		assertThat(sponsorship.isActive());
	}

	@Test
	public void testSponsorshipDoesntExpireInFuture() throws Exception {
		System.out.println(CLASS_NAME + "testSponsorshipDoesntExpireInFuture");

		LocalDate today = LocalDate.of(2020, 2, 2);
		when(spyScheduler.getCurrentLocalDate())
				.thenReturn(today);

		Member member = setUpMember();
		User sponsor = perun.getUsersManagerBl().getUserByMember(session, setUpMember());
		AuthzResolverBlImpl.setRole(session, sponsor, vo, Role.SPONSOR);

		LocalDate nextYear = today.plusYears(1);
		perun.getMembersManagerBl().setSponsorshipForMember(session, member, sponsor, nextYear);

		ReflectionTestUtils.invokeMethod(spyScheduler, "expireSponsorships");

		Sponsorship sponsorship = perun.getMembersManagerBl().getSponsorship(session, member, sponsor);
		assertThat(sponsorship.isActive());
	}

	@Test
	public void testAutoExtensionExtendsMemberWithLastAccess() throws Exception {
		System.out.println(CLASS_NAME + "testAutoExtensionExtendsMemberWithLastAccess");

		autoExpirationTest(
				voRules -> {
					voRules.put(membershipPeriodKeyName, "+3m");
					voRules.put(autoExtensionLastLoginPeriod, "3m");
				},
				userExtSources -> {
					for (UserExtSource userExtSource : userExtSources) {
						jdbc.update("update user_ext_sources set last_access='1996-01-01 00:00:00.1' where id=?",
								userExtSource.getId());
					}
				},
				(oldExpiration, newExpiration) -> assertThat(newExpiration).isAfter(oldExpiration));
	}

	@Test
	public void testAutoExtensionDoesNotExtendMemberWithoutLastAccess() throws Exception {
		System.out.println(CLASS_NAME + "testAutoExtensionDoesNotExtendMemberWithoutLastAccess");

		autoExpirationTest(
				voRules -> {
					voRules.put(membershipPeriodKeyName, "+3m");
					voRules.put(autoExtensionLastLoginPeriod, "3m");
				},
				userExtSources -> {
					for (UserExtSource userExtSource : userExtSources) {
						jdbc.update("update user_ext_sources set last_access='1995-01-01 00:00:00.1' where id=?",
								userExtSource.getId());
					}
				},
				(oldExpiration, newExpiration) -> assertThat(newExpiration).isEqualTo(oldExpiration));
	}

	@Test
	public void testAutoExtensionDoesNotExtendForUnspecifiedExtSource() throws Exception {
		System.out.println(CLASS_NAME + "testAutoExtensionDoesNotExtendForUnspecifiedExtSource");

		autoExpirationTest(
				voRules -> {
					voRules.put(autoExtensionExtSources, String.valueOf(extSource.getId()));
					voRules.put(membershipPeriodKeyName, "+3m");
					voRules.put(autoExtensionLastLoginPeriod, "3m");
				},
				userExtSources -> {
					for (UserExtSource userExtSource : userExtSources) {
						if (userExtSource.getExtSource().equals(extSource)) {
							jdbc.update("update user_ext_sources set last_access='1995-01-01 00:00:00.1' where id=?",
									userExtSource.getId());
						} else {
							jdbc.update("update user_ext_sources set last_access='1996-01-01 00:00:00.1' where id=?",
									userExtSource.getId());
						}
					}
				},
				(oldExpiration, newExpiration) -> assertThat(newExpiration).isEqualTo(oldExpiration));
	}

	@Test
	public void testAutoExtensionExtendForSpecifiedExtSource() throws Exception {
		System.out.println(CLASS_NAME + "testAutoExtensionExtendForSpecifiedExtSource");

		autoExpirationTest(
				voRules -> {
					voRules.put(autoExtensionExtSources, String.valueOf(extSource.getId()));
					voRules.put(membershipPeriodKeyName, "+3m");
					voRules.put(autoExtensionLastLoginPeriod, "3m");
				},
				userExtSources -> {
					for (UserExtSource userExtSource : userExtSources) {
						if (userExtSource.getExtSource().equals(extSource)) {
							jdbc.update("update user_ext_sources set last_access='1996-01-01 00:00:00.1' where id=?",
									userExtSource.getId());
						} else {
							jdbc.update("update user_ext_sources set last_access='1995-01-01 00:00:00.1' where id=?",
									userExtSource.getId());
						}
					}
				},
				(oldExpiration, newExpiration) -> assertThat(newExpiration).isAfter(oldExpiration));
	}


	/**
	 * Performs test of sponsorship expiration being audited n days before its expiration.
	 *
	 * @param days number of days before the expiration
	 */
	private void testSponsorshipExpirationIsAuditedInDays(int days) throws Exception {
		LocalDate today = LocalDate.of(2020, 2, 2);
		when(spyScheduler.getCurrentLocalDate())
				.thenReturn(today);

		Member member = setUpMember();
		User sponsor = perun.getUsersManagerBl().getUserByMember(session, setUpMember());
		AuthzResolverBlImpl.setRole(session, sponsor, vo, Role.SPONSOR);

		LocalDate nextDay = today.plusDays(days);
		perun.getMembersManagerBl().setSponsorshipForMember(session, member, sponsor, nextDay);

		ReflectionTestUtils.invokeMethod(spyScheduler, "auditSponsorshipExpirations");

		EnrichedSponsorship es = new EnrichedSponsorship();
		es.setSponsoredMember(perun.getMembersManagerBl().getMemberById(session, member.getId()));
		es.setSponsor(perun.getUsersManagerBl().getUserById(session, sponsor.getId()));
		AuditEvent expectedEvent = new SponsorshipExpirationInDays(es, days);

		verify(auditerMock).log(any(), eq(expectedEvent));
	}

	/**
	 * Perform autoExpiration test.
	 *
	 * @param voExpirationRulesModifier takes vo's member expiration rules attribute value, can be used to modify the
	 *                                  vo's expiration rules
	 * @param uesModifier takes a list of userExtSources of the tested user, can be used to modify them
	 * @param expirationVerifier takes the expirationDate before the auto extension, and after the extension.
	 *                           Can be used to test how the expiration is changed.
	 */
	private void autoExpirationTest(Consumer<Map<String, String>> voExpirationRulesModifier,
	                                Consumer<List<UserExtSource>> uesModifier,
	                                BiConsumer<LocalDate, LocalDate> expirationVerifier) throws Exception {
		System.out.println(CLASS_NAME + "testAutoExtensionDoesNotExtendsMemberWithoutLastAccess");

		LocalDate today = LocalDate.of(1996, 2, 12);
		when(spyScheduler.getCurrentLocalDate())
				.thenReturn(today);

		setUpVoExpirationRulesAttribute(voExpirationRulesModifier);

		Member member = setUpMember();

		LocalDate oldExpiration = today.plusDays(7);
		Attribute memberExpiration =
				new Attribute(perun.getAttributesManager().getAttributeDefinition(session, MEMBER_EXPIRATION_URN));
		memberExpiration.setValue(oldExpiration.toString());
		perun.getAttributesManager().setAttribute(session, member, memberExpiration);

		User user = perun.getUsersManagerBl().getUserByMember(session, member);

		List<UserExtSource> userExtSources = perun.getUsersManagerBl().getUserExtSources(session, user);

		uesModifier.accept(userExtSources);

		List<Vo> vos = perun.getVosManagerBl().getVos(session);
		ReflectionTestUtils.invokeMethod(spyScheduler, "performAutoExtension", vos);

		Attribute newMemberExpAttr = perun.getAttributesManager().getAttribute(session, member, MEMBER_EXPIRATION_URN);

		LocalDate newExpirationDate = LocalDate.parse(newMemberExpAttr.valueAsString());
		expirationVerifier.accept(oldExpiration, newExpirationDate);
	}

	private void setUpVoExpirationRulesAttribute(Consumer<Map<String, String>> voExpirationRulesModifier) throws Exception {
		Attribute voRulesAttribute = new Attribute(
				perun.getAttributesManagerBl().getAttributeDefinition(session, VO_MEMBERSHIP_RULES_URN));
		Map<String, String> value = new LinkedHashMap<>();
		voExpirationRulesModifier.accept(value);
		voRulesAttribute.setValue(value);

		perun.getAttributesManagerBl().setAttribute(session, vo, voRulesAttribute);
	}

	// ----------------- PRIVATE METHODS -------------------------------------------

	private Group setUpGroup() throws Exception {
		Group group = new Group();
		group.setName("Test group");
		return perun.getGroupsManagerBl().createGroup(session, vo, group);
	}

	private Member setUpMember() throws Exception {

		Candidate candidate = new Candidate();
		candidate.setFirstName(Long.toHexString(Double.doubleToLongBits(Math.random())));
		candidate.setId(0);
		candidate.setMiddleName("");
		candidate.setLastName(Long.toHexString(Double.doubleToLongBits(Math.random())));
		candidate.setTitleBefore("");
		candidate.setTitleAfter("");
		final UserExtSource userExtSource = new UserExtSource(extSource, Long.toHexString(Double.doubleToLongBits(Math.random())));
		candidate.setUserExtSource(userExtSource);
		candidate.setAttributes(new HashMap<>());

		return perun.getMembersManagerBl().createMemberSync(session, vo, candidate);

	}

	private void setUpExtSource() throws Exception {
		extSource = perun.getExtSourcesManager().createExtSource(session, extSource, null);
	}

	private void setUpVo() throws Exception {
		vo = perun.getVosManager().createVo(session, vo);
		perun.getExtSourcesManager().addExtSource(session, vo, extSource);
	}

	private AttributeDefinition setUpMembershipExpirationAttribute() throws Exception {

		AttributeDefinition attr = new AttributeDefinition();
		attr.setNamespace("urn:perun:member:attribute-def:def");
		attr.setFriendlyName("membershipExpiration");
		attr.setType(String.class.getName());
		attr.setDisplayName("Membership expiration");
		attr.setDescription("Membership expiration date.");

		return perun.getAttributesManager().createAttribute(session, attr);

	}

	private AttributeDefinition setUpGroupMembershipExpirationAttribute() throws Exception {

		AttributeDefinition attr = new AttributeDefinition();
		attr.setNamespace(AttributesManager.NS_MEMBER_GROUP_ATTR_DEF);
		attr.setFriendlyName("groupMembershipExpiration");
		attr.setType(String.class.getName());
		attr.setDisplayName("Group membership expiration");
		attr.setDescription("When the member expires in group, format YYYY-MM-DD.");

		return perun.getAttributesManager().createAttribute(session, attr);
	}

	/**
	 * In test DB in application_form is created unique index for vo_id without dependency on group_id, so if you
	 * want to create application form for group, firstly you have to delete automatically created form for Vo.
	 * Then method creates new group.
	 *
	 * @return created group
	 * @throws GroupExistsException
	 */
	private Group deleteApplicationFormAndCreateGroup() throws GroupExistsException {
		// delete automatically created form for Vo
		if (Compatibility.isHSQLDB()) {
			jdbc.update("DELETE from application_form WHERE vo_id = ?", vo.getId());
		}

		Group group = new Group();
		group.setName("Group for apply");
		return perun.getGroupsManagerBl().createGroup(session, vo, group);
	}

	/**
	 * Converts object ApplicationFormItemWithPrefillValue to ApplicationFormItemData
	 *
	 * @param object ApplicationFormItemWithPrefilledValue to convert
	 * @return converted object ApplicationFormItemData
	 */
	private ApplicationFormItemData convertAppFormItemWithPrefValToAppFormItemData(ApplicationFormItemWithPrefilledValue object) {
		return new ApplicationFormItemData(object.getFormItem(), object.getFormItem().getShortname(), "", "0");
	}

	/**
	 * In the first step method sets the return value for getCurrentLocalDay method. Then sets the application expiration
	 * attribute for Vo or Group and creates and submits application.
	 *
	 * @param days number of days for move today
	 * @param group optional group
	 * @param attribute application expiration attribute for Vo or Group
	 * @return submitted application
	 * @throws Exception
	 */
	private Application setUpAndSubmitAppForPotentialAutoRejection(int days, Group group, String attribute) throws Exception {
		// change today date for test
		LocalDate today = LocalDate.now().plusDays(days);
		when(spyScheduler.getCurrentLocalDate())
			.thenReturn(today);

		// set application expiration attribute for Vo
		Attribute appExp = new Attribute(perun.getAttributesManager().getAttributeDefinition(session, attribute));
		Map<String, String> attrValue = new LinkedHashMap<>();
		attrValue.put("emailVerification", "14");
		attrValue.put("ignoredByAdmin", "60");
		appExp.setValue(attrValue);
		if (group != null) {
			perun.getAttributesManagerBl().setAttribute(session, group, appExp);
		} else {
			perun.getAttributesManagerBl().setAttribute(session, vo, appExp);
		}

		// create application
		Application application = new Application();
		application.setType(INITIAL);
		application.setCreatedBy("testUser");
		application.setVo(vo);

		// get application form item data
		ApplicationForm applicationForm;
		if (group != null) {
			application.setGroup(group);
			registrarManager.createApplicationFormInGroup(session, group);
			applicationForm = registrarManager.getFormForGroup(group);
		} else {
			applicationForm = registrarManager.getFormForVo(vo);
		}
		List<ApplicationFormItemData> data = registrarManager.getFormItemsWithPrefilledValues(session, INITIAL, applicationForm).stream()
			.map(this::convertAppFormItemWithPrefValToAppFormItemData)
			.collect(Collectors.toList());

		return registrarManager.submitApplication(session, application, data);
	}
}
