package cz.metacentrum.perun.core.impl.modules.attributes;

import cz.metacentrum.perun.core.api.Attribute;
import cz.metacentrum.perun.core.api.Member;
import cz.metacentrum.perun.core.api.User;
import cz.metacentrum.perun.core.api.exceptions.WrongAttributeValueException;
import cz.metacentrum.perun.core.impl.PerunSessionImpl;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static cz.metacentrum.perun.core.impl.modules.attributes.urn_perun_member_attribute_def_def_o365EmailAddresses_mu.UCO_ATTRIBUTE;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests attribute module.
 *
 * @author Martin Kuba makub@ics.muni.cz
 */
public class urn_perun_member_attribute_def_def_o365EmailAddresses_muTest {

	private urn_perun_member_attribute_def_def_o365EmailAddresses_mu classInstance;
	private PerunSessionImpl session;
	private Attribute attributeToCheck;
	private String uco = "123456";
	private User user = new User(10, "Joe", "Doe", "W.", "", "");
	private Member member = new Member(1, user.getId());

	@Before
	public void setUp() {
		classInstance = new urn_perun_member_attribute_def_def_o365EmailAddresses_mu();
		session = mock(PerunSessionImpl.class, RETURNS_DEEP_STUBS);
		attributeToCheck = new Attribute();
	}

	@Test
	public void fillAttribute() throws Exception {
		System.out.println("fillAttribute()");

		when(session.getPerunBl().getUsersManagerBl().getUserById(session, member.getUserId())).thenReturn(user);
		when(session.getPerunBl().getAttributesManagerBl().getAttribute(session, user, UCO_ATTRIBUTE).getValue()).thenReturn(uco);

		Attribute attribute = classInstance.fillAttribute(session, member, classInstance.getAttributeDefinition());
		Object attributeValue = attribute.getValue();
		assertThat(attributeValue, is(notNullValue()));
		List<String> expectedValue = new ArrayList<>();
		expectedValue.add(uco + "@muni.cz");
		assertThat(attributeValue, equalTo(expectedValue));
	}

	@Test(expected = WrongAttributeValueException.class)
	public void testCheckNull() throws Exception {
		System.out.println("testCheckNull()");
		attributeToCheck.setValue(null);
		classInstance.checkAttributeValue(session, member, attributeToCheck);
	}

	@Test(expected = WrongAttributeValueException.class)
	public void testCheckType() throws Exception {
		System.out.println("testCheckType()");
		attributeToCheck.setValue("AAA");
		classInstance.checkAttributeValue(session, member, attributeToCheck);
	}

	@Test(expected = WrongAttributeValueException.class)
	public void testCheckEmailSyntax() throws Exception {
		System.out.println("testCheckEmailSyntax()");
		attributeToCheck.setValue(Arrays.asList("my@example.com", "a/-+"));
		classInstance.checkAttributeValue(session, member, attributeToCheck);
	}

	@Test(expected = WrongAttributeValueException.class)
	public void testCheckDuplicates() throws Exception {
		System.out.println("testCheckDuplicates()");
		attributeToCheck.setValue(Arrays.asList("my@example.com", "aaa@bbb.com", "my@example.com"));
		classInstance.checkAttributeValue(session, member, attributeToCheck);
	}

	@Test(expected = WrongAttributeValueException.class)
	public void testCheckMissingUco() throws Exception {
		System.out.println("testCheckMissingUco()");
		attributeToCheck.setValue(Arrays.asList("my@example.com", "aaa@bbb.com"));
		when(session.getPerunBl().getUsersManagerBl().getUserById(session, member.getUserId())).thenReturn(user);
		when(session.getPerunBl().getAttributesManagerBl().getAttribute(session, user, UCO_ATTRIBUTE).getValue()).thenReturn(uco);
		classInstance.checkAttributeValue(session, member, attributeToCheck);
	}

	@Test
	public void testCorrect() throws Exception {
		System.out.println("testCorrect()");
		attributeToCheck.setValue(Arrays.asList("my@example.com", "aaa@bbb.com", uco + "@muni.cz"));
		when(session.getPerunBl().getUsersManagerBl().getUserById(session, member.getUserId())).thenReturn(user);
		when(session.getPerunBl().getAttributesManagerBl().getAttribute(session, user, UCO_ATTRIBUTE).getValue()).thenReturn(uco);
		classInstance.checkAttributeValue(session, member, attributeToCheck);
	}



}
