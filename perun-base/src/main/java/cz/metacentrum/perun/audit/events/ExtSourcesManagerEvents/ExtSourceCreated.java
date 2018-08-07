package cz.metacentrum.perun.audit.events.ExtSourcesManagerEvents;

import cz.metacentrum.perun.audit.events.AuditEvent;
import cz.metacentrum.perun.core.api.ExtSource;

public class ExtSourceCreated extends AuditEvent {

	private final ExtSource extSource;
	private final String message;

	public ExtSourceCreated(ExtSource extSource) {
		this.extSource = extSource;
		this.message = String.format("%s created.", extSource);
	}

	public ExtSource getExtSource() {
		return extSource;
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
