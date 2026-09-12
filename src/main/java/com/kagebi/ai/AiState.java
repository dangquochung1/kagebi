package com.kagebi.ai;

/**
 * The one state machine every enemy in the game runs.
 *
 * <p>One machine rather than a behaviour tree or a planner, because the whole
 * problem is seven states wide and the cost of a general solution here is that
 * nobody can answer "why is this slime standing still" by reading a switch.
 *
 * <p>The legal moves are deliberately few:
 *
 * <pre>
 *   IDLE  -> CHASE                     player came within aggro range
 *   CHASE -> IDLE | WINDUP             lost the player | came within attack range
 *   WINDUP-> ATTACK                    telegraph finished
 *   ATTACK-> RECOVER                   active window finished
 *   RECOVER-> IDLE | CHASE             cooldown finished
 *   any   -> HURT                      took a hit
 *   HURT  -> CHASE                     stagger finished
 *   any   -> DEAD                      hp reached zero
 *   DEAD  -> nothing                   absorbing, by design
 * </pre>
 *
 * <p>Every state except IDLE and DEAD has a bounded dwell time, which is what
 * makes "the brain cannot wedge" a property a test can assert rather than a
 * hope. A wedged brain is close to invisible in play - the enemy simply stands
 * there, and the room stays uncleared - so it is worth pinning down.
 */
public enum AiState {

    IDLE,
    CHASE,
    /** Telegraph. No hitbox exists yet; this is the beat the player reads. */
    WINDUP,
    ATTACK,
    RECOVER,
    /** Stagger after taking a hit. */
    HURT,
    DEAD
}
