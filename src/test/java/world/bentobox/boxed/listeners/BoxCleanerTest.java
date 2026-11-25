package world.bentobox.boxed.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.bentobox.boxed.Boxed;
import world.bentobox.boxed.CommonTestSetup;
import world.bentobox.boxed.generators.chunks.BoxedBlockPopulator;
import world.bentobox.boxed.generators.template.TemplateChunk;

/**
 * Tests {@link BoxCleaner}.
 */
class BoxCleanerTest extends CommonTestSetup {

    private Boxed addon;
    private BoxCleaner cleaner;
    private BlockData stone;
    private BlockData gold;
    private BlockData air;

    @BeforeEach
    void setUpCleaner() {
        addon = mock(Boxed.class);
        when(addon.getPlugin()).thenReturn(plugin);
        when(addon.getIslands()).thenReturn(im);
        when(addon.getOverWorld()).thenReturn(world);
        when(addon.getBoxCentre(any())).thenAnswer(inv -> {
            Location l = inv.getArgument(0);
            return new Location(world, Math.floorDiv(l.getBlockX() + 320, 640) * 640, 0,
                    Math.floorDiv(l.getBlockZ() + 320, 640) * 640);
        });
        cleaner = new BoxCleaner(addon);
        stone = mock(BlockData.class);
        gold = mock(BlockData.class);
        air = mock(BlockData.class);
    }

    @Test
    void testRestorePutsBackOnlyWhatChanged() {
        // One section of world, 0 to 15. The template is all stone.
        when(world.getMinHeight()).thenReturn(0);
        when(world.getMaxHeight()).thenReturn(16);
        TemplateChunk template = mock(TemplateChunk.class);
        when(template.getBlockData(anyInt(), anyInt(), anyInt())).thenReturn(stone);
        when(template.getChests()).thenReturn(List.of());
        when(template.getEntities()).thenReturn(List.of());

        // The world has gold at 1,2,3 and air at 4,5,6
        ChunkSnapshot snapshot = mock(ChunkSnapshot.class);
        when(snapshot.getBlockData(anyInt(), anyInt(), anyInt())).thenReturn(stone);
        when(snapshot.getBlockData(1, 2, 3)).thenReturn(gold);
        when(snapshot.getBlockData(4, 5, 6)).thenReturn(air);
        Chunk chunk = mock(Chunk.class);
        when(chunk.getWorld()).thenReturn(world);
        when(chunk.getChunkSnapshot(false, false, false)).thenReturn(snapshot);
        Block goldBlock = mock(Block.class);
        Block airBlock = mock(Block.class);
        when(chunk.getBlock(1, 2, 3)).thenReturn(goldBlock);
        when(chunk.getBlock(4, 5, 6)).thenReturn(airBlock);

        // A pig and a player are in the chunk
        Pig pig = mock(Pig.class);
        Player visitor = mock(Player.class);
        when(chunk.getEntities()).thenReturn(new Entity[] { pig, visitor });

        assertEquals(2, BoxCleaner.restore(chunk, template, mock(BoxedBlockPopulator.class)));
        verify(goldBlock).setBlockData(stone, false);
        verify(airBlock).setBlockData(stone, false);
        // Nothing else was touched
        verify(chunk, never()).getBlock(0, 0, 0);
        verify(pig).remove();
        verify(visitor, never()).remove();
    }

    @Test
    void testRestoreSkipsEmptySky() {
        when(world.getMinHeight()).thenReturn(0);
        when(world.getMaxHeight()).thenReturn(16);
        TemplateChunk template = mock(TemplateChunk.class);
        when(template.isAirSection(0)).thenReturn(true);
        when(template.getChests()).thenReturn(List.of());
        when(template.getEntities()).thenReturn(List.of());
        ChunkSnapshot snapshot = mock(ChunkSnapshot.class);
        when(snapshot.isSectionEmpty(0)).thenReturn(true);
        Chunk chunk = mock(Chunk.class);
        when(chunk.getWorld()).thenReturn(world);
        when(chunk.getChunkSnapshot(false, false, false)).thenReturn(snapshot);
        when(chunk.getEntities()).thenReturn(new Entity[0]);

        assertEquals(0, BoxCleaner.restore(chunk, template, mock(BoxedBlockPopulator.class)));
        verify(snapshot, never()).getBlockData(anyInt(), anyInt(), anyInt());
        verify(chunk, never()).getBlock(anyInt(), anyInt(), anyInt());
    }

    @Test
    void testBoxInUseIsRefused() {
        when(island.isDeletable()).thenReturn(false);
        when(im.getIslandAt(any())).thenReturn(Optional.of(island));
        assertFalse(cleaner.clean(new Location(world, 10, 64, 10)));
        assertEquals(0, cleaner.getQueueSize());
    }

    @Test
    void testEmptyPositionAndDeletedBoxAreQueuedOnce() {
        when(im.getIslandAt(any())).thenReturn(Optional.empty());
        assertTrue(cleaner.clean(new Location(world, 10, 64, 10)));
        // Anywhere else in the same position is the same job
        assertTrue(cleaner.clean(new Location(world, -300, 64, 300)));
        assertEquals(1, cleaner.getQueueSize());

        when(island.isDeletable()).thenReturn(true);
        when(island.getUniqueId()).thenReturn("old-box");
        when(im.getIslandAt(any())).thenReturn(Optional.of(island));
        assertTrue(cleaner.clean(new Location(world, 700, 64, 10)));
        assertEquals(2, cleaner.getQueueSize());
        verify(island, never()).setDeletable(anyBoolean());
    }
}
