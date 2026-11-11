package mindustry.entities;

import arc.graphics.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.core.*;
import mindustry.entities.bullet.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.world.*;

import static mindustry.Vars.*;

public class Lightning{
    private static final Rand random = new Rand();
    private static final Rect rect = new Rect();
    private static final Seq<Unit> entities = new Seq<>();
    private static final IntSet hit = new IntSet();
    private static final int maxChain = 8;
    private static final float hitRange = 30f;
    /** Distance a bolt covers per step, unless it asks for a longer one. */
    private static final float defaultStep = hitRange / 2f;
    /**
     * Long bolts are held within this many degrees of their starting angle. Without it the per-step
     * jitter random-walks the bolt off target: over 100 steps it ends up ~1900px downrange instead of
     * 4000 and can finish behind the shooter. Measured against a lone target 500 tiles out, a 25 degree
     * hold hits 3.5% of shots, 6 degrees 25%, 2 degrees 86%, 1 degree 99.9% (with 1% spare reach).
     */
    private static final float maxAimDrift = 1f;
    private static boolean bhit = false;
    private static int lastSeed = 0;

    /** Create a lighting branch at a location. Use Team.derelict to damage everyone. */
    public static void create(BulletType bulletCreated, Team team, Color color, float damage, float x, float y, float targetAngle, int length){
        createLightningInternal(null, bulletCreated, lastSeed++, team, color, damage, x, y, targetAngle, length, -1f);
    }

    /** Create a lighting branch at a location. Use Team.derelict to damage everyone. */
    public static void create(Team team, Color color, float damage, float x, float y, float targetAngle, int length){
        createLightningInternal(null, Bullets.damageLightning, lastSeed++, team, color, damage, x, y, targetAngle, length, -1f);
    }

    /** Create a lighting branch at a location. Uses bullet parameters. */
    public static void create(Bullet bullet, Color color, float damage, float x, float y, float targetAngle, int length){
        create(bullet, color, damage, x, y, targetAngle, length, -1f);
    }

    /** Create a lighting branch that covers {@code stepLength} pixels per step, for bolts meant to reach past the default range. */
    public static void create(Bullet bullet, Color color, float damage, float x, float y, float targetAngle, int length, float stepLength){
        createLightningInternal(bullet, bullet == null || bullet.type.lightningType == null ? Bullets.damageLightning : bullet.type.lightningType, lastSeed++, bullet.team, color, damage, x, y, targetAngle, length, stepLength);
    }

    private static void createLightningInternal(@Nullable Bullet hitter, BulletType hitCreate, int seed, Team team, Color color, float damage, float x, float y, float rotation, int length, float stepLength){
        random.setSeed(seed);
        hit.clear();

        float step = stepLength > 0f ? stepLength : defaultStep;
        boolean longBolt = step > defaultStep;
        float aim = rotation, originX = x, originY = y;

        Seq<Vec2> lines = new Seq<>();
        bhit = false;

        for(int i = 0; i < length / 2; i++){
            hitCreate.create(hitter == null ? null : hitter.owner, team, x, y,
                rotation, damage * (hitter == null ? 1f : hitter.damageMultiplier()), 1f, 1f, null);
            lines.add(new Vec2(x + Mathf.range(3f), y + Mathf.range(3f)));

            if(lines.size > 1){
                bhit = false;
                Vec2 from = lines.get(lines.size - 2);
                Vec2 to = lines.get(lines.size - 1);
                World.raycastEach(World.toTile(from.getX()), World.toTile(from.getY()), World.toTile(to.getX()), World.toTile(to.getY()), (wx, wy) -> {

                    Tile tile = world.tile(wx, wy);
                    if(tile != null && (tile.build != null && tile.build.isInsulated()) && tile.team() != team){
                        bhit = true;
                        //snap it instead of removing
                        lines.get(lines.size - 1).set(wx * tilesize, wy * tilesize);
                        return true;
                    }
                    return false;
                });
                if(bhit) break;
            }

            //scan box is at least as wide as one step, or the steps leave unscanned gaps along a long bolt
            rect.setSize(Math.max(hitRange, step)).setCenter(x, y);
            entities.clear();
            if(hit.size < maxChain){
                Units.nearbyEnemies(team, rect, u -> {
                    if(!hit.contains(u.id()) && (hitter == null || u.checkTarget(hitter.type.collidesAir, hitter.type.collidesGround))){
                        entities.add(u);
                    }
                });
            }

            if(hitter != null && hitter.type.pierceCap > 0 && hit.size >= hitter.type.pierceCap){
                break;
            }

            Unit furthest = Geometry.findFurthest(x, y, entities);

            if(furthest != null && furthest.within(x, y, hitRange * 2f)){
                hit.add(furthest.id());
                x = furthest.x();
                y = furthest.y();
            }else{
                rotation += random.range(20f);
                if(longBolt){
                    rotation = aim + Mathf.clamp(rotation - aim, -maxAimDrift, maxAimDrift);
                }
                x += Angles.trnsx(rotation, step);
                y += Angles.trnsy(rotation, step);
            }
        }

        if(longBolt){
            //Effect.create() only renders when the camera overlaps a clip-sized box on the effect position.
            //A bolt long enough to leave the screen would never draw when fired at from the turret, so anchor it there.
            Fx.lightning.at(originX, originY, rotation, color, lines);
        }else{
            Fx.lightning.at(x, y, rotation, color, lines);
        }
    }
}