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

import arc.math.Mathf;
import arc.math.geom.Vec2;
import mindustry.gen.Player;


public class ArcFollow extends player_follow.Follower {
  public static int distance = 2 * mindustry.Vars.tilesize, // Minimum distance between each players
                    angleOffset = 120, // Angle
                    maxAngle = 90; // Top and bottom angle, from the rear of the followed

  public ArcFollow(Player player) { 
    super(player); 
  }
  
  @Override
  public void updatePos() {
    
  }

  @Override
  public Vec2 updateFollower(int index, Player player) {
    // Puts followers behind the followed
    int pAngle = ((index == 0 ? 0 : (index+1)%2 == 0 ? angleOffset : -angleOffset) * Mathf.ceil((index+1)/2));
    float angle = Mathf.degRad * (followed.unit().rotation + 180 + (pAngle != 0 ? pAngle%maxAngle : pAngle));
    // TODO: make a shift for each player who follows him
    float radius = followed.unit().hitSize/2 + (player.unit().hitSize/2 + distance) * (Math.abs(Mathf.ceil(pAngle/maxAngle))+1);
    //for (int p=i; p<following.size; p+=1) radius += player.unit().hitSize/2 + Vars.tilesize * tileOffset;
    //Mathf.within(i, pAngle, angle, radius, i);
    
    return new Vec2(leader.x + Mathf.cos(angle) * radius, leader.y + Mathf.sin(angle) * radius);
  }
}
