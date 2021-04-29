package cz.metacentrum.perun.core.impl.modules.attributes;

import cz.metacentrum.perun.core.api.Attribute;
import cz.metacentrum.perun.core.api.AttributeDefinition;
import cz.metacentrum.perun.core.api.AttributesManager;
import cz.metacentrum.perun.core.api.Group;
import cz.metacentrum.perun.core.api.Vo;
import cz.metacentrum.perun.core.api.exceptions.InternalErrorException;
import cz.metacentrum.perun.core.api.exceptions.VoNotExistsException;
import cz.metacentrum.perun.core.impl.PerunSessionImpl;
import cz.metacentrum.perun.core.implApi.modules.attributes.GroupVirtualAttributesModuleAbstract;
import cz.metacentrum.perun.core.implApi.modules.attributes.GroupVirtualAttributesModuleImplApi;

public class urn_perun_group_attribute_def_virt_autoRegistrationEnabled extends GroupVirtualAttributesModuleAbstract implements GroupVirtualAttributesModuleImplApi {

	@Override
	public Attribute getAttributeValue(PerunSessionImpl sess, Group group, AttributeDefinition attributeDefinition) {
		Attribute attribute = new Attribute(attributeDefinition);
		Boolean value = null;
		if (isAutoRegistrationActiveInVo(sess, group)) {
			value = sess.getPerunBl().getGroupsManagerBl().isGroupForAutoRegistration(sess, group);
		}
		attribute.setValue(value);
		return attribute;
	}


	private boolean isAutoRegistrationActiveInVo(PerunSessionImpl sess, Group group) {
		Vo vo;
		try {
			vo = sess.getPerunBl().getVosManagerBl().getVoById(sess, group.getVoId());
		} catch (VoNotExistsException e) {
			throw new InternalErrorException(e);
		}

		return sess.getPerunBl().getVosManagerBl().usesEmbeddedGroupRegistrations(sess, vo);
	}

	@Override
	public AttributeDefinition getAttributeDefinition() {
		AttributeDefinition attr = new AttributeDefinition();
		attr.setNamespace(AttributesManager.NS_GROUP_ATTR_VIRT);
		attr.setType(Boolean.class.getName());
		attr.setFriendlyName("autoRegistrationEnabled");
		attr.setDisplayName("Automatic registration enabled");
		attr.setDescription("If true, group can be selected for automatic registration.");
		return attr;
	}
}
