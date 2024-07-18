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
import arc.struct.ObjectMap;
import mindustry.gen.Player;


public class JointFollow extends player_follow.Follower {
  /** Distance between players */
  public static int playerDistance = 2 * mindustry.Vars.tilesize;
  
  private ObjectMap<Player, Vec2> last = new ObjectMap<>();
  
  public JointFollow(Player player) {
    super(player);
  }

  @Override
  public boolean removeFollower(Player player) {
    last.remove(player);
    return super.removeFollower(player);
  }  

  @Override
  public Vec2 updateFollower(int index, Player player) {
    Player target = index == 0 ? followed : following.get(index - 1);
    Vec2 dest = last.get(player, () -> new Vec2().set(player)),
         followerVec = dest.cpy(), 
         targetVec = new Vec2(target.x, target.y);
    float distance = followerVec.dst(targetVec),
          minDistance = playerDistance + target.unit().hitSize/2 + player.unit().hitSize/2;

    if (distance > minDistance) {
      dest.set(followerVec.add(targetVec.sub(followerVec).div(new Vec2(distance, distance)).scl(distance - minDistance)));
      last.put(player, dest);
    }

    return dest;
  }
}
