package ritzow.solomon.engine.main;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import ritzow.solomon.engine.audio.Audio;
import ritzow.solomon.engine.graphics.Background;
import ritzow.solomon.engine.graphics.GraphicsManager;
import ritzow.solomon.engine.input.Controls;
import ritzow.solomon.engine.input.EventProcessor;
import ritzow.solomon.engine.input.InputManager;
import ritzow.solomon.engine.input.controller.Controller;
import ritzow.solomon.engine.input.controller.InteractionController;
import ritzow.solomon.engine.input.controller.PlayerController;
import ritzow.solomon.engine.input.controller.TrackingCameraController;
import ritzow.solomon.engine.input.handler.KeyHandler;
import ritzow.solomon.engine.input.handler.WindowCloseHandler;
import ritzow.solomon.engine.network.Client;
import ritzow.solomon.engine.resource.Models;
import ritzow.solomon.engine.util.ByteUtil;
import ritzow.solomon.engine.util.ClientUpdater;
import ritzow.solomon.engine.world.base.World;
import ritzow.solomon.engine.world.base.WorldUpdater;
import ritzow.solomon.engine.world.entity.Player;

public class StartClient {
	public static void main(String[] args) throws IOException {
		Client client = new Client();
		new Thread(client, "Client Thread").start();
		client.waitForSetup();

		InetSocketAddress address;
		
		if(args.length == 2) {
			try {
				address = new InetSocketAddress(args[0], Integer.valueOf(args[1]));
			} catch(IllegalArgumentException | SecurityException e) {
				System.out.println("Cannot connect to the specified address: " + e.getLocalizedMessage());
				address = new InetSocketAddress(InetAddress.getLocalHost(), 50000);
			}
		} else {
			address = new InetSocketAddress(InetAddress.getLocalHost(), 50000);
			System.out.println(address.getAddress());
		}
		
		//connect to the server once the client side graphics and whatnot is finished being set up
		if(client.connectTo(address, 1000)) {
			System.out.println("Client connected to server");
			EventProcessor eventProcessor = new EventProcessor();
			new Thread(new ClientManager(client, eventProcessor), "Client Manager").start();
			eventProcessor.run();
		} else {
			System.out.println("Client failed to connect to server");
			client.waitForExit();
		}
	}
}

class ClientManager implements Runnable, WindowCloseHandler, KeyHandler {
	private final Client client;
	private final EventProcessor eventProcessor;
	private volatile boolean exit;
	
	public ClientManager(Client client, EventProcessor eventManager) {
		this.client = client;
		this.eventProcessor = eventManager;
	}
	
	@Override
	public void run() {
		//start OpenAL and load audio files (will run on this thread, not the best solution)
		Audio.start();
		
		//wait for the client to receive the world and return it
		World world = client.getWorld();
		
		//wait for the Display, and thus InputManager, to be created, then link the game manager to the display so that the escape button and x button exit the game
		eventProcessor.waitForSetup();
		eventProcessor.getDisplay().getInputManager().getWindowCloseHandlers().add(this);
		eventProcessor.getDisplay().getInputManager().getKeyHandlers().add(this);
		
		//start the graphics manager, which will load all models into OpenGL and setup the OpenGL context.
		GraphicsManager graphicsManager = new GraphicsManager(eventProcessor.getDisplay());
		new Thread(graphicsManager, "Graphics Manager").start();
		
		//wait for the graphics manager to finish setup.
		graphicsManager.waitForSetup();

		//Add the background and world to the renderer
		graphicsManager.getRenderables().add(new Background(Models.CLOUDS));
		graphicsManager.getRenderables().add(world);
		
		//create the client update manager and link it with the window events
		ClientUpdater clientUpdater = new ClientUpdater();
		clientUpdater.link(eventProcessor.getDisplay().getInputManager());
		
		//get the client's player object, which has already been added to the world
		Player player = client.getPlayer();
		
		//create player controllers so the user can "play the game", for testing purposes
		Controller[] controllers = new Controller[] {
				new PlayerController(player, world),
				new InteractionController(player, world, graphicsManager.getRenderer().getCamera(), 200, false),
				new TrackingCameraController(graphicsManager.getRenderer().getCamera(), player, 0.005f, 0.05f, 0.6f)
		};
		
		//add the controllers to the input manager so they receive input events
		for(Controller c : controllers) {
			c.link(eventProcessor.getDisplay().getInputManager());
			clientUpdater.getUpdatables().add(c);
		}
		
		//start the client updater
		new Thread(clientUpdater, "Client Updater Thread").start();
		
		//start updating the world
		WorldUpdater worldUpdater = new WorldUpdater(world);
		new Thread(worldUpdater, "World Updater Thread").start();
		
		//display the window now that everything is set up.
		eventProcessor.setReadyToDisplay();
		
		//wait until the game should exit
		synchronized(this) {
			while(!exit) {
				try {
					this.wait();
				} catch(InterruptedException e) {
					e.printStackTrace(); //the game manager should never be interrupted
				}
			}
		}
		
		//start exiting game
		worldUpdater.exit();
		clientUpdater.exit();
		client.exit();
		Audio.stop();
		
		//wait for the renderer to stop before closing the window
		graphicsManager.waitForExit();
		eventProcessor.exit();
		
		//make sure the client and server are closed before saving the world
		client.waitUntilFinished();

		//save the world to the file "data/worlds/testWorld.dat"
		try(FileOutputStream out = new FileOutputStream(new File("data/worlds/testWorld.dat"))) {
			System.out.print("Saving world... ");
			byte[] serialized = ByteUtil.compress(ByteUtil.serialize(world));
			out.write(serialized);
			out.getChannel().truncate(serialized.length);
			System.out.println("world saved to " + serialized.length + " bytes");
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		//make sure the client updater and event manager are closed before exiting the "game" thread
		worldUpdater.waitUntilFinished();
 		clientUpdater.waitUntilFinished();
 		eventProcessor.waitUntilFinished();
	}
	
	/** can be called from any thread to exit the game **/
	public synchronized void exit() {
		exit = true;
		notifyAll();
	}

	@Override
	public void windowClose() {
		exit();
	}

	@Override
	public void keyboardButton(int key, int scancode, int action, int mods) {
		if(key == Controls.KEYBIND_QUIT && action == org.lwjgl.glfw.GLFW.GLFW_PRESS) {
			exit();
		}
		
        else if(key == Controls.KEYBIND_FULLSCREEN && action == org.lwjgl.glfw.GLFW.GLFW_PRESS) {
        	//this method should only be called from the event manager thread so it is safe to call OS related methods
            eventProcessor.getDisplay().setFullscreen(!eventProcessor.getDisplay().getFullscreen());
        }
	}

	@Override
	public void link(InputManager manager) {
		manager.getKeyHandlers().add(this);
	}

	@Override
	public void unlink(InputManager manager) {
		manager.getKeyHandlers().remove(this);
	}
}