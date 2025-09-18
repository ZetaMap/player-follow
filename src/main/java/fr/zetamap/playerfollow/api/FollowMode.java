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

import arc.func.Cons;
import arc.func.Func;
import arc.math.geom.Position;
import arc.struct.ObjectMap;


@SuppressWarnings("unchecked")
public class FollowMode<T extends Position> {
  protected static final ObjectMap<String, FollowMode<?>> modes = new ObjectMap<>();
  protected static final ObjectMap<Class<?>, FollowMode<?>> modesTypes = new ObjectMap<>();
  
  public final String name;
  public final Class<?> type;
  protected final Func<T, Follow<T>> constructor;

  FollowMode(String name, Class<Follow<T>> type, Func<T, Follow<T>> constructor) {
    this.name = name; 
    this.type = type;
    this.constructor = constructor;
  }
  
  /** Consider using {@link FollowManager#add(FollowMode, Player)} instead, for a proper registration. */
  public <F extends Follow<T>> F create(T target) {
    return (F)constructor.get(target);
  }

  public static <T extends Position, F extends Follow<T>> FollowMode<T> 
                add(String name, Class<F> type, Func<T, F> mode) {
    FollowMode<T> m = new FollowMode<>(name, (Class<Follow<T>>)type, (Func<T, Follow<T>>)mode);
    modes.put(name, m);
    modesTypes.put(type, m);
    return m;
  }

  public static <T extends Position> FollowMode<T> of(String name) {
    return (FollowMode<T>)modes.get(name);
  }
  
  public static <T extends Position> FollowMode<T> of(Follow<T> follow) {
    return (FollowMode<T>)modesTypes.get(follow.getClass());
  }
  
  public static <T extends Position> FollowMode<T> of(Class<Follow<T>> type) {
    return (FollowMode<T>)modesTypes.get(type);
  }
  
  public static void each(Cons<FollowMode<?>> consumer) {
    modes.each((n, m) -> consumer.get(m));
  }
}
