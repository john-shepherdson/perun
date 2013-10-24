package cz.metacentrum.perun.core.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cz.metacentrum.perun.core.api.PerunBean;
import cz.metacentrum.perun.core.api.Role;

public class AuthzRoles extends HashMap<Role, Map<String, List<Integer>>> {
  private static final long serialVersionUID = 1L;
  
  public AuthzRoles() {
    super();
  }
  
	public AuthzRoles(Role role) {
		super();
	  this.put(role, null);
	}
	
	public AuthzRoles(Role role, PerunBean perunBean) {
    super();
    Map<String, List<Integer>> perunBeans = new HashMap<String, List<Integer>>();
    perunBeans.put(perunBean.getBeanName(), new ArrayList<Integer>(perunBean.getId()));
    this.put(role, perunBeans);
  }
	
	public AuthzRoles(Role role, String beanName, int id) {
    super();
	  Map<String, List<Integer>> perunBeans = new HashMap<String, List<Integer>>();
    perunBeans.put(beanName, new ArrayList<Integer>(id));
    this.put(role, perunBeans);
  }
	
	public AuthzRoles(Role role, HashMap<String, List<Integer>> perunBeans) {
    super();
	  this.put(role, perunBeans);
  }
	
	public AuthzRoles(Role role, List<PerunBean> perunBeans) {
    super();
    Map<String, List<Integer>> complementaryObjects = new HashMap<String, List<Integer>>();
    if (perunBeans != null) {
      for (PerunBean perunBean: perunBeans) {
        if (complementaryObjects.get(perunBean.getBeanName()) == null) {
          complementaryObjects.put(perunBean.getBeanName(), new ArrayList<Integer>());
        }
        complementaryObjects.get(perunBean.getBeanName()).add(perunBean.getId());
      }
    }
    this.put(role, complementaryObjects);
  }
	
	public void putAuthzRoles(Role role, Map<String, List<Integer>> perunBeans) {
    this.putComplementaryObjects(role, perunBeans);
  }
	
	public void putAuthzRole(Role role) {
    this.putComplementaryObject(role, null);
  }
	
	public void putAuthzRole(Role role, PerunBean perunBean) {
	  Map<String, List<Integer>> complementaryObjects = new HashMap<String, List<Integer>>();
	  complementaryObjects.put(perunBean.getBeanName(), new ArrayList<Integer>());
    complementaryObjects.get(perunBean.getBeanName()).add(perunBean.getId());
	  
    this.putComplementaryObjects(role, complementaryObjects);
  }
	
	public void putAuthzRole(Role role, Class perunBeanClass, int perunBeanId) {
	  Map<String, List<Integer>> complementaryObjects = new HashMap<String, List<Integer>>();
    complementaryObjects.put(perunBeanClass.getSimpleName(), new ArrayList<Integer>());
    complementaryObjects.get(perunBeanClass.getSimpleName()).add(perunBeanId);
    
    this.putComplementaryObjects(role, complementaryObjects);
	}
	
	public boolean hasRole(Role role, PerunBean perunBean) {
	  return this.get(role).containsKey(perunBean.getBeanName()) 
	      && this.get(role).get(perunBean.getBeanName()).contains(perunBean.getId());
	}
	
	public boolean hasRole(Role role, String perunBeanName, int id) {
    return this.get(role).containsKey(perunBeanName) 
        && this.get(role).get(perunBeanName).contains(id);
  }
	
	public boolean hasRole(Role role) {
    return this.containsKey(role);
  }
	
	public List<String> getRolesNames() {
	  List<String> roles = new ArrayList<String>();

	  for (Role role: this.keySet()) {
      roles.add(role.getRoleName());
    }
	  
	  return roles;
	}
	
	protected void putComplementaryObject(Role role, PerunBean perunBean) {
	  if (this.containsKey(role)) {
      if (perunBean != null) {
        if (this.get(role).get(perunBean.getBeanName()) == null) {
          this.get(role).put(perunBean.getBeanName(), new ArrayList<Integer>());
        }
        this.get(role).get(perunBean.getBeanName()).add(perunBean.getId());
	    }
	  } else  {
	      this.put(role, new HashMap<String, List<Integer>>());
	      if (perunBean != null) {
	        if (!this.containsKey(perunBean.getBeanName()) || this.get(perunBean.getBeanName()) == null) {
	          this.get(role).put(perunBean.getBeanName(), new ArrayList<Integer>());
	        }
	        if (perunBean != null) {
	          this.get(role).get(perunBean.getBeanName()).add(perunBean.getId());
	        }
	      }
	  }
	}
	
	protected void putComplementaryObjects(Role role, Map<String, List<Integer>> perunBeans) {
    if (this.containsKey(role) && (this.get(role) != null)) {
      if (perunBeans != null) {
        for (String perunBeanName: perunBeans.keySet()) {
          if (this.get(role).get(perunBeanName) != null) {
            this.get(role).get(perunBeanName).addAll(perunBeans.get(perunBeanName));
          } else {
            this.get(role).putAll(perunBeans);
          }
        }
      }
    } else {
      this.put(role, new HashMap<String, List<Integer>>());
      if (perunBeans != null) {
        this.get(role).putAll(perunBeans);
      }
    }
  }

  public String toString() {
	  String roles = "";
	  if (!this.isEmpty()) {
	    for (Role role: this.keySet()) {
	      roles += role.getRoleName() + this.get(role) + ",";
	    }
	  }
    return getClass().getSimpleName() + "[" + roles + "]";
	}
}
