package cz.metacentrum.perun.webgui.client.mainmenu;

import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.*;
import cz.metacentrum.perun.webgui.client.PerunWebSession;
import cz.metacentrum.perun.webgui.tabs.GroupsTabs;
import cz.metacentrum.perun.webgui.tabs.TabItemWithUrl;
import cz.metacentrum.perun.webgui.tabs.UrlMapper;
import cz.metacentrum.perun.webgui.tabs.groupstabs.GroupsTabItem;

/**
 * Main menu item
 * 
 * @author Vaclav Mach <374430@mail.muni.cz>
 * @author Pavel Zlamal <256627@mail.muni.cz>
 * @version $Id$
 */
public class MainMenuItem {

	/**
	 * Current PerunWebSession
	 */
	private PerunWebSession session = PerunWebSession.getInstance();
	
	/**
	 * Widget containing the menu item
	 */
	private Widget widget = null;
	
	/**
	 * TabItem for link
	 */
	private TabItemWithUrl tabItem;
	
	/**
	 * Image next to the link
	 */
	private Image image;
	
	/**
	 * Link text
	 */
	private String title = "";
	
	/**
	 * Whether is the link active - the user is on the page
	 */
	private boolean active = false;
	
	/**
	 * Creates new menu item
	 *
     * @param title Text shown
     * @param tabItem
     */
	public MainMenuItem(String title, TabItemWithUrl tabItem){
		this.title = title;
		this.tabItem = tabItem;
		this.session = session;
	}
	
	/**
	 * Creates new menu item with custom image
	 *
     * @param title
     * @param tabItem
     * @param image
     */
	public MainMenuItem(String title, TabItemWithUrl tabItem, Image image){
		this(title, tabItem);
		this.image = image;
		build();
	}
	
	/**
	 * Creates new menu item with custom image
	 *
     * @param title
     * @param tabItem
     * @param imageResource
     */
	public MainMenuItem(String title, TabItemWithUrl tabItem, ImageResource imageResource){
		this(title, tabItem, new Image(imageResource));
	}

	/**
	 * Returns the link widget
	 * 
	 * @return
	 */
	public Widget getWidget(){
		return this.widget;
	}

    /**
     * Returns the image of link widget
     *
     * @return
     */
    public Widget getIcon(){
        return this.image;
    }
	
	/**
	 * Re-build main menu item
     * (check if item is selected)
	 */
	public void build()
	{

        Widget widget = new Widget();

        boolean enabled = (tabItem != null);

		// if menu item enabled - show hyperlink
		if(enabled){
            widget = new Hyperlink(title, session.getTabManager().getLinkForTab(tabItem));
            widget.removeStyleName("mainMenuNotActive");
		// else show plain text
		}else {
            widget  = new HTML(title);
            widget.addStyleName("mainMenuNotActive");
		}

        if (tabItem != null && session.getTabManager().getActiveTab() != null) {
           this.active = (tabItem.getUrlWithParameters().equals(((TabItemWithUrl)session.getTabManager().getActiveTab()).getUrlWithParameters()));
            // FIXME - hack for group admin - groups when changing active VO
            // IF ACTIVE TAB IS GROUP ADMIN - GROUPS
            if (((TabItemWithUrl)session.getTabManager().getActiveTab()).getUrlWithParameters().startsWith(GroupsTabs.URL + UrlMapper.TAB_NAME_SEPARATOR + tabItem.getUrl() + "?vo=")) {
                this.active = ((TabItemWithUrl)tabItem).getUrlWithParameters().startsWith(GroupsTabs.URL + UrlMapper.TAB_NAME_SEPARATOR + tabItem.getUrl() + "?vo=");
            }
        } else {
            this.active = false;
        }

		if(active){
            widget.addStyleName("mainMenuActive");
		} else {
            widget.removeStyleName("mainMenuActive");
        }

        this.widget = widget;

    }

	public TabItemWithUrl getTabItem() {
		return tabItem;
	}

	public void setTabItem(TabItemWithUrl tabItem) {
		this.tabItem = tabItem;
	}

}
