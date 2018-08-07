package cz.metacentrum.perun.audit.events.RegistrarManagerEvents;

import cz.metacentrum.perun.audit.events.AuditEvent;
import cz.metacentrum.perun.registrar.model.ApplicationForm;

public class FormItemDeleted extends AuditEvent {

	private final ApplicationForm form;
	private final String message;

	public FormItemDeleted(ApplicationForm form) {
		this.form = form;
		this.message = String.format("Application form item ID=%d voID=%d %s has been deleted", form.getId(),
				form.getVo().getId(), ((form.getGroup() != null) ? " groupID=" + form.getGroup().getId() : ""));
	}

	@Override
	public String getMessage() {
		return message;
	}

	public ApplicationForm getForm() {
		return form;
	}

	@Override
	public String toString() {
		return message;
	}
}
