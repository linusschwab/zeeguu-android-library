package ch.unibe.zeeguulibrary.Dialogs;

import ch.unibe.zeeguulibrary.Core.ZeeguuConnectionManager;

/**
 * Callback interface that must be implemented by the container activity
 */
public interface ZeeguuDialogCallbacks {
    ZeeguuConnectionManager getZeeguuConnectionManager();
    void showZeeguuLoginDialog(String message, String email);
    void showZeeguuCreateAccountDialog(String message, String username, String email);
    void displayMessage(String message);
}
