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
import arc.util.Time;
import arc.util.pooling.Pool;
import arc.util.pooling.Pools;

import mindustry.Vars;
import mindustry.gen.Player;


public class OrbitFollow extends fr.zetamap.playerfollow.Follow {
  /** Radius between rings */
  public static float ringGap = 3f * Vars.tilesize;
  /** Minimum spacing between players */
  public static float playerSpacing = 2f * Vars.tilesize;
  /** Degrees added each times to each rings */
  public static float angleSpeed = (1f * Vars.tilesize) / Vars.tilesize;

  public Seq<Ring> rings = new Seq<>();
  
  private int ringI = 0, followerI = 0;
  private float totalHitSize = 0, gap = ringGap, spacing = playerSpacing, angle = angleSpeed;
  private final FloatSeq chords = new FloatSeq();
  private final Pool<Ring> ringPool = Pools.get(Ring.class, Ring::new);
  
  public OrbitFollow(Player target) {
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
    if (rings.isEmpty()) return;
    checkRings();
    
    // Update rings angle
    rings.each(r -> r.addAngle(angle));
    
    ringI = followerI = 0;
  }

  @Override
  protected void update(Vec2 out, int index, Player player) {
    Ring ring = rings.get(ringI);
    float shift = ring.angle + ring.angles.get(followerI);
    out.add(Mathf.cos(shift) * ring.radius, Mathf.sin(shift) * ring.radius);
    
    if (++followerI >= ring.angles.size) {
      ringI++;
      followerI = 0;
    }
  }

  /**
   * There are two methods implemented <a href="https://github.com/xorblo-doitus/queue_leu_leu/blob/main/src/queue_leu_leu/orbit/orbit.py"> on the Python repo</a>. <br>
   * An approximative one: <a href="https://github.com/xorblo-doitus/queue_leu_leu/blob/176cdb0dd6895744e29aa5191a8d55f53dbd203b/src/queue_leu_leu/orbit/orbit.py#L77">adapt_compact_approx()</a>.
   * And an exact one: <a href="https://github.com/xorblo-doitus/queue_leu_leu/blob/176cdb0dd6895744e29aa5191a8d55f53dbd203b/src/queue_leu_leu/orbit/orbit.py#L123">adapt_compact()</a>. <br>
   * I chose the approximate method for optimization purposes, at the cost of slight follower overlap in some cases.
   */
  public void adaptRings() {
    int inRing = 0, ringI = 0, maxI = followers.size-1, n, i, ii;
    float angle = 0, biggest = hitSize(followed), totalRadius = gap + biggest,
          size, radius, totalAngle, extra;

    chords.clear();
    for (i=0; i<maxI; i++) chords.add(hitSize(followers.get(i)) + spacing + hitSize(followers.get(i+1)));

    for (i=0, n=followers.size; i<n; i++) {
      inRing++;
      size = hitSize(followers.get(i));
      radius = totalRadius + biggest;
      
      if (size > biggest) {
        biggest = size;
        radius = totalRadius + biggest;
        // Recalculate previous angle
        angle = 0;
        for (ii=i-inRing+1; ii<i-1; ii++) angle += advanceOnCircle(radius, chords.get(ii));
      }

      if (inRing >= 2) angle += advanceOnCircle(radius, chords.get(i-1));
      
      totalAngle = angle + advanceOnCircle(radius, hitSize(followers.get(i-inRing+1)) + spacing + size); 
      if ((inRing > 2 && totalAngle > Mathf.PI2) || i >= maxI) {
        angle = totalAngle;
        
        // Create the new ring, or reuse them, with every selected followers
        Ring ring = getCreateRing(ringI);
        ring.radius = radius;
        ring.angles.clear();
        extra = (Mathf.PI2 - angle) / inRing;
        ring.angles.add(0);
        for (ii=i-inRing+1; ii<i; ii++) 
          ring.angles.add(ring.angles.peek() + extra + advanceOnCircle(ring.radius, chords.get(ii)));
        
        totalRadius += gap + 2*biggest;
        ringI++;
        angle = biggest = 0;
        inRing = 0;
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
    if (gap != ringGap || 
        spacing != playerSpacing || 
        total != totalHitSize) {
      ringGap = Math.max(ringGap, 1);
      playerSpacing = Math.max(playerSpacing, 0);
      gap = ringGap;
      spacing = playerSpacing;
      angle = angleSpeed;
      totalHitSize = total;
      adaptRings();
    }
  }

  /** Create missing rings if needed and return the requested one */
  public Ring getCreateRing(int index) {
    for (int i=0; i<index-rings.size+1; i++) rings.add(ringPool.obtain().set(index+i));
    return rings.get(index);
  }
  
  
  public static float advanceOnCircle(float radius, float chord) { return advanceOnCircle(radius, chord, Mathf.PI2); }
  public static float advanceOnCircle(float radius, float chord, float fallback) {
    float alpha = chord / (2 * radius);
    // This can rarely happen if follower spacing is higher than ring spacing
    if (alpha < -1 || alpha > 1) return fallback;
    return (float)(2 * Math.asin(alpha));
  }
  
  
  /** Pooled */
  public static class Ring implements Pool.Poolable {
    public float angle = 0, radius = 1;
    public FloatSeq angles = new FloatSeq();
    public boolean pair;
    
    public Ring set(int i) {
      pair = i % 2 == 0;
      return this;
    }
    
    /** Adds or subs angle, alternately according to {@link #pair} */
    public void addAngle(float degrees) {
      if (pair) angle += Mathf.degRad * degrees;// * Time.delta;
      else angle -= Mathf.degRad * degrees;// * Time.delta;
      angle %= Mathf.PI2;
    }

    @Override
    public void reset() {
      pair = false;
      angle = 0;
      radius = 1;
      angles.clear();
      angles.items = new float[8]; //free memory
    }
  }
}
