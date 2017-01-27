package ritzow.solomon.engine.util;

/**
 * Instances of Exitable can be passed to the Synchronizer utility to wait until exited. When an Exitable finishes, the isFinished method should return true and
 * the object should call notifyAll()
 * @author Solomon Ritzow
 *
 */
public interface Exitable {
	public void exit();
	public boolean isFinished();
}