package com.etcmc.etccore.manager;

import com.etcmc.etccore.ETCCore;

/**
 * @deprecated Moved to {@link com.etcmc.etccore.listener.BuildProtectionListener}.
 *             This class is kept for binary compatibility only and will be removed in a future version.
 */
@Deprecated(forRemoval = true)
public class BuildProtectionListener extends com.etcmc.etccore.listener.BuildProtectionListener {
    public BuildProtectionListener(ETCCore plugin) {
        super(plugin);
    }
}
