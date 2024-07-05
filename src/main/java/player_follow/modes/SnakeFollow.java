/*
 * This file is part of Player Follow. The plugin that allow players to follow each others.
 *
 * MIT License
 *
 * Copyright (c) 2023 ZetaMap
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

package player_follow.modes;

import arc.math.geom.Vec2;
import arc.struct.Seq;
import mindustry.gen.Player;


public class SnakeFollow extends player_follow.Follower {
  public static int playerDistance = 2 * mindustry.Vars.tilesize; // Distance between each players
  
  public Vec2 leader = new Vec2();
  public Seq<Vec2> trail = new Seq<>();
  private int distance, leaderPos = 0;
  private float tempPos = 0, totalHitSize = 0;
  
  public SnakeFollow(Player player) {
    super(player);
    distance = playerDistance;
    trail.add(new Vec2(player.x, player.y));
  }

  @Override
  public void playerFollowPreProcessing() {
    updatePos(new Vec2(followed.x, followed.y));
  }
  
  @Override
  public Vec2 computePlayerFollow(int index, Player player) {
    return updateFollower(player);
  }
  
  @Override
  public void addFollower(Player player) {
    super.addFollower(player);
    totalHitSize += player.unit().hitSize;
    adaptTrail();
  }
  
  @Override
  public boolean removeFollower(Player player) {
    boolean removed = super.removeFollower(player);
    if (removed && !following.isEmpty()) {
      totalHitSize -= player.unit().hitSize;
      adaptTrail();
    }
    return removed;
  }
  
  /* Update the position of the leader. */
  public void updatePos(Vec2 newPos) {
    checkTrail();
    leader = newPos;
    Vec2 current = trail.get(leaderPos).cpy();

    while (trail.get(leaderPos).dst(leader) >= getDistance()) {
      leaderPos = wrapped(leaderPos - 1);
      trail.set(leaderPos, current.approach(leader, getDistance()).cpy());
    }

    tempPos = followed.unit().hitSize + getDistance() + leader.dst(trail.get(leaderPos));
  }

  /** {@link #updatePos(Vec2)} must be called before updating followers. */
  public Vec2 updateFollower(Player player) {
    tempPos += player.unit().hitSize;
    float offset = leaderPos + tempPos / getDistance();
    tempPos += player.unit().hitSize + getDistance();
    
    Vec2 source = trail.get(wrapped((int)Math.ceil(offset % trail.size))).cpy(),
         target = tempPos >= 0 ? trail.get(wrapped((int)(offset % trail.size))) : leader;
    return source.lerp(target, 1 - (offset % 1));
  }

  /** 
   * Recalculate trail if {@link #playerDistance} has been modified or 
   * if one of the followers changed of size. 
   */
  public void checkTrail() {
    float total = following.sumf(p -> p.unit().hitSize);
    
    if (playerDistance != distance || total != totalHitSize) {
      distance = playerDistance;
      totalHitSize = total;
      adaptTrail();
    }    
  }

  /**
   * Automatically adapt the trail. <br>
   * Must be used instead of {@link #increaseTrail(int, int, Vec2, Vec2)} and {@link #decreaseTrail(int)}.
   */
  public void adaptTrail() {
    int delta = getTotalSize() + 1 - trail.size;
    
    if (delta > 0) {
      Vec2 from = trail.size > 1 ? trail.get(wrapped(leaderPos - 2)) : leader;
      increaseTrail(leaderPos, delta, trail.get(wrapped(leaderPos - 1)), from);
    } else if (delta < 0) decreaseTrail(-delta);
  }
  
  /**
   * Increase the trail {@code at} an index, of an {@code amount} of points with a dest {@code position}
   * and an {@code await_from} position.
   */
  public void increaseTrail(int at, int amount, Vec2 pos, Vec2 awayFrom) {
    if (leaderPos >= at) leaderPos += amount;
    
    Vec2 offset = pos.cpy().sub(awayFrom), position = pos.cpy();
    for (int i=0; i<amount; i++)
      trail.insert(at + i, position.add(offset).cpy());
  }
  
  /** Remove an {@code amount} of points at end of the trail. */
  public void decreaseTrail(int amount) {
    Seq<Vec2> newTrail = new Seq<>();
    
    for (int i=leaderPos-trail.size; i<leaderPos-amount; i++)
      newTrail.add(trail.get(wrapped(i)));

    trail = newTrail;
    leaderPos = 0;
  }
  
  /** Get the size of a follower (in the trail). */
  public float getSize(Player player) {
    return (2 * player.unit().hitSize + getDistance()) / getDistance();
  }
  
  /** Get the total size of the trail. */
  public int getTotalSize() {
    return (int)(getSize(followed) + following.sumf(p -> getSize(p)));
  }
  
  /** Security to never get a distance less than 0. */
  public int getDistance() {
    if (distance < 1) distance = 1;
    return distance;
  }
  
  /** Get the leader index in the trail. */
  public int getLeaderPos() {
    return leaderPos;
  }
  
  /** Cyclic index of the trail. */
  public int wrapped(int i) {
    return Math.floorMod(i, trail.size);
  }
}
