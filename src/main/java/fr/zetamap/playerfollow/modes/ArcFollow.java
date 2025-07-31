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

import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.struct.FloatSeq;
import arc.struct.Seq;
import arc.util.pooling.Pool;
import arc.util.pooling.Pools;
import mindustry.Vars;
import mindustry.gen.Player;


public class ArcFollow extends fr.zetamap.playerfollow.Follow {
  /** Radius between rings */
  public static float ringGap = 3f * Vars.tilesize;
  /** Spacing between players */
  public static float playerSpacing = 2f * Vars.tilesize;
  /** Max angle for each side, at back of the leader */
  public static float maxSidesAngle = 90f * Mathf.degRad;

  public Seq<Ring> rings = new Seq<>();
  
  private int ringI = 0, followerI = 0;
  private float gap = ringGap, spacing = playerSpacing, totalHitSize = 0, maxAngle = maxSidesAngle;
  private final FloatSeq toAdd = new FloatSeq(), chords = new FloatSeq();
  private final Pool<Ring> ringPool = Pools.get(Ring.class, Ring::new);

  public ArcFollow(Player target) {
    super(target);
  }
  
  @Override
  protected void add0(Player player) {
    totalHitSize += hitSize(player);
    adaptRings();
  }
  
  @Override
  protected void remove0(Player player) {
    totalHitSize -= hitSize(player);
    adaptRings();
  } 
  
  @Override
  protected void clear0() {
    totalHitSize = 0;
    adaptRings();
  }
  
  @Override
  protected void preUpdate() {
    checkRings();
    ringI = followerI = 0;
  }
  
  @Override
  protected void update(Vec2 out, int index, Player player) {
    Ring ring = rings.get(ringI);
    float shift = (followed.unit().rotation + 180) * Mathf.degRad + ring.angles.get(followerI);
    out.add(Mathf.cos(shift) * ring.radius, Mathf.sin(shift) * ring.radius);

    if (++followerI >= ring.angles.size) {
      ringI++;
      followerI = 0;
    }
  }

  /** Update arcs and follower placement */
  public void adaptRings() {
    toAdd.clear();
    chords.clear();
    if (followers.isEmpty()) toAdd.add(0);
    for (int i=0, n=followers.size; i<n; i++) {
      toAdd.add(hitSize(followers.get(i)));
      if (i < n-1) chords.add(toAdd.peek() + spacing + hitSize(followers.get(i+1)));
    }

    int ringI = 0, startI = 0, endI = -1, maxI = followers.size-1, i, ringFollowers;
    float lastBiggest = 0, totalRadius = Math.max(1, gap + hitSize(followed)),
          biggest = toAdd.first(), angle = edgeAngle(totalRadius + biggest, biggest),
          size, radius, extra;
    boolean overfits;

    while (endI < maxI) {
      endI++;
      size = toAdd.get(endI);
      radius = totalRadius + biggest;
      
      // Recalculate angles if a follower too big
      if (size > biggest) {
        lastBiggest = biggest;
        biggest = size;
        radius = totalRadius + biggest;
        // Recalculate previous angles
        angle = edgeAngle(radius, toAdd.get(startI));
        for (i=startI; i<endI-1; i++) angle += advanceOnCircle(radius, chords.get(i));
      }

      if (endI - startI >= 1) angle += advanceOnCircle(radius, chords.get(endI-1));
      
      overfits = endI - startI > 0 && angle + edgeAngle(radius, toAdd.get(endI)) > maxAngle;
      
      // Place followers on the ring
      if (overfits || endI >= maxI) {
        // Remove the follower who is overflowing
        if (overfits) {
          endI--;
  
          if (toAdd.get(endI+1) > size) {
            biggest = lastBiggest;
            radius = totalRadius + biggest;
            angle = advanceOnCircle(radius, toAdd.get(startI));
            for (i=startI; i<endI-1; i++) angle += advanceOnCircle(radius, chords.get(i));
          } else if (endI - startI >= 0) angle -= advanceOnCircle(radius, chords.get(endI));
        }  
        
        angle += edgeAngle(radius, toAdd.get(endI));

        // Create the new ring with every selected followers
        Ring ring = getCreateRing(ringI);
        ring.radius = radius;
        ring.angles.clear();
        if (endI == startI) ring.angles.add(0);
        else {
          ringFollowers = endI - startI;
          extra = (maxAngle - angle) / ringFollowers;
          ring.angles.add(edgeAngle(radius, toAdd.get(startI)) - maxAngle/2);
          for (i=startI; i<endI; i++)
            ring.angles.add(ring.angles.peek() + extra + advanceOnCircle(ring.radius, chords.get(i)));
        }

        // Progress
        totalRadius = ring.radius + biggest + gap;
        ringI++;

        startI = endI+1;
        if (startI < toAdd.size) {
          biggest = toAdd.get(startI);
          lastBiggest = 0;
          angle = advanceOnCircle(totalRadius + biggest, biggest);
        }
      }
    }

    // Remove empty rings
    if (ringI < rings.size-1) {
      for (i=ringI; i<rings.size; i++) ringPool.free(rings.get(i));
      rings.removeRange(ringI, rings.size-1);
    }
  }

  /** Recalculate the rings if .gap, .spacing or a follower size has been changed */
  public void checkRings() {
    float total = followers.sumf(this::hitSize);
    if (maxAngle != maxSidesAngle ||
        gap != ringGap || 
        spacing != playerSpacing || 
        total != totalHitSize) {
      maxSidesAngle = Mathf.clamp(maxSidesAngle, 0.03f, Mathf.PI2);
      ringGap = Math.max(ringGap, 1);
      playerSpacing = Math.max(playerSpacing, 0);
      maxAngle = maxSidesAngle;
      gap = ringGap;
      spacing = playerSpacing;
      totalHitSize = total;
      adaptRings();
    }
  }

  /** Create missing rings if needed and return the requested one */
  public Ring getCreateRing(int index) {
    for (int i=0; i<index-rings.size+1; i++) rings.add(ringPool.obtain());
    return rings.get(index);

  }
  
  
  public static float advanceOnCircle(float radius, float chord) { return advanceOnCircle(radius, chord, Mathf.PI2); }
  public static float advanceOnCircle(float radius, float chord, float fallback) {
    float alpha = chord / (2f * radius);
    // This can rarely happen if follower spacing is higher than ring spacing
    if (alpha < -1 || alpha > 1) return fallback;
    return (float)(2f * Math.asin(alpha));
  }
  
  public static float edgeAngle(float radius, float distance) { return edgeAngle(radius, distance, 0f); }
  public static float edgeAngle(float radius, float distance, float fallback) {
    float alpha = distance / radius;
    if (alpha < -1 || alpha > 1) return fallback;
    return (float)Math.asin(alpha);
  }
  
  
  /** Pooled */
  public static class Ring implements Pool.Poolable {
    public float radius = 1;
    public FloatSeq angles = new FloatSeq();
    
    @Override
    public void reset() {
      radius = 1;
      angles.clear();
      angles.items = new float[8]; //free the memory
    }
  }
}
