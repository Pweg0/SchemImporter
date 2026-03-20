package com.schematicimporter.paste;

import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

/**
 * Narrow interface for sending paste completion feedback.
 *
 * <p>Allows {@link PasteExecutor} to be tested without a real
 * {@link net.minecraft.commands.CommandSourceStack}.</p>
 */
public interface PasteFeedbackSink {

    /**
     * Send a success message.
     *
     * @param message             supplier for the message component
     * @param broadcastToOps      whether to broadcast to ops (passed through to real stack)
     */
    void sendSuccess(Supplier<Component> message, boolean broadcastToOps);

    /**
     * Send a failure message (warnings, unknown block counts, etc.).
     *
     * @param message the failure component
     */
    void sendFailure(Component message);

    /**
     * Wrap a real {@link net.minecraft.commands.CommandSourceStack} as a {@link PasteFeedbackSink}.
     */
    static PasteFeedbackSink of(net.minecraft.commands.CommandSourceStack source) {
        return new PasteFeedbackSink() {
            @Override
            public void sendSuccess(Supplier<Component> message, boolean broadcastToOps) {
                source.sendSuccess(message, broadcastToOps);
            }

            @Override
            public void sendFailure(Component message) {
                source.sendFailure(message);
            }
        };
    }

    /** A no-op sink that discards all messages. Useful for testing. */
    static PasteFeedbackSink noop() {
        return new PasteFeedbackSink() {
            @Override
            public void sendSuccess(Supplier<Component> message, boolean broadcastToOps) {}

            @Override
            public void sendFailure(Component message) {}
        };
    }
}
