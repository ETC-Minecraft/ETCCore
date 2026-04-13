package dev.foliacmds.manager;

import dev.foliacmds.FoliaCustomCommands;

/**
 * @deprecated Moved to {@link dev.foliacmds.listener.CommandBlockListener}.
 *             This class is kept for binary compatibility only and will be removed in a future version.
 */
@Deprecated(forRemoval = true)
public class CommandBlockListener extends dev.foliacmds.listener.CommandBlockListener {
    public CommandBlockListener(FoliaCustomCommands plugin) {
        super(plugin);
    }
}
