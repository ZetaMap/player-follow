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

package fr.zetamap.playerfollow.modes;

import arc.math.geom.Vec2;
import arc.struct.Seq;
import arc.util.pooling.Pool;
import arc.util.pooling.Pools;
import mindustry.gen.Player;


public class SnakeFollow extends fr.zetamap.playerfollow.Follow {
  /** Distance between each players */
  public static float playerDistance = 2f * mindustry.Vars.tilesize;
  
  public Seq<Vec2> trail = new Seq<>();
  
  private int leaderI = 0;
  private float distance = playerDistance, totalDistance = 0, totalHitSize = 0;
  private final Pool<Vec2> vecPool = Pools.get(Vec2.class, Vec2::new);
  
  public SnakeFollow(Player target) {
    super(target);
    trail.add(vecPool.obtain().set(target));
  }
  
  @Override
  protected void add0(Player player) {
    totalHitSize += hitSize(player);
    adaptTrail();
  }
  
  @Override
  protected void remove0(Player player) {
    totalHitSize -= hitSize(player);
    adaptTrail();
  }  

  @Override
  protected void clear0() {
    totalHitSize = 0;
    adaptTrail();
  }
  
  /** Update the position of the leader. */
  @Override
  protected void preUpdate() {
    checkTrail();
    Vec2 current = vecPool.obtain().set(getLeader());

    while (getLeader().dst(leader) >= distance) {
      moveLeader();
      getLeader().set(current.approach(leader, distance));
    }

    totalDistance = followed.dead() ? 1f : followed.unit().hitSize + leader.dst(getLeader());
    vecPool.free(current);
  }

  @Override
  protected void update(Vec2 out, int index, Player player) {
    totalDistance += hitSize(player);
    float offset = leaderI + totalDistance / distance;
    Vec2 source = get((int)Math.ceil(offset % trail.size)),
         target = totalDistance >= 0 ? get((int)offset) : leader;
    out.set(source).lerp(target, 1 - (offset % 1));
    totalDistance += hitSize(player) + distance;
  }

  /** 
   * Recalculates the trail if {@link #playerDistance} has been modified or 
   * if one of the followers has changed of size. 
   */
  public void checkTrail() {
    float total = followers.sumf(this::hitSize);
    
    if (playerDistance != distance || total != totalHitSize) {
      distance = playerDistance;
      if (distance < 1) distance = 1;
      totalHitSize = total;
      adaptTrail();
    }    
  }

  /**
   * Automatically adapt the trail. <br>
   * Must be used instead of {@link #increaseTrail(int, int, Vec2, Vec2)} and {@link #decreaseTrail(int)}.
   */
  public void adaptTrail() {
    int delta = totalSize()+1 - trail.size;
    
    if (delta > 0) {
      if (trail.size > 1) increaseTrail(leaderI, delta, get(leaderI-1), get(leaderI-2));
      else increaseTrail(leaderI, delta, get(leaderI-1), null);
    } else if (delta < 0) decreaseTrail(-delta);
  }

  /**
   * Increases the trail from {@code at} index, of an {@code amount} of points, with a {@code dest} position,
   * and an optional {@code awaitFrom} position.
   */
  public void increaseTrail(int at, int amount, Vec2 dest, Vec2 awayFrom) {
    if (amount <= 0) return;
    if (leaderI >= at) leaderI += amount;
    
    // Deals with the backing array if amount > 1, for optimization purposes
    if (amount == 1) {
      if (awayFrom == null) trail.insert(at, vecPool.obtain().set(dest));
      else trail.insert(at, vecPool.obtain().set(dest).scl(2).sub(awayFrom));
    } else {
      Object[] items = trail.ensureCapacity(amount);
      System.arraycopy(items, at, items, at + amount, trail.size - at);
      if (awayFrom == null) {
        for (int i=0; i<amount; i++) items[at + i] = vecPool.obtain().set(dest);
      } else {
        Vec2 offset = vecPool.obtain().set(dest).sub(awayFrom), position = vecPool.obtain().set(dest);
        for (int i=0; i<amount; i++) items[at + i] = vecPool.obtain().set(position.add(offset));
        vecPool.free(offset);
        vecPool.free(position);
      }
      trail.size += amount;
    }
  }
  
  /** Removes an {@code amount} of points from the end of the trail (circular, starting at {@link #leaderI}). */
  public void decreaseTrail(int amount) {
    if (amount <= 0) return;
    int newSize = trail.size - amount;
    if (newSize <= 0) {
      vecPool.freeAll(trail);
      trail.clear();
      trail.items = (Vec2[])new Object[8]; //free memory
      leaderI = 0;
      return;
    }
    
    // Deals with the backing array if amount > 1, for optimization purposes
    if (amount == 1) {
      moveLeader();
      vecPool.free(trail.remove(leaderI));
    } else {
      Object[] items = trail.items;
      for (int i=0; i<leaderI; i++) vecPool.free((Vec2)items[i]);
      System.arraycopy(items, leaderI, items, Math.max(0, leaderI - amount), trail.size - leaderI);
      for (int i=newSize; i<trail.size; i++) {
        vecPool.free((Vec2)items[i]);
        items[i] = null;
      }
      trail.size = newSize;
      moveLeader(amount);  
    }
    
    // Shrink capacity if too large
    int capacityNeeeded = (int)Math.max(newSize * 1.75, 8);
    if (((Object[])trail.items).length > capacityNeeeded) {
      trail.size = capacityNeeeded; // fake the size
      trail.shrink();
      trail.size = newSize;
    }
  }

  /** Gets the size of a follower, in the trail. */
  public float size(Player player) {
    return (player.unit().hitSize + distance) / distance;
  }
  
  /** Gets the total size of the trail. */
  public int totalSize() {
    return (int)(size(followed) + followers.sumf(this::size));
  }

  /** Moves the leader index 1 point backward, in the trail. */
  public void moveLeader() {
    moveLeader(1);
  }
  
  /** Moves the leader index {@code n} points backward, in the trail. */
  public void moveLeader(int n) {
    leaderI = Math.floorMod(leaderI - n, trail.size);
  }
  
  /** Gets the leader point */
  public Vec2 getLeader() {
    return trail.get(leaderI);
  }
  
  /** Gets a point in the trail (circular). */
  public Vec2 get(int i) {
    return trail.get(Math.floorMod(i, trail.size));
  }
}