package ritzow.sandbox.client.graphics;

import static org.lwjgl.glfw.GLFW.glfwSwapInterval;
import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_LINEAR;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glViewport;
import static org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.glBindFramebuffer;
import static org.lwjgl.opengl.GL30.glBlitFramebuffer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import ritzow.sandbox.client.input.InputManager;
import ritzow.sandbox.client.input.handler.FramebufferSizeHandler;
import ritzow.sandbox.client.input.handler.WindowFocusHandler;

/** Initializes OpenGL and loads data to the GPU, then renders any Renderable objects added to the List returned by getUpdatables() **/
public final class RenderManager implements Runnable, FramebufferSizeHandler, WindowFocusHandler {
	private volatile boolean updateViewport, focused;
	private volatile int framebufferWidth, framebufferHeight;
	private final Display display;
	private final List<Renderer> renderers;
	
	public RenderManager(Display display) {
		this.display = display;
		this.link(display.getInputManager());
		this.renderers = new ArrayList<>();
	}
	
	public void initialize() {
		display.setGraphicsContextOnThread();
		org.lwjgl.opengl.GL.createCapabilities();
		glfwSwapInterval(0);
		glDisable(GL_DEPTH_TEST);
		glEnable(GL_BLEND);
		GraphicsUtility.checkErrors();
	}

	public void shutdown() {
		GraphicsUtility.checkErrors();
		renderers.clear();
		display.closeContext();
		org.lwjgl.opengl.GL.destroy();
	}
	
	public void run() {
		if(focused) {
			int width = framebufferWidth, height = framebufferHeight;
			if(updateViewport) {
				glViewport(0, 0, width, height);
				updateViewport = false;
			}

			glClear(GL_COLOR_BUFFER_BIT);

			for(Renderer r : renderers) {
				glBindFramebuffer(GL_READ_FRAMEBUFFER, r.render(width, height).framebufferID);
				glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
				glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL_COLOR_BUFFER_BIT, GL_LINEAR);
			}

			GraphicsUtility.checkErrors();
			display.refresh();
		}
	}
	
	public Collection<Renderer> getRenderers() {
		return renderers;
	}

	@Override
	public void framebufferSize(int width, int height) {
		framebufferWidth = width;
		framebufferHeight = height;
		updateViewport = true;
	}

	@Override
	public void windowFocus(boolean focused) {
		this.focused = focused;
	}

	@Override
	public void link(InputManager manager) {
		manager.getFramebufferSizeHandlers().add(this);
		manager.getWindowFocusHandlers().add(this);
	}

	@Override
	public void unlink(InputManager manager) {
		manager.getFramebufferSizeHandlers().remove(this);
		manager.getWindowFocusHandlers().remove(this);
	}
}
