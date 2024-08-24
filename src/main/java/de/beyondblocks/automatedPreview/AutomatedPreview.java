package de.beyondblocks.automatedPreview;

import se.llbit.chunky.PersistentSettings;
import se.llbit.chunky.main.Chunky;
import se.llbit.chunky.main.ChunkyOptions;
import se.llbit.chunky.renderer.ConsoleProgressListener;
import se.llbit.chunky.renderer.DefaultRenderManager;
import se.llbit.chunky.renderer.PreviewRenderer;
import se.llbit.chunky.renderer.RenderContext;
import se.llbit.chunky.renderer.projection.ProjectionMode;
import se.llbit.chunky.renderer.scene.Camera;
import se.llbit.chunky.renderer.scene.CustomPreviewRayTracer;
import se.llbit.chunky.renderer.scene.Scene;
import se.llbit.chunky.resources.TexturePackLoader;
import se.llbit.chunky.world.ChunkPosition;
import se.llbit.chunky.world.World;
import se.llbit.log.Level;
import se.llbit.log.Log;
import se.llbit.log.Receiver;
import se.llbit.nbt.NamedTag;
import se.llbit.nbt.Tag;
import se.llbit.util.TaskTracker;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

public class AutomatedPreview {
    public static final String MC_VERSION = "1.21.1";
    public static final String CACHE_DIR = ".automated-preview-cache";


    public static void main(String[] args) throws InterruptedException {
        if (args.length < 1 || args.length > 2) {
            // TODO: fov modifier, dimension override, chunk radius override, spawn override, ...
            System.err.println("Usage: java -jar automatedPreview.jar [Path to world directory] (Output Directory)");
            System.exit(1);
        }

        TaskTracker taskTracker = new TaskTracker(new ConsoleProgressListener()); // Maybe make this also just output to the log

        Log.setLevel(Level.INFO);
        Log.setReceiver(new Receiver() {
            @Override
            public void logEvent(Level level, String message) {
                if (level.equals(Level.ERROR)){
                    System.err.println("[" + level + "] " + message);
                }else {
                    System.out.println("[" + level + "] " + message);
                }
            }
        }, Level.INFO, Level.ERROR, Level.WARNING);


        File cacheDir = new File(CACHE_DIR);
        if (!cacheDir.exists()) {
            if (!cacheDir.mkdirs()) {
                System.err.println("Failed to create cache directory: " + cacheDir.getAbsolutePath());
                System.exit(1);
            }
        }

        File texturePath = new File(cacheDir, "minecraft-" + MC_VERSION + ".jar");
        if (!texturePath.exists()) {
            try {
                Log.info(
                        "Downloading Minecraft " + MC_VERSION + " to " + texturePath.getAbsolutePath());
                MinecraftDownloader.downloadMinecraft(MC_VERSION, texturePath.toPath()).get();
            } catch (Exception e) {
                Log.error("Could not download assets", e);
                System.exit(-1);
                return;
            }
            Log.info("Finished downloading");
        } else {
            Log.info("Using cached Minecraft " + MC_VERSION + " from " + texturePath.getAbsolutePath());
        }


        // Parse arguments
        File worldDirectory = new File(args[0]);
        File outputDirectory = (args.length == 2) ? new File(args[1]) : new File(".");

        if (!outputDirectory.exists()) {
            if (!outputDirectory.mkdirs()) {
                System.err.println("Failed to create output directory: " + outputDirectory.getAbsolutePath());
                System.exit(1);
            }
        }

        Chunky chunky = new Chunky(ChunkyOptions.getDefaults());

        String[] resourcePacks = new String[1];
        resourcePacks[0] = texturePath.getAbsolutePath();

        PersistentSettings.setDisableDefaultTextures(true);
        TexturePackLoader.loadTexturePacks(resourcePacks, false);

        Integer dimension = guessDimension(worldDirectory);

        World world = World.loadWorld(worldDirectory, dimension, World.LoggedWarnings.NORMAL);

        int spawnX;
        int spawnY;
        int spawnZ;

        // Manually reading the World Spawn Coordinates
        File worldFile = new File(worldDirectory, "level.dat");
        try (FileInputStream fin = new FileInputStream(worldFile);
             InputStream gzin = new GZIPInputStream(fin);
             DataInputStream in = new DataInputStream(gzin)) {
            Set<String> request = new HashSet<>();
            request.add(".Data.SpawnX");
            request.add(".Data.SpawnY");
            request.add(".Data.SpawnZ");

            Map<String, Tag> result = NamedTag.quickParse(in, request);

            spawnX = result.get(".Data.SpawnX").intValue();
            spawnY = result.get(".Data.SpawnY").intValue();
            spawnZ = result.get(".Data.SpawnZ").intValue();

        } catch (IOException e) {
            Log.error("Failed to load level.dat");
            Log.error("Error: " + e.getMessage());

            // Fallback to the Player Spawn Position Chunky is providing
            spawnX = (int) world.spawnPosX();
            spawnY = (int) world.spawnPosY();
            spawnZ = (int) world.spawnPosZ();

            System.exit(-1);
        }


        Log.info("Dimension:" + world.currentDimension());
        Log.info("Level Name:" + world.levelName());
        Log.info("Spawn: " + spawnX + ", " + spawnY + ", " + spawnZ);


        Scene scene = chunky.getSceneFactory().newScene();
        scene.initBuffers();
        scene.setCanvasSize(1920, 1080);

        // TODO: Make it match to the World Size

        scene.setYClipMin(-64);
        scene.setYClipMax(320);
        int radius = WorldAnalyser.calculateChunkRadius(worldDirectory);

        Log.info("Calculated Radius: " + radius);
        if (radius <= 0) {
            radius = 8;
        } else if (radius > 128) {
            radius = 128;
        }
        Log.info("Using Radius: "+ radius);
        // System.exit(0);
        scene.loadChunks(taskTracker, world, chunksAroundSpawn(spawnX, spawnZ, radius));
        scene.moveCameraToCenter();

        double[][] angles = {
                {-Math.PI / 4, -Math.PI / 4},
                {-3 * Math.PI / 4, -Math.PI / 4},
                {-5 * Math.PI / 4, -Math.PI / 4},
                {-7 * Math.PI / 4, -Math.PI / 4}
        };

        for (int i = 0; i < angles.length; i++) {
            RenderContext context = new RenderContext(chunky);
            PreviewRenderer renderer = new PreviewRenderer("", "", "", new CustomPreviewRayTracer());
            DefaultRenderManager manager = new DefaultRenderManager(context, true);

            Scene newScene = manager.bufferedScene;
            newScene.copyState(scene, true);
            newScene.name = "view_" + i;
            newScene.setTransparentSky(true);

            // context.setSceneDirectory(new File("/tmp/automated-preview-scenes/" + newScene.name())); Only needed if you want to store the scenes

            Camera camera = newScene.camera();

            camera.setView(angles[i][0], angles[i][1], 0);
            camera.setProjectionMode(ProjectionMode.PARALLEL);
            camera.setShift(0, 0);
            camera.setFoV(camera.getFov() * 0.5);

            newScene.setTargetSpp(1);

            renderer.render(manager);

            newScene.saveFrame(new File(outputDirectory, "output_view_" + i + ".png"), taskTracker, 2);
        }

        System.exit(0);
    }

    private static Integer guessDimension(File worldDirectory) {
        Integer dimension = null;

        // Check if world is only having one dimension
        // Bit 0: Overworld
        // Bit 1: Nether
        // Bit 2: End
        byte dimensions = 0;

        if (new File(worldDirectory, "region").exists()) {
            dimensions |= 1;
        } else if (new File(worldDirectory, "DIM-1/region").exists()) {
            dimensions |= 2;
        } else if (new File(worldDirectory, "DIM1/region").exists()) {
            dimensions |= 4;
        }

        int dimensionCount = Integer.bitCount(dimensions);

        if (dimensionCount == 1) {
            byte found_dimension = (byte) (dimensions & -dimensions);

            dimension = switch (found_dimension) {
                case 1 -> 0;
                case 2 -> -1;
                case 3 -> 1;
                default -> dimension;
            };
        }

        if (dimension == null) {
            dimension = 1;
        }

        return dimension;
    }

    public static Collection<ChunkPosition> chunksAroundSpawn(int x, int z, int radiusInChunks) {
        int spawnChunkX = x >> 4;
        int spawnChunkZ = z >> 4;

        List<ChunkPosition> chunkPositions = new ArrayList<>();

        for (int dx = -radiusInChunks; dx <= radiusInChunks; dx++) {
            for (int dz = -radiusInChunks; dz <= radiusInChunks; dz++) {
                int chunkX = spawnChunkX + dx;
                int chunkZ = spawnChunkZ + dz;
                chunkPositions.add(ChunkPosition.get(chunkX, chunkZ));
            }
        }

        return chunkPositions;
    }

}
