/* Copyright (c) 2013-2021 Jesper Öqvist <jesper@llbit.se>
 * Copyright (c) 2013-2021 Chunky contributors
 *
 * This file is part of Chunky.
 *
 * Chunky is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Chunky is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with Chunky.  If not, see <http://www.gnu.org/licenses/>.
 */
package se.llbit.chunky.renderer.scene;

import se.llbit.chunky.block.Air;
import se.llbit.chunky.block.Water;
import se.llbit.chunky.renderer.WorkerState;
import se.llbit.math.Ray;
import se.llbit.math.Vector3;

/**
 * Modified PreviewRayTracer from upstream Chunky
 * Original Author: Jesper Öqvist <jesper@llbit.se>
 */
public class CustomPreviewRayTracer implements RayTracer {

  /**
   * Do a quick preview ray tracing for the current ray.
   */
  @Override public void trace(Scene scene, WorkerState state) {
    Ray ray = state.ray;
    if (scene.isInWater(ray)) {
      ray.setCurrentMaterial(Water.INSTANCE);
    } else {
      ray.setCurrentMaterial(Air.INSTANCE);
    }
    while (true) {
      if (!nextIntersection(scene, ray)) {
        break;
      } else if (ray.getCurrentMaterial() != Air.INSTANCE && ray.color.w > 0) {
        break;
      } else {
        ray.o.scaleAdd(Ray.OFFSET, ray.d);
      }
    }

    if (ray.getCurrentMaterial() == Air.INSTANCE) {
      scene.sky.getSkySpecularColor(ray);
    } else {
      scene.sun.flatShading(ray);
    }
  }

  /**
   * Find next ray intersection.
   * @return Next intersection
   */
  public static boolean nextIntersection(Scene scene, Ray ray) {
    ray.setPrevMaterial(ray.getCurrentMaterial(), ray.getCurrentData());
    ray.t = Double.POSITIVE_INFINITY;
    boolean hit = false;
    if (scene.sky().cloudsEnabled()) {
      hit = scene.sky().cloudIntersection(scene, ray);
    }
    if (scene.isWaterPlaneEnabled()) {
      hit = waterPlaneIntersection(scene, ray) || hit;
    }
    if (scene.intersect(ray)) {
      // Octree tracer handles updating distance.
      return true;
    }
    if (hit) {
      ray.distance += ray.t;
      ray.o.scaleAdd(ray.t, ray.d);
      scene.updateOpacity(ray);
      return true;
    } else {
      ray.setCurrentMaterial(Air.INSTANCE);
      return false;
    }
  }

  private static boolean waterPlaneIntersection(Scene scene, Ray ray) {
    double t = (scene.getEffectiveWaterPlaneHeight() - ray.o.y - scene.origin.y) / ray.d.y;
    if (scene.getWaterPlaneChunkClip()) {
      Vector3 pos = new Vector3(ray.o);
      pos.scaleAdd(t, ray.d);
      if (scene.isChunkLoaded((int)Math.floor(pos.x), (int)Math.floor(pos.z)))
        return false;
    }
    if (ray.d.y < 0) {
      if (t > 0 && t < ray.t) {
        ray.t = t;
        Water.INSTANCE.getColor(ray);
        ray.setNormal(0, 1, 0);
        ray.setCurrentMaterial(scene.getPalette().water);
        return true;
      }
    }
    if (ray.d.y > 0) {
      if (t > 0 && t < ray.t) {
        ray.t = t;
        Water.INSTANCE.getColor(ray);
        ray.setNormal(0, -1, 0);
        ray.setCurrentMaterial(Air.INSTANCE);
        return true;
      }
    }
    return false;
  }

}