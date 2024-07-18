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


public class OrbitFollow extends player_follow.Follower {
  public static float SPEED_SCALE = 1f / tilesize;
  /** Radius between rings */
  public static int ringGap = 3 * tilesize;
  /** Minimum spacing between players */
  public static int playerSpacing = 2 * tilesize;
  /** Speed of the orbit */
  public static int angleSpeed = 2;

  public Seq<Ring> rings = new Seq<>();
  private int gap = ringGap, spacing = playerSpacing, 
              speed = angleSpeed, ringI = 0, 
              followerI = 0;
  private float totalHitSize = 0;
  
  public OrbitFollow(Player player) {
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
    if (rings.isEmpty()) return;
    checkRings();
    
    // Update rings angle
    rings.each(r -> r.addAngle(speed * SPEED_SCALE));
    
    ringI = followerI = 0;
  }
  
  @Override
  /** {@link #updatePos()} must be called before. */
  public Vec2 updateFollower(int index, Player player) {
    float shift = rings.get(ringI).angle + rings.get(ringI).angles[followerI];
    Vec2 dest = leader.cpy().add(new Vec2(Mathf.cos(shift), Mathf.sin(shift)).scl(rings.get(ringI).radius));
    
    if (followerI++ >= rings.get(ringI).angles.length) {
      ringI++;
      followerI = 0;
    }
    
    return dest;
  }

  /**
   * Place followers with even spacing between them. <br>
   * WARNING: this method can create a little overlapping followers. <br><br>
   * 
   * Others methods can be found <a href="https://github.com/xorblo-doitus/queue_leu_leu/blob/main/src/queue_leu_leu/orbit/orbit.py#L78"> on the Python repo</a>.<br>
   * I choice this method because it do the job, and more simple to implement.
   */
  public void adaptRings() {
    int ring = 0, maxI = following.size - 1;
    float angle = 0,
          biggest = getHitSize(followed),
          totalRadius = gap + biggest;
    Seq<Float> inRing = new Seq<>();

    for (int i=0; i<following.size; i++) {
      inRing.add(getHitSize(following.get(i)));
      float size = inRing.peek(),
            newRadius = totalRadius + biggest;
      
      if (size > biggest) {
        biggest = size;
        newRadius = totalRadius + biggest;
        // Recalculate previous angle
        angle = 0;
        for (int ii=0; ii<inRing.size-2; ii++) 
          angle += advanceOnCircle(newRadius, inRing.get(ii) + spacing + inRing.get(ii+1));
      }

      if (inRing.size >= 2) 
        angle += advanceOnCircle(newRadius, inRing.get(inRing.size-2) + spacing + size);
      
      float totalAngle = angle + advanceOnCircle(newRadius, inRing.first() + spacing + size);
      
      if ((inRing.size > 2 && totalAngle > Mathf.PI2) || i >= maxI) {
        angle = totalAngle;
        
        // Create the new ring, or reuse them, with every selected followers
        Ring r = getRing(ring);
        r.radius = newRadius;
        r.angles = new float[inRing.size];
        r.angles[0] = 0;
        float step = (Mathf.PI2 - angle) / inRing.size;
        for (int ii=1; ii<inRing.size; ii++)
          r.angles[ii] = r.angles[ii-1] + step + 
                         advanceOnCircle(r.radius, inRing.get(ii-1) + spacing + inRing.get(ii));
        
        totalRadius += gap + 2*biggest;
        ring++;
        angle = biggest = 0;
        inRing.clear();
      }
    }
    
    // Remove empty rings
    if (ring < rings.size-1) rings.removeRange(ring, rings.size-1);
  }
  
  /** Recalculate the rings if .gap, .spacing or a follower size has been changed */
  public void checkRings() {
    float total = following.sumf(p -> getHitSize(p));
    if (gap != ringGap || spacing != playerSpacing || total != totalHitSize) {
      ringGap = Math.max(ringGap, 1);
      gap = ringGap;
      playerSpacing = Math.max(playerSpacing, 0);
      spacing = playerSpacing;
      totalHitSize = total;
      adaptRings();
    }
    
    // Clamp the speed
    if (angleSpeed != speed) {
      angleSpeed = (int)Mathf.clamp(angleSpeed, -180 / SPEED_SCALE, 180 / SPEED_SCALE);
      speed = angleSpeed;
    }
}

  /** Get the size of the player */
  public float getHitSize(Player player) {
    return Math.max(1, player.unit().hitSize / 2);
  }
  
  /** Create missing rings if needed and return the requested one */
  public Ring getRing(int index) {
    for (int i=0; i<index-rings.size+1; i++)
      rings.add(new Ring(index+i));
    return rings.get(index);
  }
  
  
  public static float advanceOnCircle(float radius, float chord) { return advanceOnCircle(radius, chord, Mathf.PI2); }
  public static float advanceOnCircle(float radius, float chord, float fallback) {
    float alpha = chord / (2 * radius);
    // This can rarely happen if follower spacing is higher than ring spacing
    if (alpha < -1 || alpha > 1) return fallback;
    return (float)(2 * Math.asin(alpha));
  }
  
  
  public class Ring {
    public float angle = 0, radius = 1;
    public float[] angles;
    public final int i;
    
    public Ring(int i) {
      this.i = i;
    }
    
    /** Add or sub angle, alternately according to {@link #i} */
    public void addAngle(float degrees) {
      if (i % 2 == 0) angle += Mathf.degRad * degrees;
      else angle -= Mathf.degRad * degrees;
      angle %= Mathf.PI2;
    }
  }
}
