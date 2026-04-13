package dev.foliacmds.manager;

// NOTE: This file is a deprecated wrapper. Real implementation is in dev.foliacmds.listener.ChatProtectionListener
import dev.foliacmds.FoliaCustomCommands;

/**
 * @deprecated Moved to {@link dev.foliacmds.listener.ChatProtectionListener}.
 *             This class is kept for binary compatibility only and will be removed in a future version.
 */
@Deprecated(forRemoval = true)
public class ChatProtectionListener extends dev.foliacmds.listener.ChatProtectionListener {
    public ChatProtectionListener(FoliaCustomCommands plugin) {
        super(plugin);
    }
}

