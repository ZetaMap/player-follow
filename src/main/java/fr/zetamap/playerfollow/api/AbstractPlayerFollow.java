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

package fr.zetamap.playerfollow.api;

import arc.math.geom.Vec2;
import fr.zetamap.playerfollow.Players;
import mindustry.Vars;
import mindustry.gen.Player;


public abstract class AbstractPlayerFollow extends AbstractFollow<Player> {
  public static float SCALE = Vars.tilesize;

  public AbstractPlayerFollow(Player target) {
    super(target);
  }

  /** Send a message to followers */
  public void message(String message, Object... args) {
    message(Players::warn, message, args);
  }
  
  /** Send a message to followers */
  public void message(arc.func.Cons3<Player, String, Object[]> sender, String message, Object... args) {
    followers.each(p -> sender.get(p, message, args));
  }

  @Override
  protected boolean cannotUpdate(Player player) {
    return player.dead();
  }

  @Override
  protected void setPosition(Player player, Vec2 target) {
    player.unit().set(target);
    player.set(target);
    player.snapInterpolation();
  }

  /** Gets the size of the player */
  protected float hitSize(Player player) {
    return cannotUpdate(player) ? 1f : Math.max(1f, player.unit().hitSize / 2f);
  }
}
