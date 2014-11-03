package cz.metacentrum.perun.core.impl.modules.attributes;

import cz.metacentrum.perun.core.api.AttributeDefinition;
import cz.metacentrum.perun.core.api.AttributesManager;
import cz.metacentrum.perun.core.implApi.modules.attributes.ResourceAttributesModuleAbstract;
import cz.metacentrum.perun.core.implApi.modules.attributes.ResourceAttributesModuleImplApi;

/**
 *
 * @author Sona Mastrakova <sona.mastrakova@gmail.com>
 */
public class urn_perun_resource_attribute_def_def_rootMailAliases extends ResourceAttributesModuleAbstract implements ResourceAttributesModuleImplApi {

    public AttributeDefinition getAttributeDefinition() {
        AttributeDefinition attr = new AttributeDefinition();
        attr.setNamespace(AttributesManager.NS_RESOURCE_ATTR_DEF);
        attr.setFriendlyName("rootMailAliases");
        attr.setDisplayName("Set root aliases instead of users.");
        attr.setType(String.class.getName());
        attr.setDescription("Set root aliases instead of users.");
        return attr;
    }
}
