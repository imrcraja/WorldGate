@Override
public void onInitializeClient() {

    WorldGateMod.LOGGER.info(
            "WorldGate client initialized."
    );

    registerVersionKeybinds();

    EXECUTOR.submit(() -> {

        boolean connected =
                SESSION.connect();

        if (connected) {

            WorldGateMod.LOGGER.info(
                    "WorldGate Firebase session ready (uid={})",
                    SESSION.uid()
            );

        } else {

            WorldGateMod.LOGGER.error(
                    "WorldGate could not sign in to Firebase -- "
                            + "check internet/API key."
            );
        }
    });
}

private static void registerVersionKeybinds() {

    try {

        Class<?> keybindClass =
                Class.forName(
                        "com.rcraja.worldgate.client.WorldGateKeybinds"
                );

        keybindClass
                .getMethod("register")
                .invoke(null);

        WorldGateMod.LOGGER.info(
                "WorldGate keybinds registered."
        );

    } catch (ClassNotFoundException ignored) {

        /*
         * A version module without the optional
         * keybind implementation simply continues.
         */

    } catch (Exception e) {

        WorldGateMod.LOGGER.error(
                "WorldGate keybind registration failed.",
                e
        );
    }
}
