package cz.metacentrum.perun.ldapc.processor.impl;

import cz.metacentrum.perun.ldapc.processor.EventDispatcher.MessageBeans;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Required;

public class RegexpDispatchEventCondition extends SimpleDispatchEventCondition {

  private Pattern pattern;

  @Override
  public boolean isApplicable(MessageBeans beans, String msg) {
    Matcher matcher = pattern.matcher(msg);
    return super.isApplicable(beans, msg) && matcher.find();
  }

  @Required
  public void setPattern(String regexp) {
    this.pattern = Pattern.compile(regexp, Pattern.DOTALL);
  }
}
