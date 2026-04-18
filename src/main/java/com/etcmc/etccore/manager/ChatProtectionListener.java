package com.etcmc.etccore.manager;

// NOTE: This file is a deprecated wrapper. Real implementation is in com.etcmc.etccore.listener.ChatProtectionListener
import com.etcmc.etccore.ETCCore;

/**
 * @deprecated Moved to {@link com.etcmc.etccore.listener.ChatProtectionListener}.
 *             This class is kept for binary compatibility only and will be removed in a future version.
 */
@Deprecated(forRemoval = true)
public class ChatProtectionListener extends com.etcmc.etccore.listener.ChatProtectionListener {
    public ChatProtectionListener(ETCCore plugin) {
        super(plugin);
    }
}

