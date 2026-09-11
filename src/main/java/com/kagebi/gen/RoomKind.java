package com.kagebi.gen;

/** What a room is for. Drives the minimap icon, the music sting and the loot. */
public enum RoomKind {

    /** Where the player arrives. Always empty of enemies. */
    START,
    NORMAL,
    /** One chest, no enemies. */
    TREASURE,
    /** Costs a key to enter; better chest. */
    LOCKED,
    SHOP,
    /** Reachable only by finding it; never required to reach the boss. */
    SECRET,
    BOSS,
    /** The stairs down, unlocked once the boss is dead. */
    EXIT
}
