package cz.metacentrum.perun.audit.events.AttributesManagerEvents;

import cz.metacentrum.perun.audit.events.AuditEvent;
import cz.metacentrum.perun.core.api.AttributeDefinition;
import cz.metacentrum.perun.core.api.Vo;

/**
 * @author Vojtech Sassmann <vojtech.sassmann@gmail.com>
 */
public class AttributeRemovedForVo extends AuditEvent {

	private final AttributeDefinition attribute;
	private final Vo vo;
	private final String message;

	public AttributeRemovedForVo(AttributeDefinition attribute, Vo vo) {
		this.attribute = attribute;
		this.vo = vo;
		this.message = String.format("%s removed for %s.", attribute, vo);
	}

	public AttributeDefinition getAttribute() {
		return attribute;
	}

	public Vo getVo() {
		return vo;
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
