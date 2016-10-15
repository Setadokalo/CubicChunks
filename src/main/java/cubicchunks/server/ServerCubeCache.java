/*
 *  This file is part of Cubic Chunks Mod, licensed under the MIT License (MIT).
 *
 *  Copyright (c) 2015 contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package cubicchunks.server;

import cubicchunks.CubicChunks;
import cubicchunks.server.chunkio.CubeIO;
import cubicchunks.server.chunkio.async.forge.AsyncWorldIOExecutor;
import cubicchunks.util.CubeCoords;
import cubicchunks.util.XYZMap;
import cubicchunks.world.ICubeCache;
import cubicchunks.world.ICubicWorldServer;
import cubicchunks.world.IProviderExtras;
import cubicchunks.world.column.Column;
import cubicchunks.world.cube.Cube;
import cubicchunks.worldgen.generator.ICubeGenerator;
import cubicchunks.worldgen.generator.ICubePrimer;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import org.apache.logging.log4j.Logger;

import javax.annotation.Detainted;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

/**
 * This is CubicChunks equivalent of ChunkProviderServer, it loads and unloads Cubes and Columns.
 * <p>
 * There are a few necessary changes to the way vanilla methods work:
 * * Because loading a Chunk (Column) doesn't make much sense with CubicChunks,
 * all methods that load Chunks, actually load  an empry column with no blocks in it
 * (there may be some entities that are not in any Cube yet).
 * * dropChunk method is not supported. Columns are unloaded automatically when the last cube is unloaded
 */
public class ServerCubeCache extends ChunkProviderServer implements ICubeCache, IProviderExtras{

	private static final Logger log = CubicChunks.LOGGER;

	private ICubicWorldServer worldServer;
	private CubeIO cubeIO;

	// TODO: Use a better hash map!
	private XYZMap<Cube> cubeMap = new XYZMap<>(0.7f, 8000);

	private ICubeGenerator   cubeGen;

	public ServerCubeCache(ICubicWorldServer worldServer, ICubeGenerator cubeGen) {
		super((WorldServer) worldServer,
				worldServer.getSaveHandler().getChunkLoader(worldServer.getProvider()), // forge uses this in
				null); // safe to null out IChunkGenerator (Note: lets hope mods don't touch it, ik its public)

		this.cubeGen   = cubeGen;

		this.worldServer = worldServer;
		this.cubeIO = new CubeIO(worldServer);
	}

	@Override
	@Detainted
	public void unload(Chunk chunk) {
		//ignore, ChunkGc unloads cubes
	}

	@Override
	@Detainted
	public void unloadAllChunks() {
		//ignore, ChunkGc unloads cubes
	}

	/**
	 * Vanilla method, returns a Chunk (Column) only of it's already loaded.
	 */
	@Override
	@Nullable
	public Column getLoadedChunk(int columnX, int columnZ) {
		return (Column) this.id2ChunkMap.get(ChunkPos.asLong(columnX, columnZ));
	}

	/**
	 * Loads Chunk (Column) if it can be loaded from disk, or returns already loaded one.
	 * Doesn't generate new Columns.
	 */
	@Override
	@Nullable
	public Column loadChunk(int columnX, int columnZ) {
		return this.loadChunk(columnX, columnZ, null);
	}

	/**
	 * Load chunk asynchronously. Currently CubicChunks only loads synchronously.
	 */
	@Override
	@Nullable
	public Column loadChunk(int columnX, int columnZ, Runnable runnable) {
		// TODO: Set this to LOAD when PlayerCubeMap works
		if (runnable == null) {
			return getColumn(columnX, columnZ, /*Requirement.LOAD*/Requirement.LIGHT);
		}

		// TODO here too
		asyncGetColumn(columnX, columnZ, Requirement.LIGHT, col -> runnable.run());
		return null;
	}

	/**
	 * If this Column is already loaded - returns it.
	 * Loads from disk if possible, otherwise generates new Column.
	 */
	@Override
	public Column provideChunk(int cubeX, int cubeZ) {
		return getColumn(cubeX, cubeZ, Requirement.GENERATE);
	}

	@Override
	public boolean saveChunks(boolean alwaysTrue) {
		for(Cube cube : cubeMap){ // save cubes
			if (cube.needsSaving()) {
				this.cubeIO.saveCube(cube);
			}
		}
		for(Chunk chunk : id2ChunkMap.values()){ // save columns
			Column column = (Column)chunk;
			// save the column
			if (column.needsSaving(alwaysTrue)) {
				this.cubeIO.saveColumn(column);
			}
		}

		return true;
	}

	@Override
	public boolean unloadQueuedChunks() {
		// NOTE: the return value is completely ignored
		// NO-OP, This is called by WorldServer's tick() method every tick
		return false;
	}

	@Override
	public String makeString() {
		return "ServerCubeCache: " + this.id2ChunkMap.size() + " columns, "
				+ this.cubeMap.getSize() + " cubes";
	}

	@Override
	public List<Biome.SpawnListEntry> getPossibleCreatures(@Nonnull final EnumCreatureType type, @Nonnull final BlockPos pos) {
		return cubeGen.getPossibleCreatures(type, pos);
	}

	@Nullable
	public BlockPos getStrongholdGen(@Nonnull World worldIn, @Nonnull String name, @Nonnull BlockPos pos) {
		return cubeGen.getClosestStructure(name, pos);
	}

	// getLoadedChunkCount() in ChunkProviderServer is fine - MC: 1.10.2 Forge: 12.18.1.209

	@Override
	public boolean chunkExists(int cubeX, int cubeZ) {
		return this.id2ChunkMap.get(ChunkPos.asLong(cubeX, cubeZ)) != null;
	}

	//==============================
	//=====CubicChunks methods======
	//==============================

	@Override
	public Cube getCube(int cubeX, int cubeY, int cubeZ) {
		return getCube(cubeX, cubeY, cubeZ, Requirement.GENERATE);
	}

	@Override
	public Cube getCube(CubeCoords coords) {
		return getCube(coords.getCubeX(), coords.getCubeY(), coords.getCubeZ());
	}

	@Override
	public Cube getLoadedCube(int cubeX, int cubeY, int cubeZ) {
		return cubeMap.get(cubeX, cubeY, cubeZ);
	}

	@Override
	public Cube getLoadedCube(CubeCoords coords) {
		return getLoadedCube(coords.getCubeX(), coords.getCubeY(), coords.getCubeZ());
	}

	/**
	 * Load a cube, asynchronously. The work done to retrieve the column is specified by the
	 * {@link Requirement} <code>req</code>
	 *
	 * @param cubeX Cube x position
	 * @param cubeY Cube y position
	 * @param cubeZ Cube z position
	 * @param req Work done to retrieve the column
	 * @param callback Callback to be called when the load finishes. Note that <code>null</code> can be passed to
	 * the callback if the work specified by <code>req</code> is not sufficient to provide a cube
	 *
	 * @see #getCube(int, int, int, Requirement) for the synchronous equivalent to this method
	 */
	public void asyncGetCube(int cubeX, int cubeY, int cubeZ, @Nonnull Requirement req, @Nonnull Consumer<Cube> callback) {
		Cube cube = getLoadedCube(cubeX, cubeY, cubeZ);
		if (req == Requirement.LOAD_CACHED || (cube != null && req.compareTo(Requirement.GENERATE) <= 0)) {
			callback.accept(cube);
			return;
		}

		if (cube == null) {
			asyncGetColumn(cubeX, cubeZ, req, col -> {
				if (col == null) {
					callback.accept(null);
					return;
				}
				AsyncWorldIOExecutor.queueCubeLoad(worldObj, cubeIO, col, cubeY, loaded -> {
					onCubeLoaded(loaded, col);
					loaded = postCubeLoadAttempt(cubeX, cubeY, cubeZ, loaded, col, req);
					callback.accept(loaded);
				});
			});
		}
	}

	@Override
	@Nullable
	public Cube getCube(int cubeX, int cubeY, int cubeZ, @Nonnull Requirement req) {

		Cube cube = getLoadedCube(cubeX, cubeY, cubeZ);
		if(req == Requirement.LOAD_CACHED ||
				(cube != null && req.compareTo(Requirement.GENERATE) <= 0)) {
			return cube;
		}

		// try to get the Column
		Column column = getColumn(cubeX, cubeZ, req);
		if(column == null) {
			return cube; // Column did not reach req, so Cube also does not
		}

		if(cube == null) {
			cube = AsyncWorldIOExecutor.syncCubeLoad(worldObj, cubeIO, cubeY, column);
			onCubeLoaded(cube, column);
		}

		return postCubeLoadAttempt(cubeX, cubeY, cubeZ, cube, column, req);
	}

	/**
	 * After successfully loading a cube, add it to it's column and the lookup table
	 *
	 * @param cube The cube that was loaded
	 * @param column The column of the cube
	 */
	private void onCubeLoaded(@Nullable Cube cube, @Nonnull Column column) {
		if(cube != null) {
			column.addCube(cube);
			cubeMap.put(cube); // cache the Cube
			cube.onLoad();             // init the Cube
		}
	}

	/**
	 * Process a recently loaded cube as per the specified effort level.
	 *
	 * @param cubeX Cube x position
	 * @param cubeY Cube y position
	 * @param cubeZ Cube z positon
	 * @param cube The loaded cube, if loaded, else <code>null</code>
	 * @param column The column of the cube
	 * @param req Work done on the cube
	 *
	 * @return The processed cube, or <code>null</code> if the effort level is not sufficient to provide a cube
	 */
	@Nullable
	private Cube postCubeLoadAttempt(int cubeX, int cubeY, int cubeZ, @Nullable Cube cube, @Nonnull Column column, @Nonnull Requirement req) {
		// Fast path - Nothing to do here
		if (req == Requirement.LOAD) return cube;
		if (req == Requirement.GENERATE && cube != null) return cube;

		if(cube == null) {
			// generate the Cube
			cube = generateCube(cubeX, cubeY, cubeZ, column);
			if(req == Requirement.GENERATE) {
				return cube;
			}
		}

		if(!cube.isFullyPopulated()) {
			// forced full population of this cube
			populateCube(cube);
			if (req == Requirement.POPULATE) {
				return cube;
			}
		}

		//TODO: Direct skylight might have changed and even Cubes that have there
		//      initial light done, there might be work to do for a cube that just loaded
		if(!cube.isInitialLightingDone()) {
			calculateDiffuseSkylight(cube);
		}

		return cube;
	}


	/**
	 * Generate a cube at the specified position
	 *
	 * @param cubeX Cube x position
	 * @param cubeY Cube y position
	 * @param cubeZ Cube z position
	 * @param column Column of the cube
	 *
	 * @return The generated cube
	 */
	@Nonnull
	private Cube generateCube(int cubeX, int cubeY, int cubeZ, @Nonnull Column column) {
		ICubePrimer primer = cubeGen.generateCube(cubeX, cubeY, cubeZ);
		Cube cube = new Cube(column, cubeY, primer);

		this.worldServer.getFirstLightProcessor().initializeSkylight(cube); // init sky light, (does not require any other cubes, just OpacityIndex)
		onCubeLoaded(cube, column);
		return cube;
	}

	/**
	 * Populate a cube at the specified position, generating surrounding cubes as necessary
	 *
	 * @param cube The cube to populate
	 */
	private void populateCube(@Nonnull Cube cube) {
		int cubeX = cube.getX();
		int cubeY = cube.getY();
		int cubeZ = cube.getZ();

		cubeGen.getPopulationRequirement(cube).forEachPoint((x, y, z) -> {
			Cube popcube = getCube(x + cubeX, y + cubeY, z + cubeZ);
			if(!popcube.isPopulated()) {
				cubeGen.populate(popcube);
				popcube.setPopulated(true);
			}
		});
		cube.setFullyPopulated(true);
	}

	/**
	 * Initialize skylight for the cube at the specified position, generating surrounding cubes as needed.
	 *
	 * @param cube The cube to light up
	 */
	private void calculateDiffuseSkylight(@Nonnull Cube cube) {
		int cubeX = cube.getX();
		int cubeY = cube.getY();
		int cubeZ = cube.getZ();

		for(int x = -2; x <= 2; x++) {
			for(int z = -2;z <= 2;z++) {
				for(int y = 2;y >= -2;y--) {
					if(x != 0 || y != 0 || z != 0) {
						getCube(x +  cubeX, y + cubeY, z + cubeZ);
					}
				}
			}
		}
		this.worldServer.getFirstLightProcessor().diffuseSkylight(cube);
	}


	/**
	 * Retrieve a column, asynchronously. The work done to retrieve the column is specified by the
	 * {@link Requirement} <code>req</code>
	 * @param columnX Column x position
	 * @param columnZ Column z position
	 * @param req Work done to retrieve the column
	 * @param callback Callback to be called when the column has finished loading. Note that the returned column
	 * is not guaranteed to be non-null
	 *
	 * @see ServerCubeCache#getColumn(int, int, Requirement) for the synchronous variant of this method
	 */
	public void asyncGetColumn(int columnX, int columnZ, Requirement req, Consumer<Column> callback) {
		Column column = getLoadedChunk(columnX, columnZ);
		if (column != null || req == Requirement.LOAD_CACHED) {
			callback.accept(column);
			return;
		}

		AsyncWorldIOExecutor.queueColumnLoad(worldObj, cubeIO, columnX, columnZ, col -> {
			col = postProcessColumn(columnX, columnZ, col, req);
			callback.accept(col);
		});
	}


	@Override
	@Nullable
	public Column getColumn(int columnX, int columnZ, Requirement req) {
		Column column = getLoadedChunk(columnX, columnZ);
		if(column != null || req == Requirement.LOAD_CACHED) {
			return column;
		}

		column = AsyncWorldIOExecutor.syncColumnLoad(worldObj, cubeIO, columnX, columnZ);
		column = postProcessColumn(columnX, columnZ, column, req);

		return column;
	}

	/**
	 * After loading a column, do work on it, where the work required is specified by <code>req</code>
	 * @param columnX X position of the column
	 * @param columnZ Z position of the column
	 * @param column The loaded column, or <code>null</code> if the column couldn't be loaded
	 * @param req The amount of work to be done on the cube
	 * @return The postprocessed column, or <code>null</code>
	 */
	@Nullable
	private Column postProcessColumn(int columnX, int columnZ, Column column, Requirement req) {
		if(column != null) {
			id2ChunkMap.put(ChunkPos.asLong(columnX, columnZ), column);
			column.setLastSaveTime(this.worldServer.getTotalWorldTime()); // the column was just loaded
			column.onChunkLoad();
			return column;
		}else if(req == Requirement.LOAD) {
			return null;
		}

		column = new Column(this, worldServer, columnX, columnZ);
		cubeGen.generateColumn(column);

		id2ChunkMap.put(ChunkPos.asLong(columnX, columnZ), column);
		column.setLastSaveTime(this.worldServer.getTotalWorldTime()); // the column was just generated
		column.onChunkLoad();
		return column;
	}

	public String dumpLoadedCubes() {
		StringBuilder sb = new StringBuilder(10000).append("\n");
		for (Chunk chunk : this.id2ChunkMap.values()) {
			Column column = (Column)chunk;
			if (column == null) {
				sb.append("column = null\n");
				continue;
			}
			sb.append("Column[").append(column.getX()).append(", ").append(column.getZ()).append("] {");
			boolean isFirst = true;
			for (Cube cube : column.getLoadedCubes()) {
				if (!isFirst) {
					sb.append(", ");
				}
				isFirst = false;
				if (cube == null) {
					sb.append("cube = null");
					continue;
				}
				sb.append("Cube[").append(cube.getY()).append("]");
			}
			sb.append("\n");
		}
		return sb.toString();
	}

	public void flush() {
		this.cubeIO.flush();
	}

	void takeOutGarbage(){

		Iterator<Cube> cubeIt = cubeMap.iterator();
		while(cubeIt.hasNext()) {
			if(tryUnloadCube(cubeIt.next())) {
				cubeIt.remove();
			}
		}

		Iterator<Chunk> columnIt = id2ChunkMap.values().iterator();
		while(columnIt.hasNext()) {
			if(tryUnloadColumn((Column)columnIt.next())) {
				columnIt.remove();
			}
		}
	}

	private boolean tryUnloadCube(Cube cube) {
		if(!cube.getTickets().canUnload()){
			return false; // There are tickets
		}

		// unload the Cube!
		cube.onUnload();
		cube.getColumn().removeCube(cube.getY());

		if(cube.needsSaving()) { // save the Cube, if it needs saving
			this.cubeIO.saveCube(cube);
		}
		return true;
	}

	private boolean tryUnloadColumn(Column column) {
		if(column.hasLoadedCubes()){
			return false; // It has loaded Cubes in it
			              // (Cubes are to Columns, as tickets are to Cubes... in a way)
		}
		column.unloaded = true; // flag as unloaded (idk, maybe vanilla uses this somewhere)

		// unload the Column!
		column.onChunkUnload();

		if(column.needsSaving(true)) { // save the Column, if it needs saving
			this.cubeIO.saveColumn(column);
		}
		return true;
	}
}
