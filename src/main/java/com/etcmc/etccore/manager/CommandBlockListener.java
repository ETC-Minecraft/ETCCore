package com.etcmc.etccore.manager;

import com.etcmc.etccore.ETCCore;

/**
 * @deprecated Moved to {@link com.etcmc.etccore.listener.CommandBlockListener}.
 *             This class is kept for binary compatibility only and will be removed in a future version.
 */
@Deprecated(forRemoval = true)
public class CommandBlockListener extends com.etcmc.etccore.listener.CommandBlockListener {
    public CommandBlockListener(ETCCore plugin) {
        super(plugin);
    }
}
