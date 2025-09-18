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

package fr.zetamap.playerfollow.api;

import arc.func.Cons2;
import arc.math.geom.Position;
import arc.math.geom.Vec2;
import arc.struct.DelayedRemovalSeq;
import arc.struct.Seq;


public abstract class AbstractFollow<T extends Position> implements Follow<T> {
  /** Because {@link #update(Cons2)} can be called in another thread, {@link #remove(T)} will be delayed. */
  public final DelayedRemovalSeq<T> followers = new DelayedRemovalSeq<>();
  /** The followed target */
  public final T followed;
  /** Aka {@link #followed} position, updated each times {@link #update()} is called. */
  public final Vec2 leader = new Vec2();
  /** This is needed to avoid removing the follow while creating one and adding a follower. */
  protected boolean canRemove;
  protected final Vec2 target = new Vec2();
  
  public AbstractFollow(T target) {
    followed = target;
  }
  
  @Override
  public T followed() {
    return followed;
  }

  @Override
  public Seq<T> followers() {
    return followers;
  }
  
  @Override
  public boolean add(T follower) {
    if (!followers.addUnique(follower)) return false;
    addImpl(follower);
    return canRemove = true;
  }
  
  @Override
  public boolean remove(T follower) {
    if (!followers.remove(follower)) return false;
    removeImpl(follower);
    return canRemove = true;
  }
  
  @Override
  public boolean contains(T follower) {
    return followers.contains(follower);
  }
  
  @Override
  public void clear() {
    if (followers.isEmpty()) return;
    followers.clear();
    clearImpl();
  }
  
  /** @return {@code true} if there is no followers, excluding the case of an initial empty follow, else {@code false}. */
  @Override
  public boolean shouldRemove() {
    return canRemove && followers.isEmpty();
  }

  /** Computes and updates {@link #followers}'s position. */
  @Override
  public void update(Cons2<T, Vec2> notifer) {
    leader.set(followed);
    if (cannotUpdate(followed)) return;
    followers.begin();
    try {
      if (followers.isEmpty()) return;
      preUpdate();
      
      for (int i=0; i<followers.size; i++) {
        T follower = followers.get(i);
        if (cannotUpdate(follower)) continue;
  
        update(target.set(leader), i, follower);
        setPosition(follower, target);
        if (notifer != null) notifer.get(follower, target);
      }  
    } finally {
      followers.end();
    }
  }
  
  // Can be overridden to do things when adding, removing or clearing followers.
  protected void addImpl(T follower) {}
  protected void removeImpl(T follower) {}
  protected void clearImpl() {}
  
  /** Can be overridden to do things before updating followers positions. */
  protected void preUpdate() {}

  /**
   * @param out the player's new position, starting from the {@link #leader} position.
   * @param index the {@link #followers} index.
   * @param follower the follower to update.
   */
  protected abstract void update(Vec2 out, int index, T follower);
  
  /** @return whether the {@code follower} or the {@link #followed} cannot be updated. */
  protected abstract boolean cannotUpdate(T follower);
  
  /** Set the position of the {@code follower}. */
  protected abstract void setPosition(T follower, Vec2 target);
}
