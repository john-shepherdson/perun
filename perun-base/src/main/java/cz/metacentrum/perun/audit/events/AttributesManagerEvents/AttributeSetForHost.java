package cz.metacentrum.perun.audit.events.AttributesManagerEvents;

import cz.metacentrum.perun.audit.events.AuditEvent;
import cz.metacentrum.perun.core.api.Attribute;
import cz.metacentrum.perun.core.api.Host;

/**
 * @author Vojtech Sassmann <vojtech.sassmann@gmail.com>
 */
public class AttributeSetForHost extends AuditEvent {

	private final Attribute attribute;
	private final Host host;
	private final String message;

	public AttributeSetForHost(Attribute attribute, Host host) {
		this.attribute = attribute;
		this.host = host;
		this.message = String.format("%s set for %s.", attribute, host);
	}

	public Attribute getAttribute() {
		return attribute;
	}

	public Host getHost() {
		return host;
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
