package ritzow.solomon.engine.world.base;

import ritzow.solomon.engine.util.Service;

public final class ClientWorldUpdater implements Service {
	private volatile World world;
	private volatile boolean setup, exit, finished;
	
	public ClientWorldUpdater(World world) {
		this.world = world;
	}
	
	@Override
	public void run() {
		BlockGridUpdater blockManagerForeground = new BlockGridUpdater(world, world.getForeground());
		BlockGridUpdater blockManagerBackground = new BlockGridUpdater(world, world.getBackground());
		new Thread(blockManagerForeground, "Foreground Block Updater").start();
		new Thread(blockManagerBackground, "Background Block Updater").start();
		
		synchronized(this) {
			setup = true;
			this.notifyAll();
		}
		
		try {
			long currentTime;
			float updateTime;
			long previousTime = System.nanoTime();
			while(!exit) {
			    currentTime = System.nanoTime();
			    updateTime = (currentTime - previousTime) * 0.0000000625f; //convert from nanoseconds to sixteenth of a milliseconds
			    previousTime = currentTime;
			    world.update(updateTime);
				Thread.sleep(16); //TODO change based on time it takes to update (computer speed)
			}
		} catch (InterruptedException e) {
			System.err.println("World update loop was interrupted");
		} finally {
			blockManagerForeground.exit();
			blockManagerForeground.waitUntilFinished();
			blockManagerBackground.exit();
			blockManagerBackground.waitUntilFinished();
			synchronized(this) {
				finished = true;
				notifyAll();
			}
		}
	}
	
	public World getWorld() {
		return world;
	}
	
	@Override
	public void exit() {
		exit = true;
	}

	@Override
	public boolean isFinished() {
		return finished;
	}

	@Override
	public boolean isSetupComplete() {
		return setup;
	}
}