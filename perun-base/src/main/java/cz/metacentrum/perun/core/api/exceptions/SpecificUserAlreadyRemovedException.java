package cz.metacentrum.perun.core.api.exceptions;

/**
 * This exception is thrown when trying to delete a specific user that has already been deleted
 *
 * @author Michal Stava
 */
public class SpecificUserAlreadyRemovedException extends PerunException {
  static final long serialVersionUID = 0;

  /**
   * Simple constructor with a message
   *
   * @param message message with details about the cause
   */
  public SpecificUserAlreadyRemovedException(String message) {
    super(message);
  }

  /**
   * Constructor with a message and Throwable object
   *
   * @param message message with details about the cause
   * @param cause   Throwable that caused throwing of this exception
   */
  public SpecificUserAlreadyRemovedException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Constructor with a Throwable object
   *
   * @param cause Throwable that caused throwing of this exception
   */
  public SpecificUserAlreadyRemovedException(Throwable cause) {
    super(cause);
  }

}
