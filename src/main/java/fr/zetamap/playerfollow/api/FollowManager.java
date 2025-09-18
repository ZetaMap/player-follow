/*
 * This file is part of Player Follow. The plugin that allow players to follow each others.
 *
 * MIT License
 *
 * Copyright (c) 2025 ZetaMap
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
import arc.func.Func;
import arc.math.geom.Position;
import arc.math.geom.Vec2;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Interval;
import arc.util.Log;

import mindustry.Vars;
import mindustry.async.AsyncProcess;


public class FollowManager<T extends Position> {
  public final ObjectMap<T, Follow<T>> all = new ObjectMap<>();
  protected Interval timer = new Interval();
  /** Used to delay add and remove operations while updating. */
  protected boolean updating;
  /** {@code null} value means removal. */
  protected final ObjectMap<T, Follow<T>> pendingChanges = new ObjectMap<>();
  /** ticks between follow updates. */
  protected final float updateInterval;
  protected final Cons2<T, Vec2> notifier;
  protected final Func<T, String> followerToString;

  public FollowManager(Cons2<T, Vec2> notifier, Func<T, String> followerToString, float updateIntervalTicks) {
    this.notifier = notifier;
    this.followerToString = followerToString;
    this.updateInterval = updateIntervalTicks;
    
    Updater.init();
    Updater.add(this);
  }

  /** Adds a follow to the list. */
  public <F extends Follow<T>> F add(F follow) {
    (updating ? pendingChanges : all).put(follow.followed(), follow);
    return follow;
  }
  
  /** Creates a follow from the specified {@code mode} for the specified {@code target}, and adds it to the list. */
  public <F extends Follow<T>> F add(FollowMode<T> mode, T target) {
    return add(mode.create(target));
  }

  /** Removes a follow from the list. */
  public <F extends Follow<T>> boolean remove(F follow) {
    follow.clear();
    if (!updating) return all.remove(follow.followed()) != null;
    boolean found = get(follow.followed()) != null;
    if (found) pendingChanges.put(follow.followed(), null);
    return found;
  }
  
  /** Removes a follow of the specified {@code target} from the list. */
  public boolean remove(T target) {
    Follow<T> last = updating ? get(target) : all.remove(target);
    if (last == null) return false;
    if (updating) pendingChanges.put(target, null);
    last.clear();
    return true;
  }
  
  /** Remove a {@code follower} from all follows. */
  public void removeFollower(T follower) {
    all.each((p, f) -> f.remove(follower));
  }
  
  /** Find a follow by the followed player. */
  @SuppressWarnings("unchecked")
  public <F extends Follow<T>> F get(T target) {
    return (F)all.get(target);
  }
  
  /** Find a follow by one of his followers. */
  @SuppressWarnings("unchecked")
  public <F extends Follow<T>> F find(T follower) {
    for (Follow<T> f : all.values()) {
      if (f.contains(follower)) return (F)f;
    }
    return null;
  }
  
  /** 
   * Change the mode of a follow by instantiating a new one and copying the followers. <br>
   * If the follow is not already added, it will be after copying.
   */
  public <F extends Follow<T>> F changeMode(F follow, FollowMode<T> mode) {
    F f = add(mode, follow.followed());
    f.followers().set(follow.followers());
    follow.followers().clear();
    return f;
  }
  
  /** 
   * Change the mode of a follow by instantiating a new one and copying the followers. <br>
   * If the follow is not in the list, it will be added. 
   */
  public <F extends Follow<T>> F changeMode(T target, FollowMode<T> mode) {
    F f = get(target), newFollow = add(mode, target);
    if (f == null) return newFollow;
    newFollow.followers().set(f.followers());
    f.followers().clear();
    return newFollow;
  }
  
  /** Change the mode of all follows. */
  public void changeMode(FollowMode<T> mode) {
    all.each((p, f) -> {
      Follow<T> newFollow = add(mode, p);
      newFollow.followers().set(f.followers());
      f.followers().clear();
    });
  }
  
  
  /** Global {@link FollowManager} updater. */
  public static class Updater {
    public static final Seq<FollowManager<Position>> managers = new Seq<>();
    private static boolean initialized, updating;
    
    public static void init() {
      if (initialized) return;
      
      Vars.asyncCore.processes.add(new AsyncProcess() {      
        public void process() {
          int wwidth = Vars.world.unitWidth(), wheight = Vars.world.unitHeight();
          managers.each(m -> m.updating, m -> {
            m.all.each((p, f) -> {
              if (f.shouldRemove()) {
                m.pendingChanges.put(p, null);
                return;
              }
              
              try { 
                f.update((fp, out) -> {
                  out.clamp(0, 0, wwidth, wheight);
                  m.notifier.get(p, out);
                }); 
              } catch (Exception t) {
                Log.err("Failed to update follow of target '"+m.followerToString.get(f.followed())+"'", t);
                Log.warn("Follow removed to avoid future errors.");
                m.pendingChanges.put(p, null);
              }
            });  
          });
        }
        
        public boolean shouldProcess() {
          managers.each(m -> {
            if (m.updating = !m.all.isEmpty() && m.timer.get(m.updateInterval))
              updating = true;
          });
          return updating;
        }
        
        public void end() {
          if (!updating) return;
          managers.each(m -> m.updating = !m.pendingChanges.isEmpty(), m -> {
            m.pendingChanges.each((p, f) -> {
              if (f == null) m.all.remove(p);
              else m.all.put(p, f);
            });
            m.updating = false;
            m.pendingChanges.clear();
          });
          updating = false;
        }
      });
      
      initialized = true;
    }
    
    @SuppressWarnings("unchecked")
    public static void add(FollowManager<?> manager) {
      managers.add((FollowManager<Position>)manager);
    }
  }
}
