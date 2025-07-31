/*
 * This file is part of Player Follow. The plugin that allow players to follow each others.
 *
 * MIT License
 *
 * Copyright (c) 2024-2025 ZetaMap
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package fr.zetamap.playerfollow;

import arc.math.geom.Vec2;
import arc.struct.ObjectMap;
import arc.struct.Seq;

import mindustry.gen.Player;


public abstract class Follow {
  public final Seq<Player> followers = new Seq<>();
  public final Player followed;
  /** Aka {@link #followed} position, updated each times {@link #update(Vec2)} is called. */
  public final Vec2 leader = new Vec2();
  private boolean canRemove;
  private final Vec2 target = new Vec2();
  
  public Follow(Player target) {
    followed = target;
  }
  
  public boolean add(Player player) {
    if (contains(player)) return false;
    followers.add(player);
    add0(player);
    return canRemove = true;
  }
  
  /** Can be overridden to do things when adding a follower. */
  protected void add0(Player player) {}
  
  public boolean remove(Player player) {
    boolean removed = followers.remove(Players.equals(player));
    if (removed) {
      remove0(player);
      canRemove = removed;
    }
    return removed;
  }
  
  /** Can be overridden to do things when removing a follower. */
  protected void remove0(Player player) {}
  
  public boolean contains(Player player) {
    return followers.contains(Players.equals(player));
  }
  
  public void clear() {
    if (followers.isEmpty()) return;
    followers.clear();
    clear0();
  }
  
  /** Can be overridden to do things when removing all followers. */
  protected void clear0() {}
  
  /** @return {@code true} if there is no followers, excluding the case of an initial empty follow, else {@code false}. */
  public boolean shouldRemove() {
    return canRemove && followers.isEmpty();
  }

  /** Send a message to followers */
  public void message(String message, Object... args) {
    message(Players::warn, message, args);
  }
  
  /** Send a message to followers */
  public void message(arc.func.Cons3<Player, String, Object[]> sender, String message, Object... args) {
    followers.each(p -> sender.get(p, message, args));
  }
  
  // There is no client-side check for Vars.player.unit() == null when setting the position.
  // So try to anticipate a NullPointerException by hooking the client snapshot, and thus avoid sending 
  // the new position if the client says it is dying; and so doesn't have a unit.
  // This doesn't work in all cases, but better than crashing on every map change or dying player.
  private static final ObjectMap<Player, Boolean> sayingDead = new ObjectMap<>();
  static {
    mindustry.Vars.net.handleServer(mindustry.gen.ClientSnapshotCallPacket.class, (c, p) -> {
      if (c.player == null || c.kicked) return;
      p.handleServer(c);
      sayingDead.put(c.player, c.player.dead() || p.dead || p.unitID != c.player.unit().id);
    });
    arc.Events.on(mindustry.game.EventType.PlayerLeave.class, e -> sayingDead.remove(e.player));
  }
  private static boolean isDead(Player player) {
    Boolean dead;
    return player.dead() || (dead = sayingDead.get(player)) == null || dead;
  }
  
  /** Computes and updates followers positions */
  public void update(arc.func.Cons2<Player, Vec2> notifer) {
    leader.set(followed);
    if (followers.isEmpty() || isDead(followed)) return;
    preUpdate();
    
    for (int i=0; i<followers.size; i++) {
      Player follower = followers.get(i);
      if (isDead(follower)) continue;

      update(target.set(leader), i, follower);

      follower.unit().set(target);
      follower.set(target);
      follower.snapInterpolation();
      if (notifer != null) notifer.get(follower, target);
    }
  }
  
  /** Can be overridden to do things before updating followers positions */
  protected void preUpdate() {}

  /**
   * @param out the player's new position, starting from the {@link #leader} position.
   * @param index the {@link #followers} player index.
   * @param player the player to update.
   */
  protected abstract void update(Vec2 out, int index, Player player);
  
  /** Gets the size of the player */
  protected float hitSize(Player player) {
    return isDead(player) ? 1f : Math.max(1f, player.unit().hitSize / 2f);
  }
}
