package fr.zetamap.playerfollow.api;

import arc.func.Cons2;
import arc.math.geom.Position;
import arc.math.geom.Vec2;
import arc.struct.Seq;


public interface Follow<T extends Position> {
  T followed();
  Seq<T> followers();
  
  boolean add(T follower);
  boolean remove(T follower);
  boolean contains(T follower);
  void clear();
  
  boolean shouldRemove();
  
  default void update() { update(null); }
  void update(Cons2<T, Vec2> notifer);
}
