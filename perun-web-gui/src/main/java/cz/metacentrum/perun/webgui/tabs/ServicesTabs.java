package cz.metacentrum.perun.webgui.tabs;

import cz.metacentrum.perun.webgui.client.PerunWebSession;
import cz.metacentrum.perun.webgui.tabs.servicestabs.*;

import java.util.Map;

/**
 * Services tabs
 * 
 * @author Vaclav Mach <374430@mail.muni.cz>
 * @version $Id$
 */
public class ServicesTabs {

	private PerunWebSession session = PerunWebSession.getInstance();
	
	static public final String URL = "srv";
	
	/**
	 * Creates a new instance of pages
     */
	public ServicesTabs(){}

	/**
	 * Loads the page
	 * 
	 * @return true on success / false otherwise
	 */
	public boolean loadTab(final String tab, final Map<String, String> parameters) {
	
		if(tab == null){
			return false;				
		}
		// if active
		boolean open = ("1".equals(parameters.get("active")));

		if (tab.equals(ServiceDestinationsTabItem.URL)) {
			session.getTabManager().addTab(ServiceDestinationsTabItem.load(parameters), open);
			return true;
		}
		if (tab.equals(ServiceDetailTabItem.URL)) {
			session.getTabManager().addTab(ServiceDetailTabItem.load(parameters), open);
			return true;
		}
		if (tab.equals(ServiceRequiredAttributesTabItem.URL)) {
			session.getTabManager().addTab(ServiceRequiredAttributesTabItem.load(parameters), open);
			return true;
		}
		if (tab.equals(ServicesTabItem.URL)) {
			session.getTabManager().addTab(ServicesTabItem.load(parameters), open);
			return true;
		}

        if (tab.equals(ServiceExecServicesTabItem.URL)) {
            session.getTabManager().addTab(ServiceExecServicesTabItem.load(parameters), open);
            return true;
        }
        if (tab.equals(ViewExecServiceTabItem.URL)) {
            session.getTabManager().addTab(ViewExecServiceTabItem.load(parameters), open);
            return true;
        }
		
		return false;
		
		
		
	}
	
}