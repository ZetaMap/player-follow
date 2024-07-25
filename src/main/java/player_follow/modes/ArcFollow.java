/*
 * This file is part of Player Follow. The plugin that allow players to follow each others.
 *
 * MIT License
 *
 * Copyright (c) 2024 ZetaMap
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

import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.struct.Seq;
import mindustry.gen.Player;

import static mindustry.Vars.tilesize;


public class ArcFollow extends player_follow.Follower {
  /** Radius between rings */
  public static int ringGap = 3 * tilesize;
  /** Spacing between players */
  public static int playerSpacing = 2 * tilesize;
  /** Max angle each side, at back of the leader */
  public static float maxSidesAngle = 90 * Mathf.degRad;

  public Seq<Ring> rings = new Seq<>();
  private int gap = ringGap, spacing = playerSpacing, 
              ringI = 0, followerI = 0;
  private float totalHitSize = 0, maxAngle = maxSidesAngle;
  

  public ArcFollow(Player player) {
    super(player);
  }
  
  @Override
  public void addFollower(Player player) {
    super.addFollower(player);
    totalHitSize += getHitSize(player);
    adaptRings();
  }
  
  @Override
  public boolean removeFollower(Player player) {
    boolean removed = super.removeFollower(player);
    if (removed && !following.isEmpty()) {
      totalHitSize -= getHitSize(player);
      adaptRings();
    }
    return removed;
  } 
  
  @Override
  public void updatePos() {
    checkRings();
    ringI = followerI = 0;
  }
  
  @Override
  public Vec2 updateFollower(int index, Player player) {
    float shift = (followed.unit().rotation + 180) * Mathf.degRad + rings.get(ringI).angles[followerI];
    Vec2 dest = leader.cpy().add(new Vec2(Mathf.cos(shift), Mathf.sin(shift)).scl(rings.get(ringI).radius));
    
    if (followerI++ >= rings.get(ringI).angles.length) {
      ringI++;
      followerI = 0;
    }
    
    return dest;
  }

  /** 
   * Update arcs and follower placement <br><br>
   * 
   * TODO: this method need optimizations.
   */
  public void adaptRings() {
    // Caches
    float[] toAdd = new float[following.size],
            chords = new float[following.size - 1];
    for (int i=0; i<following.size; i++)
      toAdd[i] = getHitSize(following.get(i));
    for (int i=0; i<following.size - 1; i++)
      chords[i] = getHitSize(following.get(i)) + spacing + getHitSize(following.get(i+1));

    int ringI = 0, startI = 0, endI = -1;
    float lastBiggest = 0, totalRadius = Math.max(1, gap + getHitSize(followed)),
          biggest = toAdd[0], angle = getEdgeAngle(totalRadius + biggest, toAdd[startI]);

    while (endI < toAdd.length - 1) {
      endI++;
      float size = toAdd[endI], radius = totalRadius + biggest;

      if (size > biggest) {
        lastBiggest = biggest;
        biggest = size;
        radius = totalRadius + biggest;
        // Recalculate previous angles
        angle = getEdgeAngle(radius, toAdd[startI]);
        for (int i=startI; i<endI - 1; i++) angle += advanceOnCircle(radius, chords[i]);
      }

      if (endI - startI >= 1) angle += advanceOnCircle(radius, chords[endI - 1]);
      
      boolean overfits = (endI - startI > 0 && angle + getEdgeAngle(radius, toAdd[endI]) > maxAngle);
      if (overfits || endI >= toAdd.length - 1) {
        if (overfits) {
          // Remove the follower who is overflowing
          endI--;

          if (toAdd[endI + 1] > size) { // Don't use Math.min() it won't work in every case
            biggest = lastBiggest;
            radius = totalRadius + biggest;
            angle = advanceOnCircle(radius, toAdd[startI]);
            for (int i=startI; i<endI - 1; i++) 
              angle += advanceOnCircle(radius, chords[i]);
          } else if (endI - startI >= 0) 
            angle -= advanceOnCircle(radius, chords[endI]);
        }

        angle += getEdgeAngle(radius, toAdd[endI]);

        // Create the new ring with every selected followers
        Ring ring = this.getRing(ringI);
        ring.radius = radius;
        ring.angles = new float[endI - startI];
        ring.angles[0] = (maxAngle - angle) / 2 + advanceOnCircle(ring.radius, toAdd[startI]);
        for (int i=startI, ii=1; i < endI; i++, ii++) 
          ring.angles[ii] = ring.angles[ii-1] + advanceOnCircle(ring.radius, chords[i]);

        // Progress
        totalRadius = ring.radius + biggest + gap;
        ringI++;

        // Clean up variables
        startI = endI + 1;
        if (startI < toAdd.length) {
          biggest = toAdd[startI];
          lastBiggest = 0;
          angle = advanceOnCircle(totalRadius + biggest, biggest);
        }
      }
    }

    // Remove empty rings
    if (ringI < rings.size-1) rings.removeRange(ringI, rings.size-1);
}

  
  /** Recalculate the rings if .gap, .spacing or a follower size has been changed */
  public void checkRings() {
    float total = following.sumf(p -> getHitSize(p));
    if (maxAngle != maxSidesAngle ||
        gap != ringGap || 
        spacing != playerSpacing || 
        total != totalHitSize) {
      maxSidesAngle = Mathf.clamp(maxAngle, Mathf.PI2, 0.03f);
      ringGap = Math.max(ringGap, 1);
      playerSpacing = Math.max(playerSpacing, 0);
      gap = ringGap;
      spacing = playerSpacing;
      totalHitSize = total;
      adaptRings();
    }
  }

  /** Get the size of the player */
  public float getHitSize(Player player) {
    return Math.max(1, player.unit().hitSize / 2);
  }
  
  /** Create missing rings if needed and return the requested one */
  public Ring getRing(int index) {
    for (int i=0; i<index-rings.size+1; i++)
      rings.add(new Ring());
    return rings.get(index);
  }
  
  
  public static float advanceOnCircle(float radius, float chord) { return advanceOnCircle(radius, chord, Mathf.PI2); }
  public static float advanceOnCircle(float radius, float chord, float fallback) {
    float alpha = chord / (2f * radius);
    // This can rarely happen if follower spacing is higher than ring spacing
    if (alpha < -1 || alpha > 1) return fallback;
    return (float)(2f * Math.asin(alpha));
  }
  
  public static float getEdgeAngle(float radius, float distance) { return getEdgeAngle(radius, distance, 0f); }
  public static float getEdgeAngle(float radius, float distance, float fallback) {
    float alpha = distance / radius;
    if (alpha < -1 || alpha > 1) return fallback;
    return (float)Math.asin(alpha);
  }
  
  
  public class Ring {
    public float radius = 1;
    public float[] angles;
  }
}
