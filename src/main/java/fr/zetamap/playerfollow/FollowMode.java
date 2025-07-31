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

package fr.zetamap.playerfollow;

import arc.func.Cons;
import arc.func.Func;
import arc.struct.ObjectMap;

import mindustry.gen.Player;

import fr.zetamap.playerfollow.modes.*;


public class FollowMode {
  protected static final ObjectMap<String, FollowMode> modes = new ObjectMap<>();
  protected static final ObjectMap<Class<?>, FollowMode> modesTypes = new ObjectMap<>();
  
  public final String name;
  public final Class<?> type;
  final Func<Player, Follow> constructor;
  
  FollowMode(String name, Class<?> type, Func<Player, Follow> constructor) {
    this.name = name; 
    this.type = type;
    this.constructor = constructor;
  }
  
  /** Consider using {@link FollowManager#add(FollowMode, Player)} instead, for a proper registration. */
  @SuppressWarnings("unchecked")
  public <T extends Follow> T create(Player target) {
    return (T)constructor.get(target);
  }
  

  public static <T extends Follow> FollowMode add(String name, Class<T> type, Func<Player, T> mode) {
    @SuppressWarnings("unchecked")
    FollowMode m = new FollowMode(name, type, (Func<Player, Follow>)mode);
    modes.put(name, m);
    modesTypes.put(type, m);
    return m;
  }
  
  public static FollowMode of(String name) {
    return modes.get(name);
  }
  
  public static FollowMode of(Follow follow) {
    return modesTypes.get(follow.getClass());
  }
  
  public static FollowMode of(Class<? extends Follow> type) {
    return modesTypes.get(type);
  }
  
  
  public static void each(Cons<FollowMode> consumer) {
    modes.each((n, m) -> consumer.get(m));
  }
  
  
  // Default modes
  public static final FollowMode
    arc = add("arc", ArcFollow.class, ArcFollow::new),
    joint = add("joint", JointFollow.class, JointFollow::new),
    snake = add("snake", SnakeFollow.class, SnakeFollow::new),
    orbit = add("orbit", OrbitFollow.class, OrbitFollow::new);
}
