import arc.*;
import arc.mock.*;
import arc.util.*;
import mindustry.*;
import mindustry.ai.*;
import mindustry.content.*;
import mindustry.core.*;
import mindustry.entities.*;
import mindustry.entities.bullet.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.net.*;
import mindustry.type.*;
import mindustry.world.blocks.defense.turrets.*;
import org.junit.jupiter.api.*;

import static mindustry.Vars.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * An arc's range only exists if its bolt walks that far: Lightning advances node by node and damages
 * whatever the nodes land on, so raising Turret.range alone leaves the bolt dying a few tiles out.
 */
public class ArcBoltTests{

    @BeforeAll
    static void initWorld(){
        headless = true;
        Core.app = new MockApplication();
        Core.settings = new MockSettings();
        Core.audio = new MockAudio();
        Core.files = new MockFiles();
        Groups.init();
        net = new Net(null);
        Vars.content = new ContentLoader();
        Vars.state = new GameState();
        Vars.tree = new FileTree();
        //normally wired up by Vars.init(), which needs assets
        Vars.collisions = new EntityCollisions();
        Vars.indexer = new BlockIndexer();
        content.createBaseContent();
        Vars.world = new World();
        //500 tile bolts need room to travel
        world.resize(800, 800);
        Log.useColors = false;
        Time.setDeltaProvider(() -> 1f);
    }

    @AfterEach
    void clearEntities(){
        Groups.clear();
    }

    @Test
    void arcRangeIs500Tiles(){
        assertEquals(500f * tilesize, ((Turret)Blocks.arc).range, 0.01f, "arc range is not 500 tiles");
    }

    @Test
    void arcBoltHitsUnitAtItsMaxRange(){
        Unit target = shoot(((PowerTurret)Blocks.arc).shootType, ((Turret)Blocks.arc).range);

        assertTrue(target.health < target.maxHealth(),
            "the arc's bolt never reached a unit at its stated range of " + ((Turret)Blocks.arc).range + "px");
    }

    @Test
    void arcBoltStillHitsCloseTargets(){
        Unit target = shoot(((PowerTurret)Blocks.arc).shootType, 80f);

        assertTrue(target.health < target.maxHealth(), "the bolt stopped hitting targets it used to reach");
    }

    @Test
    void preModBoltGeometryDoesNotReach500Tiles(){
        //length 25 at the default step is what the arc shipped with; it reaches ~160px and nothing more
        BulletType vanilla = new LightningBulletType(){{
            damage = 12f;
            collidesAir = false;
        }};

        Unit target = shoot(vanilla, ((Turret)Blocks.arc).range);

        assertEquals(target.maxHealth(), target.health, "a default-length bolt should not reach 500 tiles");
    }

    /** Fires a bolt along +X from a fixed point at a dagger parked {@code dist} away, then ticks the chain. */
    private Unit shoot(BulletType type, float dist){
        float x = 300f, y = 300f;

        Unit target = UnitTypes.dagger.create(Team.crux);
        target.set(x + dist, y);
        target.add();

        //the per-team trees are normally filled by Logic each tick, and Lightning consults them
        //while the chain is being built, so they have to be populated before the bolt fires
        state.teams.updateTeamStats();

        type.create(null, Team.sharded, x, y, 0f, type.damage, 1f, 1f, null);

        for(int i = 0; i < 30; i++){
            Groups.unit.updatePhysics();
            Groups.bullet.updatePhysics();
            Groups.bullet.update();
            Groups.bullet.collide();
        }

        return target;
    }
}
