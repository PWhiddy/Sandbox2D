package ritzow.sandbox.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.Scanner;
import ritzow.sandbox.data.ByteUtil;
import ritzow.sandbox.data.Deserializer;
import ritzow.sandbox.network.Protocol;
import ritzow.sandbox.server.Server.ClientState;
import ritzow.sandbox.world.World;
import ritzow.sandbox.world.block.DirtBlock;
import ritzow.sandbox.world.block.GrassBlock;
import ritzow.sandbox.world.entity.PlayerEntity;

public final class StartServer {
	private static final boolean SAVE_WORLD = false;
	
	public static void main(String... args) throws SocketException {
		Thread.currentThread().setName("Server Setup");
		
		Server server = new Server(new InetSocketAddress(Protocol.DEFAULT_SERVER_UDP_PORT));

		//the save file to try to load the world from
		final File saveFile = new File(args.length > 0 ? args[0] : "data/worlds/world.dat");
		
		World world = saveFile.exists() ? 
				loadWorld(saveFile, SerializationProvider.getProvider()) : generateWorld(1000, 1000, server);
		server.start(world);
		
		System.out.println("Startup Complete.");
		System.out.println("Type 'exit' to stop server or 'list' to list connected clients");
		try(Scanner scanner = new Scanner(System.in)) {
			String next;
			reader: while(true) {
				switch(next = scanner.nextLine()) {
				case "exit":
				case "quit":
				case "stop":
					break reader;
				case "list":
					if(server.getConnectedClients() == 0) {
						System.out.println("No connected clients.");
					} else {
						System.out.println("Connected clients:");
						for(ClientState client : server.listClients()) {
							System.out.print("\t - ");
							System.out.println(client);
						}
					}
					break;
				case "disconnect":
					server.disconnectAll("server manual disconnect");
					break;
				default:
					server.broadcastConsoleMessage(next);
					System.out.println("Sent message '" + next + "' to " + 
					server.getConnectedClients() + " connected client(s).");
				}
			}
		}
		
		server.stop();
		
		if(SAVE_WORLD) {
			try {
				if(!saveFile.exists())
					saveFile.createNewFile();
				saveWorld(world, saveFile);
			} catch (IOException e) {
				System.out.println("Could not find or create file to save world: " + e.getLocalizedMessage());
			}
		} else {
			System.out.println("Server stopped.");
		}
	}
	
	public static void saveWorld(World world, File file) {
		try(FileOutputStream out = new FileOutputStream(file)) {
			System.out.print("Saving world... ");
			world.removeIf(e -> e instanceof PlayerEntity); //remove players before saving to file
			byte[] serialized = ByteUtil.compress(SerializationProvider.getProvider().serialize(world));
			out.write(serialized);
			out.getChannel().truncate(serialized.length);
			System.out.println("world saved to " + serialized.length + " bytes.");
		} catch (IOException e) {
			System.out.println("Error while saving world to file: " + e.getLocalizedMessage());
		}
	}
	
	public static World loadWorld(File file, Deserializer des) {
		try(FileInputStream in = new FileInputStream(file)) {
			byte[] data = new byte[(int)file.length()];
			in.read(data);
			World world = des.deserialize(ByteUtil.decompress(data));
			return world;
		} catch(IOException e) {
			System.out.println("Error loading world from file " + e);
			return null;
		}
	}
	
	public static World generateWorld(int width, int height, Server server) {
		World world = new World(width, height, 0.016f);
		for(int column = 0; column < world.getForeground().getWidth(); column++) {
			double halfheight = world.getForeground().getHeight()/2;
			halfheight += (Math.sin(column * 0.1f) + 1) * (world.getForeground().getHeight() - halfheight) * 0.05f;
			for(int row = 0; row < halfheight; row++) {
				world.getForeground().set(column, row, new DirtBlock());
				world.getBackground().set(column, row, new DirtBlock());
			}
			world.getForeground().set(column, (int)halfheight, new GrassBlock());
			world.getBackground().set(column, (int)halfheight, new DirtBlock());
		}
		return world;
	}
}
