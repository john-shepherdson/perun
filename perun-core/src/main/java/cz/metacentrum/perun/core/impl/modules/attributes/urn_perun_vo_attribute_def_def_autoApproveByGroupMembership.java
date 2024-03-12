package cz.metacentrum.perun.core.impl.modules.attributes;

import cz.metacentrum.perun.core.api.Attribute;
import cz.metacentrum.perun.core.api.AttributeDefinition;
import cz.metacentrum.perun.core.api.AttributesManager;
import cz.metacentrum.perun.core.api.PerunBean;
import cz.metacentrum.perun.core.api.Vo;
import cz.metacentrum.perun.core.api.exceptions.WrongAttributeValueException;
import cz.metacentrum.perun.core.api.exceptions.WrongReferenceAttributeValueException;
import cz.metacentrum.perun.core.impl.PerunSessionImpl;
import cz.metacentrum.perun.core.implApi.modules.attributes.VoAttributesModuleAbstract;
import cz.metacentrum.perun.core.implApi.modules.attributes.VoAttributesModuleImplApi;
import java.util.ArrayList;
import java.util.List;

/**
 * Module for list of group IDs in which user is already valid member, his application to any group in Vo will be
 * automatically approved.
 *
 * @author Sarka Palkovicova <sarka.palkovicova@gmail.com>
 */
public class urn_perun_vo_attribute_def_def_autoApproveByGroupMembership extends VoAttributesModuleAbstract
    implements VoAttributesModuleImplApi {

  @Override
  public void checkAttributeSemantics(PerunSessionImpl sess, Vo vo, Attribute attribute)
      throws WrongReferenceAttributeValueException {
    if (attribute.getValue() == null) {
      return;
    }
    try {
      List<Integer> groupIds =
          sess.getPerunBl().getGroupsManagerBl().getGroups(sess, vo).stream().map(PerunBean::getId).toList();
      for (String id : attribute.valueAsList()) {
        if (!groupIds.contains(Integer.valueOf(id))) {
          throw new WrongReferenceAttributeValueException("Group with ID " + id + " does not exist.");
        }
      }
    } catch (NumberFormatException ex) {
      throw new InternalError(ex);
    }
  }

  @Override
  public void checkAttributeSyntax(PerunSessionImpl sess, Vo vo, Attribute attribute)
      throws WrongAttributeValueException {
    if (attribute.getValue() == null) {
      return;
    }

    for (String id : attribute.valueAsList()) {
      try {
        Integer.valueOf(id);
      } catch (NumberFormatException ex) {
        throw new WrongAttributeValueException(id + " is not a correct group id.");
      }
    }
  }

  @Override
  public AttributeDefinition getAttributeDefinition() {
    AttributeDefinition attr = new AttributeDefinition();
    attr.setNamespace(AttributesManager.NS_VO_ATTR_DEF);
    attr.setType(ArrayList.class.getName());
    attr.setFriendlyName("autoApproveByGroupMembership");
    attr.setDisplayName("Auto approve by group membership");
    attr.setDescription(
        "List of group IDs in which if the user is already a valid member, their application to any group in Vo will " +
        "be automatically approved.");
    return attr;
  }
}
