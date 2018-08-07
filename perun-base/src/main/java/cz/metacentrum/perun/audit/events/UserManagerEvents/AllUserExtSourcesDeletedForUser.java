package cz.metacentrum.perun.audit.events.UserManagerEvents;

import cz.metacentrum.perun.audit.events.AuditEvent;
import cz.metacentrum.perun.core.api.User;

public class AllUserExtSourcesDeletedForUser extends AuditEvent {

	private final User user;
	private final String message;

	public AllUserExtSourcesDeletedForUser(User user) {
		this.user = user;
		this.message = String.format("All user ext sources removed for %s.", user);
	}

	@Override
	public String getMessage() {
		return message;
	}

	public User getUser() {
		return user;
	}

	@Override
	public String toString() {
		return message;
	}
}
