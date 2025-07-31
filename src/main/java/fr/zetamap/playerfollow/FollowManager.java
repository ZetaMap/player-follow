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

package fr.zetamap.playerfollow;

import arc.math.geom.Vec2;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Interval;
import arc.util.Log;
import arc.util.pooling.Pool;
import arc.util.pooling.Pools;

import mindustry.Vars;
import mindustry.gen.Player;


public class FollowManager {
  public static final Seq<Follow> all = new Seq<>(false);
  /** ticks between follow updates. */
  private static final float updateInterval = 60f / 30f;//30 fps

  /** Must be called once */
  public static void init() {
    // computes synchronously and sends the new positions asynchronously, to avoid blocking the server
    Vars.asyncCore.processes.add(new mindustry.async.AsyncProcess() {      
      ObjectMap<Player, Vec2> pos = new ObjectMap<>();
      Pool<Vec2> pool = Pools.get(Vec2.class, Vec2::new);
      Interval timer = new Interval();
      boolean process;

      public void begin() {
        process = Vars.state.isPlaying() && timer.get(updateInterval) && !all.isEmpty();
        if (!process) return;
        
        java.util.Iterator<Follow> elements = all.iterator();
        while (elements.hasNext()) {
          Follow follow = elements.next();
          
          if (follow.shouldRemove()) {
            elements.remove();
            continue;
          }
          
          try { follow.update((p, out) -> pos.get(p, pool::obtain).set(out)); } 
          catch (Exception t) {
            Log.err("Failed to update follow of target '"+follow.followed.uuid()+"'", t);
            Log.warn("Follow removed from the list to avoid future errors.");
            elements.remove();
          }
        }
        
        // Remove positions outside the map
        ObjectMap.Entries<Player, Vec2> entries = pos.entries();
        int wwidth = Vars.world.unitWidth(), wheight = Vars.world.unitHeight();
        while (entries.hasNext()) {
          Vec2 tmp = entries.next().value;
          if (tmp.x < 0 || tmp.y < 0 || tmp.x > wwidth || tmp.y > wheight) {
            entries.remove();
            pool.free(tmp);
          }
        }
      }
      
      public void process() {
        pos.each((p, out) -> {
          mindustry.gen.Call.setPosition(p.con, out.x, out.y);
          out.set(-1, -1); // invalidate the position
        });
      }
      
      public boolean shouldProcess() {
        return process;
      }
    });
  }

  /** Adds a follow to the list. */
  public static Follow add(Follow follow) {
    all.add(follow);
    return follow;
  }
  
  /** Creates a follow from the specified {@code mode} for the specified {@code target}, and adds it to the list. */
  public static Follow add(FollowMode mode, Player target) {
    return add(mode.create(target));
  }

  /** Removes a follow from the list. */
  public static boolean remove(Follow follow) {
    follow.clear();
    return all.remove(follow);
  }
  
  /** Removes a follow of the specified {@code target} from the list. */
  public static boolean remove(Player target) {
    int index = all.indexOf(f -> Players.equals(f.followed, target));
    if (index == -1) return false;
    
    all.get(index).clear();
    all.remove(index);
    return true;
  }
  
  /** Remove a {@code follower} from all follows. */
  public static boolean removeFollower(Player follower) {
    boolean[] result = {false};
    all.each(f -> { if (f.remove(follower)) result[0] = true; });
    return result[0];
  }
  
  /** Find a follow by the followed player. */
  public static Follow get(Player target) {
    return all.find(f -> Players.equals(f.followed, target));
  }
  
  /** Find a follow by one of his followers. */
  public static Follow find(Player follower) {
    return all.find(f -> f.contains(follower));
  }
  
  /** Change the mode of a follow by instantiating a new one and copying the followers. */
  public static Follow changeMode(Follow follow, FollowMode mode) {
    int index = all.indexOf(follow);
    if (index == -1 || follow == null) return null;
    
    Follow f = mode.create(follow.followed);
    follow.followers.each(p -> f.add(p));
    follow.clear();
    all.set(index, f);
    return f;
  }
  
  /** Change the mode of a follow by instantiating a new one and copying the followers. */
  public static Follow changeMode(Player target, FollowMode mode) {
    int index = all.indexOf(f -> Players.equals(f.followed, target));
    if (index == -1) return null;
    
    Follow follow = all.get(index), f = mode.create(target);
    follow.followers.each(p -> f.add(p));
    follow.clear();
    all.set(index, f);
    return f;
  }
  
  /** Change the mode of all follows. */
  public static void changeMode(FollowMode mode) {
    for (int i=0; i<all.size; i++) {
      Follow follow = all.get(i), f = mode.create(follow.followed);
      follow.followers.each(p -> f.add(p));
      follow.clear();
      all.set(i, f);
    }
  }
}
