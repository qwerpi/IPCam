import java.io.BufferedInputStream;

import javazoom.jl.player.Player;

public class Sound {
	private Player player;
	private Thread t;
	private Runnable r;

	// Plays the detect sound
	public void fx() {
		if (r == null)
			r = new Runnable() {
				public void run() {
					try {
						BufferedInputStream buffer = new BufferedInputStream(
								getClass().getResourceAsStream("detect.mp3"));
						player = new Player(buffer);
						player.play();
					} catch (Exception e) {
						System.out.println(e);
					}
				}
			};
		if (t != null && t.isAlive())
			return;
		t = new Thread(r);
		t.start();
	}
}