package cz.metacentrum.perun.audit.events.AttributesManagerEvents;

import cz.metacentrum.perun.audit.events.AuditEvent;
import cz.metacentrum.perun.core.api.Attribute;
import cz.metacentrum.perun.core.api.Resource;

/**
 * @author Vojtech Sassmann <vojtech.sassmann@gmail.com>
 */
public class AttributeSetForResource extends AuditEvent {

	private final Attribute attribute;
	private final Resource resource;
	private final String message;

	public AttributeSetForResource(Attribute attribute, Resource resource) {
		this.attribute = attribute;
		this.resource = resource;
		this.message = String.format("%s set for %s.", attribute, resource);
	}

	public Attribute getAttribute() {
		return attribute;
	}

	public Resource getResource() {
		return resource;
	}

	@Override
	public String getMessage() {
		return message;
	}

	@Override
	public String toString() {
		return message;
	}
}
