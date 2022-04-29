package world.bentobox.boxed.generators;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.io.File;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.util.noise.SimplexOctaveGenerator;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.block.Biome;

import world.bentobox.boxed.Boxed;

import org.bukkit.util.noise.SimplexNoiseGenerator;

public class BoxedChunkGeneratorV2 extends ChunkGenerator {
	private final Boxed addon;
	private final YamlConfiguration config;
	public final AbstractBoxedBiomeGeneratorV2 biomeGenerator;

	public BoxedChunkGeneratorV2(Boxed addon, AbstractBoxedBiomeGeneratorV2 biomeGenerator) {
		this.addon = addon;
		this.biomeGenerator = biomeGenerator;

		File biomeFile = new File(addon.getDataFolder(), "biomes.yml");
		if (!biomeFile.exists()) {
			addon.saveResource("biomes.yml", true);
		}
		config = YamlConfiguration.loadConfiguration(biomeFile);
	}
	
	/**
	 * NOTE: This method is by no means optimized. Very often, you shouldn't instantiate 
	 * your noise generator all the time, as it runs math operations in the background.
	 * However, this is a world generation tutorial and I want to keep things simple.
	 * There are a lot of different steps that you as an individual developer need to 
	 * optimize based on what it is that you're generating. 
	 * 
	 * Most worldgen plugins don't even use SimplexOctaveGenerator.
	 * @param info
	 * @param rawX
	 * @param rawZ
	 * @return
	 */
	private int getHeightFor(WorldInfo info, int rawX, int rawZ) {
		//The shape of the world is created with "noise maps". There is an inbuilt noise map generator
		//within the bukkit API that the old tutorial also uses:
		SimplexOctaveGenerator generator = new SimplexOctaveGenerator(info.getSeed(), 8);
		// SimplexNoiseGenerator noise = new SimplexNoiseGenerator(info.getSeed());

		// info.get
		// generator.noise
		int dist = addon.getSettings().getIslandDistance();
		Biome biome = biomeGenerator.getZoomedOutBiome(rawX, rawZ);
		
		double noiseScaleHorizontal = config.getDouble("biomes." + biome.name() + ".scale", 10D);
		generator.setScale(noiseScaleHorizontal);

		double x = ((((double)rawX*4) % dist) / 4);
		double z = ((((double)rawZ*4) % dist) / 4);

		// return (int) (generator.noise(x, z, 0.002D, 1D)*5D + 114D);

		// return (int) noise.noise(x, z);
		//I, for one, don't know what this number does, only that the bukkit tutorial set it way too low
		//and made the terrain excessively smooth.
		// generator.setScale(0.03D);
		
		return (int) (generator.noise(x, z, 
				0D,
				0D) + 63D);
	}
	
	/**
	 * This is where we will set the shape of the world. 
	 */
	// @Override
	// public void generateNoise(WorldInfo info, Random random, int chunkX, int chunkZ, ChunkData chunkAccess)
	// {
	// 	//Iterate through the chunk and set the stone
	// 	for(int x = 0; x < 16; x++)
	// 		for(int z = 0; z < 16; z++)
	// 		{
	// 			int rawX = chunkX*16 + x;
	// 			int rawZ = chunkZ*16 + z;

	// 			Biome biome = biomeGenerator.getZoomedOutBiome(rawX, rawZ);
	// 			double height = config.getDouble("biomes." + biome.name() + ".height", 8D);
	// 			int currentHeight = getHeightFor(info, rawX, rawZ);
	// 			for(int y = 63; y <= info.getMaxHeight(); y++) {
	// 				double heightOffset = height - y;

	// 				//be very sure that x and z is in the 0-15 range. 
	// 				//DO NOT pass rawX and rawZ here, it will cause a crash.
	// 				Material materialToSet = currentHeight + heightOffset >= 0
	// 					? Material.STONE
	// 					: Material.AIR;
	// 				chunkAccess.setBlock(x, y, z, materialToSet);
	// 			}
	// 		}
	// }
	
	/**
	 * This is where we put grass and dirt
	 */
	// @Override
	// public void generateSurface(WorldInfo info, Random random, int chunkX, int chunkZ, ChunkData chunkAccess)
	// {
	// 	//Iterate through the chunk and set grass blocks
	// 	for(int x = 0; x < 16; x++)
	// 		for(int z = 0; z < 16; z++)
	// 		{
	// 			int rawX = chunkX*16 + x;
	// 			int rawZ = chunkZ*16 + z;
	// 			int currentHeight = getHeightFor(info, rawX, rawZ);
	// 			// chunkAccess.setBlock(x, currentHeight, z, Material.AIR);
				
	// 			for(int diff = currentHeight - 63; diff > 0; diff--) {
	// 				chunkAccess.setBlock(x, currentHeight-diff, z, Material.AIR);
	// 			}
	// 		}
	// }

	//Just set bedrock. Simple and direct.
	// @Override
	// public void generateBedrock(WorldInfo info, Random random, int chunkX, int chunkZ, ChunkData chunkAccess)
	// {
  //       for(int x = 0; x < 16; x++)
  //       	for(int z = 0; z < 16; z++)
	// 		{
	// 			chunkAccess.setBlock(x, info.getMinHeight(), z, Material.BEDROCK);
				
	// 			//Sometimes bedrock appears a little higher than minY.
	// 			if(random.nextBoolean())
	// 				chunkAccess.setBlock(x, info.getMinHeight()+1, z, Material.BEDROCK);
	// 		}
	// }

	// @Override
	// public void generateCaves(WorldInfo info, Random random, int chunkX, int chunkZ, ChunkData chunkAccess)
	// {
	// 	//There are many different cave algorithms. For this, almost any dev will
	// 	//code their own thing. For now, I will leave this empty for simplicity's sake.
		
	// 	//Additionally, note that vanilla's 1.18 caves are generated in generateNoise,
	// 	//while 1.17 and below caves (stuff like ravines) are generated
	// 	//here
	// }
	
	// Register your populator
	// @Override
  //   public List<BlockPopulator> getDefaultPopulators(World world) {
  //       return new ArrayList<BlockPopulator>() {{
  //       	add(new BoxedBlockPopulator());
  //       }};
  //   }
	
	
	//For the purpose of this tutorial, we will suppress vanilla completely.
	//This may not always be what you want (i.e. my world generation plugin, TerraformGenerator,
	//will allow vanilla caves to generate, so I set shouldGenerateNoise and shouldGenerateCaves
	//to true.
	
	//On your own, you can toggle these to see what happens. Refer to the API as well.
	@Override
	public boolean shouldGenerateNoise() {
		return true;
	}

	@Override
	public boolean shouldGenerateSurface() {
		return true;
	}

	@Override
	public boolean shouldGenerateBedrock() {
		return true;
	}

	@Override
	public boolean shouldGenerateCaves() {
		return true;
	}

	@Override
	public boolean shouldGenerateDecorations() {
		return true;
	}

	@Override
	public boolean shouldGenerateMobs() {
		return false;
	}

	@Override
	public boolean shouldGenerateStructures() {
		return false;
	}
	
}
