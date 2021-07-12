package cz.metacentrum.perun.core.impl.modules.attributes;

import cz.metacentrum.perun.core.AbstractPerunIntegrationTest;
import cz.metacentrum.perun.core.api.Attribute;
import cz.metacentrum.perun.core.api.Group;
import cz.metacentrum.perun.core.api.exceptions.WrongAttributeValueException;
import cz.metacentrum.perun.core.impl.PerunSessionImpl;
import org.junit.Before;
import org.junit.Test;

public class urn_perun_group_attribute_def_def_adGroupNameTest extends AbstractPerunIntegrationTest {

	private urn_perun_group_attribute_def_def_adGroupName classInstance;
	private Attribute attributeToCheck;
	private Group group;

	@Before
	public void setUp() throws Exception {
		classInstance = new urn_perun_group_attribute_def_def_adGroupName();
		attributeToCheck = new Attribute();
		group = new Group();

	}

	@Test(expected = WrongAttributeValueException.class)
	public void testWrongSyntax() throws Exception {
		System.out.println("testWrongSyntax()");
		attributeToCheck.setValue("bad@value");

		classInstance.checkAttributeSyntax((PerunSessionImpl) sess, group, attributeToCheck);
	}

	@Test
	public void testCorrectSyntax() throws Exception {
		System.out.println("testCorrectSyntax()");
		attributeToCheck.setValue("correctValue");

		classInstance.checkAttributeSyntax((PerunSessionImpl) sess, group, attributeToCheck);
	}

	@Test
	public void testCorrectSyntaxWithDash() throws Exception {
		System.out.println("testCorrectSyntax()");
		attributeToCheck.setValue("correct-Value-with-Dash");

		classInstance.checkAttributeSyntax((PerunSessionImpl) sess, group, attributeToCheck);
	}
}
