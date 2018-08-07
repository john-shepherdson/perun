package cz.metacentrum.perun.audit.events.GroupManagerEvents;

import cz.metacentrum.perun.audit.events.AuditEvent;
import cz.metacentrum.perun.core.api.Group;
import cz.metacentrum.perun.core.api.User;

public class AdminRemovedForGroup extends AuditEvent {

	private final User user;
	private final Group group;
	private final String message;

	public AdminRemovedForGroup(User user, Group group) {
		this.user = user;
		this.group = group;
		this.message = String.format("%s was removed from admins of %s.", user, group);
	}

	@Override
	public String getMessage() {
		return message;
	}

	public User getUser() {
		return user;
	}

	public Group getGroup() {
		return group;
	}

	@Override
	public String toString() {
		return message;
	}
}
