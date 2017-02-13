package ritzow.solomon.engine.main;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.SocketException;
import java.net.UnknownHostException;
import ritzow.solomon.engine.network.Server;
import ritzow.solomon.engine.util.ByteUtil;
import ritzow.solomon.engine.world.base.World;
import ritzow.solomon.engine.world.block.DirtBlock;
import ritzow.solomon.engine.world.block.GrassBlock;
import ritzow.solomon.engine.world.block.RedBlock;

public class StartServer {
	public static void main(String[] args) throws SocketException, UnknownHostException {
		Server server = new Server();
		new Thread(server, "Server Thread").start();
		
		server.waitForSetup();
		
		//the save file to try to load the world from
		File saveFile = new File("data/worlds/testWorld.dat");
		
		//if a world exists, load it.
		if(saveFile.exists()) {
			try(FileInputStream in = new FileInputStream(saveFile)) {
				byte[] data = new byte[(int)saveFile.length()];
				in.read(data);
				World world = (World)ByteUtil.deserialize(ByteUtil.decompress(data));
				server.startWorld(world); //start the world on the server, which will send it to clients that connect
			} catch(IOException | ReflectiveOperationException e) {
				System.err.println("Couldn't load world from file: " + e.getLocalizedMessage() + ": " + e.getCause());
				e.printStackTrace();
				System.exit(1);
			}
		} else { //if no world can be loaded, create a new one.
			World world = new World(300, 100);
			for(int column = 0; column < world.getForeground().getWidth(); column++) {
				double height = world.getForeground().getHeight()/2;
				height += (Math.sin(column * 0.1f) + 1) * (world.getForeground().getHeight() - height) * 0.05f;
				for(int row = 0; row < height; row++) {
					if(Math.random() < 0.007) {
						world.getForeground().set(column, row, new RedBlock());
					} else {
						world.getForeground().set(column, row, new DirtBlock());
					}
					world.getBackground().set(column, row, new DirtBlock());
				}
				world.getForeground().set(column, (int)height, new GrassBlock());
				world.getBackground().set(column, (int)height, new DirtBlock());
			}
			server.startWorld(world); //start the world on the server, which will send it to clients that connect
		}
	}
}