package fr.zetamap.playerfollow;

import mindustry.gen.Player;
import fr.zetamap.playerfollow.api.FollowMode;
import fr.zetamap.playerfollow.modes.*;


/** Default modes */
public class FollowModes {
  public static final FollowMode<Player>
    arc = FollowMode.add("arc", ArcFollow.class, ArcFollow::new),
    joint = FollowMode.add("joint", JointFollow.class, JointFollow::new),
    snake = FollowMode.add("snake", SnakeFollow.class, SnakeFollow::new),
    orbit = FollowMode.add("orbit", OrbitFollow.class, OrbitFollow::new);
}
