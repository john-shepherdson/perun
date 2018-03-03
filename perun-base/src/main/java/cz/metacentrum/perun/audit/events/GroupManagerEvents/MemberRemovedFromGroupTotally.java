package cz.metacentrum.perun.audit.events.GroupManagerEvents;

import cz.metacentrum.perun.audit.events.AuditEvent;
import cz.metacentrum.perun.core.api.Group;
import cz.metacentrum.perun.core.api.Member;

public class MemberRemovedFromGroupTotally implements AuditEvent {

	private Member member;
	private Group group;
	private String name = this.getClass().getName();
	private String message;

	public MemberRemovedFromGroupTotally() {
	}

	public MemberRemovedFromGroupTotally(Member member, Group group) {
		this.member = member;
		this.group = group;
	}

	@Override
	public String getMessage() {
		return toString();
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public Member getMember() {
		return member;
	}

	public void setMember(Member member) {
		this.member = member;
	}

	public Group getGroup() {
		return group;
	}

	public void setGroup(Group group) {
		this.group = group;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@Override
	public String toString() {
		return member + "was removed from " + group + "totally.";
	}
}
