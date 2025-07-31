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
import arc.struct.ObjectMap;
import arc.util.pooling.Pool;
import arc.util.pooling.Pools;
import mindustry.gen.Player;


public class JointFollow extends fr.zetamap.playerfollow.Follow {
  /** Distance between players */
  public static float playerDistance = 2f * mindustry.Vars.tilesize;
  
  private final ObjectMap<Player, Vec2> last = new ObjectMap<>();
  private final Pool<Vec2> vecPool = Pools.get(Vec2.class, Vec2::new);
  
  public JointFollow(Player target) {
    super(target);
  }

  @Override
  protected void remove0(Player player) {
    Vec2 v = last.remove(player);
    if (v != null) vecPool.free(v);
  }  
  
  @Override
  protected void clear0() {
    last.each((p, v) -> vecPool.free(v));
    last.clear();
  } 

  @Override
  protected void update(Vec2 out, int index, Player player) {
    Player target = index == 0 ? followed : followers.get(index-1);
    Vec2 dest = last.get(player, () -> vecPool.obtain().set(player));
    out.set(target); // reuse 'out' instead of creating another Vec2
    float distance = dest.dst(out), minDistance = playerDistance + hitSize(target) + hitSize(player);

    if (distance > minDistance) {
      out.sub(dest);
      out.x /= distance; // There is no method to divide by a scalar instead of a vector
      out.y /= distance; //
      dest.add(out.scl(distance - minDistance));
    }

    out.set(dest);
  }
}
