package cz.metacentrum.perun.webgui.tabs.memberstabs;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.i18n.client.DateTimeFormat;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.user.client.ui.*;
import com.google.gwt.user.datepicker.client.DatePicker;
import cz.metacentrum.perun.webgui.client.PerunWebSession;
import cz.metacentrum.perun.webgui.client.localization.ButtonTranslation;
import cz.metacentrum.perun.webgui.client.resources.ButtonType;
import cz.metacentrum.perun.webgui.client.resources.SmallIcons;
import cz.metacentrum.perun.webgui.json.JsonCallbackEvents;
import cz.metacentrum.perun.webgui.json.attributesManager.SetAttributes;
import cz.metacentrum.perun.webgui.model.Attribute;
import cz.metacentrum.perun.webgui.model.RichMember;
import cz.metacentrum.perun.webgui.tabs.TabItem;
import cz.metacentrum.perun.webgui.widgets.CustomButton;
import cz.metacentrum.perun.webgui.widgets.TabMenu;
import cz.metacentrum.perun.webgui.widgets.TabPanelForTabItems;

import java.util.*;

/**
 * Inner tab for changing members membership expiration
 * !! USE AS INNER TAB ONLY !!
 *
 * @author Pavel Zlamal <256627@mail.muni.cz>
 * @version $Id$
 */
public class MembershipExpirationTabItem implements TabItem {

    private RichMember member;
    private int memberId;
    private PerunWebSession session = PerunWebSession.getInstance();
    private SimplePanel contentWidget = new SimplePanel();
    private Label titleWidget = new Label("Loading member");
    TabPanelForTabItems tabPanel;
    private JsonCallbackEvents events = new JsonCallbackEvents();

    /**
     * Constructor
     *
     * @param member RichMember object, typically from table
     * @param events Events triggered when status is changed
     */
    public MembershipExpirationTabItem(RichMember member, JsonCallbackEvents events){
        this.member = member;
        this.memberId = member.getId();
        this.events = events;
        this.tabPanel = new TabPanelForTabItems(this);
    }

    public boolean isPrepared(){
        return !(member == null);
    }

    public Widget draw() {

        this.titleWidget.setText("Set membership expiration");

        VerticalPanel vp = new VerticalPanel();
        vp.setSize("350px", "100%");

        final FlexTable layout = new FlexTable();
        layout.setSize("100%","100%");

        layout.setStyleName("inputFormFlexTable");

        layout.setHTML(0, 0, "Current expiration:");
        layout.getFlexCellFormatter().setStyleName(0, 0, "itemName");

        layout.setHTML(0, 1, member.getStatus());

        final Attribute expire = member.getAttribute("urn:perun:member:attribute-def:def:membershipExpiration");
        if (expire != null && !"null".equalsIgnoreCase(expire.getValue())) {
            layout.setHTML(0, 1, expire.getValue());
        } else {
            layout.setHTML(0, 1, "<i>never</i>");
        }

        layout.setHTML(1, 0, "New expiration:");
        layout.getFlexCellFormatter().setStyleName(1, 0, "itemName");

        final CustomButton changeButton = new CustomButton("Save", "Save changes in membership expiration date", SmallIcons.INSTANCE.diskIcon());

        final DatePicker picker = new DatePicker();
        picker.setWidth("100%");
        layout.getFlexCellFormatter().setColSpan(2, 0, 2);
        layout.setWidget(2, 0, picker);

        picker.addValueChangeHandler(new ValueChangeHandler<Date>() {
            @Override
            public void onValueChange(ValueChangeEvent<Date> dateValueChangeEvent) {
                layout.setHTML(1, 1, DateTimeFormat.getFormat("yyyy-MM-dd HH:mm:ss.S").format(picker.getValue()));
                changeButton.setEnabled(true);
            }
        });

        try {
            // set values if possible
            picker.setCurrentMonth(DateTimeFormat.getFormat("yyyy-MM-dd HH:mm:ss.S").parse(expire.getValue()));
            picker.setValue(DateTimeFormat.getFormat("yyyy-MM-dd HH:mm:ss.S").parse(expire.getValue()));
        } catch (Exception ex) {
            // if not parseable, display current date
            picker.setCurrentMonth(new Date());
            picker.setValue(new Date());
        }

        TabMenu menu = new TabMenu();
        final TabItem tab = this;

        // by default false
        changeButton.setEnabled(false);
        changeButton.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent clickEvent) {
                expire.setValueAsString(DateTimeFormat.getFormat("yyyy-MM-dd HH:mm:ss.S").format(picker.getValue()));
                Map<String, Integer> ids = new HashMap<String, Integer>();
                ids.put("member", member.getId());
                SetAttributes request = new SetAttributes(JsonCallbackEvents.closeTabDisableButtonEvents(changeButton, tab));
                ArrayList<Attribute> list = new ArrayList<Attribute>();
                list.add(expire);
                request.setAttributes(ids, list);
            }
        });
        menu.addWidget(changeButton);

        menu.addWidget(TabMenu.getPredefinedButton(ButtonType.CANCEL, ButtonTranslation.INSTANCE.cancelButton(), new ClickHandler() {
            @Override
            public void onClick(ClickEvent clickEvent) {
                session.getTabManager().closeTab(tab, false);
            }
        }));

        vp.add(layout);
        vp.add(menu);
        vp.setCellHorizontalAlignment(menu, HasHorizontalAlignment.ALIGN_RIGHT);

        this.contentWidget.setWidget(vp);

        return getWidget();

    }

    public Widget getWidget() {
        return this.contentWidget;
    }

    public Widget getTitle() {
        return this.titleWidget;
    }

    public ImageResource getIcon() {
        return SmallIcons.INSTANCE.userGreenIcon();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + memberId;
        return result;
    }

    /**
     * @param obj
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        MembershipExpirationTabItem other = (MembershipExpirationTabItem) obj;
        if (memberId != other.memberId)
            return false;
        return true;
    }

    public boolean multipleInstancesEnabled() {
        return false;
    }

    public void open() {

    }

    public boolean isAuthorized() {

        if (session.isVoAdmin(member.getVoId())) {
            return true;
        } else {
            return false;
        }

    }

}