package cz.metacentrum.perun.core.impl.modules.attributes;

import cz.metacentrum.perun.core.api.Attribute;
import cz.metacentrum.perun.core.api.AttributeDefinition;
import cz.metacentrum.perun.core.api.AttributesManager;
import cz.metacentrum.perun.core.api.User;
import cz.metacentrum.perun.core.api.exceptions.AttributeDefinitionNotExistsException;
import cz.metacentrum.perun.core.api.exceptions.InternalErrorException;
import cz.metacentrum.perun.core.api.exceptions.WrongAttributeAssignmentException;
import cz.metacentrum.perun.core.api.exceptions.WrongAttributeValueException;
import cz.metacentrum.perun.core.api.exceptions.WrongReferenceAttributeValueException;
import cz.metacentrum.perun.core.impl.PerunSessionImpl;
import cz.metacentrum.perun.core.implApi.modules.attributes.UserVirtualAttributesModuleAbstract;

/**
 * Class for access def:login-namespace:einfraid-persistent-shadow attribute. It generates value if you call it for the first time.
 *
 * @author Pavel Zlámal <zlamal@cesnet.cz>
 */
public class urn_perun_user_attribute_def_virt_login_namespace_einfraid_persistent extends UserVirtualAttributesModuleAbstract {

	public static final String SHADOW = "urn:perun:user:attribute-def:def:login-namespace:einfraid-persistent-shadow";

	@Override
	public Attribute getAttributeValue(PerunSessionImpl sess, User user, AttributeDefinition attributeDefinition) throws InternalErrorException {
		Attribute elixirPersistent = new Attribute(attributeDefinition);

		try {
			Attribute elixirPersistentShadow = sess.getPerunBl().getAttributesManagerBl().getAttribute(sess, user, SHADOW);

			if (elixirPersistentShadow.getValue() == null) {

				elixirPersistentShadow = sess.getPerunBl().getAttributesManagerBl().fillAttribute(sess, user, elixirPersistentShadow);

				if (elixirPersistentShadow.getValue() == null) {
					throw new InternalErrorException("Einfra ID couldn't be set automatically");
				}
				sess.getPerunBl().getAttributesManagerBl().setAttribute(sess, user, elixirPersistentShadow);
			}

			elixirPersistent.setValue(elixirPersistentShadow.getValue());
			return elixirPersistent;

		} catch (WrongAttributeAssignmentException e) {
			throw new InternalErrorException(e);
		} catch (AttributeDefinitionNotExistsException e) {
			throw new InternalErrorException(e);
		} catch (WrongReferenceAttributeValueException e) {
			throw new InternalErrorException(e);
		} catch (WrongAttributeValueException e) {
			throw new InternalErrorException(e);
		}
	}

	public AttributeDefinition getAttributeDefinition() {
		AttributeDefinition attr = new AttributeDefinition();
		attr.setNamespace(AttributesManager.NS_USER_ATTR_VIRT);
		attr.setFriendlyName("login-namespace:einfraid-persistent");
		attr.setDisplayName("EINFRA ID login");
		attr.setType(String.class.getName());
		attr.setDescription("Login to EINFRAID. It is set automatically with first call.");
		return attr;
	}
}
