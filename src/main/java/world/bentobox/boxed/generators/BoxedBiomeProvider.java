package world.bentobox.boxed.generators;

import java.util.List;

import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;


public class BoxedBiomeProvider extends BiomeProvider {
	private final AbstractBoxedBiomeGeneratorV2 biomeGenerator;

	public BoxedBiomeProvider(AbstractBoxedBiomeGeneratorV2 biomeGenerator) {
		this.biomeGenerator = biomeGenerator;
}

	@Override
	public Biome getBiome(WorldInfo info, int x, int y, int z) {
		return biomeGenerator.getZoomedOutBiome(x, z);
		// return Biome.OCEAN;
	}

	@Override
	public List<Biome> getBiomes(WorldInfo info) {
		return biomeGenerator.BiomeList;
	}
}
