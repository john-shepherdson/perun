package cz.metacentrum.perun.webgui.tabs.groupstabs;

import com.google.gwt.cell.client.FieldUpdater;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.user.cellview.client.CellTable;
import com.google.gwt.user.client.ui.*;
import cz.metacentrum.perun.webgui.client.PerunWebSession;
import cz.metacentrum.perun.webgui.client.UiElements;
import cz.metacentrum.perun.webgui.client.localization.ButtonTranslation;
import cz.metacentrum.perun.webgui.client.mainmenu.MainMenu;
import cz.metacentrum.perun.webgui.client.resources.*;
import cz.metacentrum.perun.webgui.json.GetEntityById;
import cz.metacentrum.perun.webgui.json.JsonCallbackEvents;
import cz.metacentrum.perun.webgui.json.JsonUtils;
import cz.metacentrum.perun.webgui.json.authzResolver.AddAdmin;
import cz.metacentrum.perun.webgui.json.usersManager.FindCompleteRichUsers;
import cz.metacentrum.perun.webgui.json.usersManager.FindUsersByIdsNotInRpc;
import cz.metacentrum.perun.webgui.model.Group;
import cz.metacentrum.perun.webgui.model.User;
import cz.metacentrum.perun.webgui.tabs.*;
import cz.metacentrum.perun.webgui.tabs.userstabs.UserDetailTabItem;
import cz.metacentrum.perun.webgui.widgets.CustomButton;
import cz.metacentrum.perun.webgui.widgets.TabMenu;

import java.util.ArrayList;
import java.util.Map;

/**
 * Provides page with add admin to VO form
 * 
 * @author Pavel Zlamal <256627@mail.muni.cz>
 * @author Vaclav Mach <374430@mail.muni.cz>
 * @version $Id$
 */
public class AddGroupManagerTabItem implements TabItem, TabItemWithUrl{

	/**
	 * Perun web session
	 */
	private PerunWebSession session = PerunWebSession.getInstance();
	
	/**
	 * Content widget - should be simple panel
	 */
	private SimplePanel contentWidget = new SimplePanel();
	
	/**
	 * Title widget
	 */
	private Label titleWidget = new Label("Loading group");

	// widget
	final SimplePanel pageWidget = new SimplePanel();
	
	/**
	 * Entity ID to set
	 */
	private Group group;
	private int groupId;
	
	// CURRENT TAB STATE
	enum State {
		searching, listAll		
	}
	
	// default state is search
	State state = State.searching;
	
	// when searching
	private String searchString = "";
    private FindCompleteRichUsers users;
	
	/**
	 * Creates a tab instance
	 *
     * @param group group to add admin into
     */
	public AddGroupManagerTabItem(Group group){
		this.group = group;
		this.groupId = group.getId();
	}
	
	/**
	 * Creates a tab instance
	 *
     * @param groupId ID of group to add admin into
     */
	public AddGroupManagerTabItem(int groupId){
		this.groupId = groupId;
        JsonCallbackEvents events = new JsonCallbackEvents(){
            public void onFinished(JavaScriptObject jso) {
                group = jso.cast();
            }
        };
        new GetEntityById(PerunEntity.GROUP, groupId, events).retrieveData();
	}
	
	
	public boolean isPrepared() {
		return !(group == null);
	}
		
	public Widget draw() {
		
		titleWidget.setText(Utils.getStrippedStringWithEllipsis(group.getName())+ ": add manager");
		
		// MAIN PANEL
		VerticalPanel vp = new VerticalPanel();
		vp.setSize("100%", "100%");

		// if members or admins group, hide
		if(group.isCoreGroup()){
			
			vp.add(new HTML("<p>Group \""+group.getName()+"\" can't have managers managed from Group manager section. Please use VO manager section.</p>"));
			
			this.contentWidget.setWidget(vp);
			return getWidget();
		}

        this.users = new FindCompleteRichUsers("", null);

        // MAIN TAB PANEL
        VerticalPanel firstTabPanel = new VerticalPanel();
        firstTabPanel.setSize("100%", "100%");

        // HORIZONTAL MENU
        TabMenu tabMenu = new TabMenu();

        // get the table
        final CellTable<User> table;
        if (session.isPerunAdmin()) {
            table = users.getTable(new FieldUpdater<User, String>() {
                public void update(int i, User user, String s) {
                    session.getTabManager().addTab(new UserDetailTabItem(user));
                }
            });
        } else {
            table = users.getTable();
        }

        final TabItem tab = this;

        final CustomButton addButton = TabMenu.getPredefinedButton(ButtonType.ADD, ButtonTranslation.INSTANCE.addSelectedManagersToGroup());
        addButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                // TODO - SHOULD HAVE ONLY ONE CALLBACK TO CORE !!
                ArrayList<User> list = users.getTableSelectedList();
                if (UiElements.cantSaveEmptyListDialogBox(list)) {
                    for (int i = 0; i < list.size(); i++) {
                        if (i == list.size()-1) {
                            AddAdmin request = new AddAdmin(PerunEntity.GROUP, JsonCallbackEvents.closeTabDisableButtonEvents(addButton, tab));
                            request.addAdmin(groupId, list.get(i).getId());
                        } else {
                            AddAdmin request = new AddAdmin(PerunEntity.GROUP, JsonCallbackEvents.disableButtonEvents(addButton));
                            request.addAdmin(groupId, list.get(i).getId());
                        }
                    }
                }
            }
        });
        tabMenu.addWidget(addButton);

        tabMenu.addWidget(TabMenu.getPredefinedButton(ButtonType.CANCEL, "", new ClickHandler() {
            @Override
            public void onClick(ClickEvent clickEvent) {
                session.getTabManager().closeTab(tab, false);
            }
        }));

        // search textbox
        TextBox searchBox = tabMenu.addSearchWidget(new PerunSearchEvent() {
            @Override
            public void searchFor(String text) {
                startSearching(text);
                searchString = text;
            }
        }, ButtonTranslation.INSTANCE.searchUsers());

        // if some text has been searched before
        if(!searchString.equals(""))
        {
            searchBox.setText(searchString);
            startSearching(searchString);
        }

        /*
        Button b1 = tabMenu.addButton("List all", SmallIcons.INSTANCE.userGrayIcon(), new ClickHandler(){
            public void onClick(ClickEvent event) {
                GetCompleteRichUsers callback = new GetCompleteRichUsers(new JsonCallbackEvents(){
                    public void onLoadingStart() {
                        table.setEmptyTableWidget(new AjaxLoaderImage().loadingStart());
                    }
                    public void onFinished(JavaScriptObject jso) {
                        users.clearTable();
                        JsArray<User> usrs = JsonUtils.<User>jsoAsArray(jso);
                        for (int i=0; i<usrs.length(); i++){
                            users.addToTable(usrs.get(i));
                        }
                        users.sortTable();
                    }
                });
                callback.retrieveData();
            }
        });
        b1.setTitle("List of all users in Perun");
        */

        addButton.setEnabled(false);
        JsonUtils.addTableManagedButton(users, table, addButton);

        // add a class to the table and wrap it into scroll panel
        table.addStyleName("perun-table");
        ScrollPanel sp = new ScrollPanel(table);
        sp.addStyleName("perun-tableScrollPanel");

        // add menu and the table to the main panel
        firstTabPanel.add(tabMenu);
        firstTabPanel.setCellHeight(tabMenu, "30px");
        firstTabPanel.add(sp);

        session.getUiElements().resizePerunTable(sp, 350, this);

        this.contentWidget.setWidget(firstTabPanel);
		return getWidget();
	
	}

    /**
     * Starts the search for users
     */
    protected void startSearching(String text){


        users.clearTable();

        // IS searched string IDs?
        if (JsonUtils.isStringWithIds(text)) {

            FindUsersByIdsNotInRpc req = new FindUsersByIdsNotInRpc(new JsonCallbackEvents(){

                public void onFinished(JavaScriptObject jso){

                    ArrayList<User> usersList = JsonUtils.jsoAsList(jso);
                    for(User u : usersList)
                    {
                        users.addToTable(u);
                    }
                }

            }, text);

            req.retrieveData();
            return;
        }


        users.searchFor(text);
    }
	
	private void setPageWidget(Widget w)
	{
		this.pageWidget.setWidget(w);

	}

	public Widget getWidget() {
		return this.contentWidget;
	}

	public Widget getTitle() {
		return this.titleWidget;
	}

	public ImageResource getIcon() {
		return SmallIcons.INSTANCE.administratorIcon(); 
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + 6786786;
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

        AddGroupManagerTabItem create = (AddGroupManagerTabItem) obj;
		if (groupId != create.groupId){
			return false;
		}
		
		return true;
	}

	public boolean multipleInstancesEnabled() {
		return false;
	}
	
	public void open()
	{
		session.getUiElements().getMenu().openMenu(MainMenu.GROUP_ADMIN);
		if(group != null){
			session.setActiveGroup(group);
			return;
		}
		session.setActiveGroupId(groupId);
		
	}
	
	public boolean isAuthorized() {

		if (session.isVoAdmin(group.getVoId()) || session.isGroupAdmin(groupId)) {
			return true; 
		} else {
			return false;
		}

	}
	
	public final static String URL = "add-manager";
	
	public String getUrl()
	{
		return URL;
	}
	
	public String getUrlWithParameters()
	{
		return GroupsTabs.URL + UrlMapper.TAB_NAME_SEPARATOR + getUrl() + "?id=" + groupId;
	}
	
	static public AddGroupManagerTabItem load(Map<String, String> parameters)
	{
		int id = Integer.parseInt(parameters.get("id"));
		return new AddGroupManagerTabItem(id);
	}

}