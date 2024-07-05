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

package player_follow;

import java.util.concurrent.locks.ReentrantLock;

import arc.math.geom.Vec2;
import arc.struct.Seq;
import mindustry.gen.Player;


public abstract class Follower {
  public static final java.util.concurrent.ExecutorService exec = arc.util.Threads.unboundedExecutor("PlayerFollow", 1);
  public static final Seq<Follower> follows = new Seq<>();
  public static int loopSleep = 10; //ms

  public Seq<Player> following = new Seq<>();
  public volatile boolean taskRunning = true;
  
  public final Player followed;
  public final java.util.concurrent.Future<?> task;
  public final ReentrantLock lock = new ReentrantLock();
  
  public Follower(Player player) {
    followed = player;
    task = exec.submit(() -> {
      try {
        Player follower;
        Vec2 dest;
        
        while (taskRunning) {
          try { Thread.sleep(loopSleep); } 
          catch (InterruptedException e) { break; }
          
          lock.lock();
          
          playerFollowPreProcessing();
          
          for (int i=0; i<following.size; i++) {
            if (!taskRunning) break;
            
            follower = following.get(i);
            // if is null, probably leaved the game
            if (follower == null) continue;
            // if followed player leaved, break the loop
            if (followed == null) {
              taskRunning = false;
              break;
            }
            
            dest = computePlayerFollow(i, follower);
            follower.unit().set(dest);
            follower.set(dest);
            mindustry.gen.Call.setPosition(follower.con, dest.x, dest.y);              
          }
          
          lock.unlock();
        }          
      } finally {
        if (lock.isLocked()) lock.unlock();
      }
    });
  }
  
  public synchronized boolean stop() {
    lock.lock();
    taskRunning = false;
    lock.unlock();
    
    // Wait a little
    try { task.get(500, java.util.concurrent.TimeUnit.MILLISECONDS); } 
    catch (Exception ex) {}
    
    if (!task.isDone()) {
      lock.lock();
      task.cancel(true);
      lock.unlock();
      
      // Wait a little
      try { task.get(200, java.util.concurrent.TimeUnit.MILLISECONDS); } 
      catch (Exception ex) {}
    }
   
    return task.isDone();
  }
  
  public void addFollower(Player player) {
    following.add(player);
  }
  
  public boolean removeFollower(Player player) {
    return following.remove(player);
  }
  
  public boolean removeFollower(arc.func.Boolf<Player> pred) {
    for(int i = 0; i < following.size; i++){
      if(pred.get(following.get(i)))
        return removeFollower(following.get(i));
    }
    return false;
  }
  
  // Can be overridden to do things before computing followers positions
  public void playerFollowPreProcessing() {}

  public abstract Vec2 computePlayerFollow(int index, Player player);
}
