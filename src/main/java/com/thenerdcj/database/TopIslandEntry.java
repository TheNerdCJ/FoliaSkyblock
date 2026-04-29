package com.thenerdcj.database;

import java.util.UUID;

public record TopIslandEntry(
        GridPosition gridPosition,
        UUID ownerUuid,
        String biomeName,
        double balance
) {}