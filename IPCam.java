import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.image.*;
import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;
import javax.swing.*;
import javax.imageio.*;

public class IPCam extends Canvas implements KeyListener, ActionListener {
	
	private int dx, dy, speed = 1, fx = 320, fy = 480, threshold;
	private float zoom = 1.1f;
	private boolean torch, focus, w, a, s, d, q, e, blackBackground, overDelayThreshold, detectMotion = false, drawMotionArea = false, IPW = true, fSet = false;
	private long maxDelay, lastImageTime, delay;
	
	private BufferedImage currImg, prevImg;
	private final int BLKSIZE = 8;
	private final javax.swing.Timer PAUSETMR, MOVETMR;
	private final ArrayList<Rectangle> BOXES;
	private final String ADDRESS;
	private final JFrame FRAME;
	private final Dimension D;
	private final Sound SND;
	private final File PATH;
	
	public IPCam(String[] args) {
		boolean fixArgs = true;
		while (fixArgs) {
			fixArgs = false;

			try {
				if (args.length < 2) {
					fixArgs = true;
				}

				if (!args[0].startsWith("http"))
					args[0] = "http://" + args[0];

				if (args.length > 2 && !args[2].equals("IPW"))
					IPW = false;

				if (IPW && args[0].indexOf("192.168.1.") > 0 && !args[0].endsWith(":8080/shot.jpg"))
					args[0] = args[0] + ":8080/shot.jpg";

				maxDelay = Long.parseLong(args[1]);

				if (args.length == 4) {
					fixArgs = true;
				}

				if (args.length > 3) {
					if (args[3].equals("motion"))
						detectMotion = true;
					else if (args[3].equals("display")) {
						detectMotion = true;
						drawMotionArea = true;
					}
				}

				if (args.length > 4) {
					threshold = Integer.parseInt(args[4]);
				}
			} catch (Exception ex) {
				fixArgs = true;
			}

			if (fixArgs) {
				String[] fa = new String[5];
				for (int i = 0; i < 5; i++) {
					fa[i] = "";
				}
				System.arraycopy(args, 0, fa, 0, Math.min(args.length, 5));
				args = fa;
				File settingsFile = new File("IPCam.ini");
				if (settingsFile.exists()) {
					try {
						Scanner settings = new Scanner(settingsFile);
						while (settings.hasNextLine()) {
							String s = settings.nextLine();
							if (s.startsWith("address:") && args[0].equals(""))
								args[0] = s.substring(8).trim();
							else if (s.startsWith("delay_warning_threshold:") && args[1].equals(""))
								args[1] = s.substring(24).trim();
							else if (s.startsWith("disable_IPW_features:") && args[2].equals(""))
								args[2] = s.substring(21).trim();
							else if (s.startsWith("motion:") && args[3].equals(""))
								args[3] = s.substring(7).trim();
							else if (s.startsWith("motion_detect_threshold:") && args[4].equals(""))
								args[4] = s.substring(24).trim();
						}
					} catch(FileNotFoundException fex) {
					}
				}
				if (args[0].equals(""))
					args[0] = "192.168.1.70";
				if (args[1].equals(""))
					args[1] = "3500";
				if (args[2].equals(""))
					args[2] = "IPW";
				if (args[3].equals(""))
					args[3] = "motion";
				if (args[4].equals(""))
					args[4] = "32";
				JTextField[] fields = new JTextField[5];
				for (int i = 0; i < fields.length; i++) {
					fields[i] = new JTextField(args[i]);
				}
				final JComponent[] inputs = new JComponent[] {
					new JLabel("Address:"), fields[0],
					new JLabel("Delay Warning Threshold (milliseconds):"), fields[1],
					new JLabel("IPW Features (\"IPW\" to enable):"), fields[2],
					new JLabel("Motion Detection (\"motion\" to enable, \"display\" to draw motion areas):"), fields[3],
					new JLabel("Motion Detection Threshold:"), fields[4]
				};
				int n = JOptionPane.showConfirmDialog(null, inputs, "IPCam Options", JOptionPane.OK_CANCEL_OPTION);
				if (n != JOptionPane.OK_OPTION) {
					System.exit(0);
				}
				for (int i = 0; i < fields.length; i++) {
					args[i] = fields[i].getText();
				}
			}
		}

		ADDRESS = args[0];
		
		PATH = new File("log/");
		if (!PATH.exists())
			PATH.mkdir();
		
		FRAME = new JFrame("IPCam");
		D = Toolkit.getDefaultToolkit().getScreenSize();
		FRAME.setLocation((D.width - fx) / 2, (D.height - fy) / 2);
		FRAME.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		// FRAME.getContentPane().setBackground(new Color(11, 11, 11));
		setPreferredSize(new Dimension(fx, fy));
		addKeyListener(this);
		
		BOXES = new ArrayList<Rectangle>();
		SND = new Sound();
		
		PAUSETMR = new javax.swing.Timer(2000, this);
		PAUSETMR.setInitialDelay(2000);
		
		MOVETMR = new javax.swing.Timer(42, new ActionListener() {
			public void actionPerformed(ActionEvent e){
				move();
				repaint();
			}
		});
		MOVETMR.start();
		
		Runnable r = new Runnable() {
			public void run() {
				while (true) {
					try {
						currImg = ImageIO.read(new URL(ADDRESS));
						fx = currImg.getWidth();
						fy = currImg.getHeight();
						if (!fSet) {
							setPreferredSize(new Dimension(fx, fy));
							FRAME.setLocation((D.width - fx) / 2, (D.height - fy) / 2);
							FRAME.pack();
							fSet = true;
						}
						lastImageTime = System.currentTimeMillis();
						if (!difference().isEmpty()) {
							SND.fx();
							ImageIO.write(currImg, "jpg", new File(PATH+ "/" + 
								new SimpleDateFormat("yyyy-MM-dd--HH-mm-ss").format(new Date()) + ".jpg"));
						}
						prevImg = currImg;
					} catch (Exception ex) {
					}
					repaint();
				}
			}
		};
		Thread tr = new Thread(r);
		tr.start();

		Runnable r2 = new Runnable() {
			public void run() {
				while (true) {
					delay = System.currentTimeMillis() - lastImageTime;
					overDelayThreshold = delay > maxDelay;
				}
			}
		};
		Thread tr2 = new Thread(r2);
		tr2.start();
		
		FRAME.add(this);
		FRAME.pack();
		FRAME.setVisible(true);

		requestFocus();

		// try{
		// 	Robot rob = new Robot();
		// 	rob.mouseMove(D.width / 2, D.height / 2);
		// 	rob.mousePress(InputEvent.BUTTON1_MASK);
		// 	rob.mouseRelease(InputEvent.BUTTON1_MASK);
		// 	rob.mouseMove((D.width - fx) / 2 + 15, (D.height - fy) / 2 + 30);
		// }catch(Exception e){
		// }
	}

	public void actionPerformed(ActionEvent e) {
		if(focus)
			focus = false;
		PAUSETMR.stop();
	}

	public ArrayList<Rectangle> difference() {
		BOXES.clear();
		
		if(focus || prevImg == null)
			return BOXES;
		
		int w = currImg.getWidth();
		int h = currImg.getHeight();
		
		for (int i = 0; i < w; i += BLKSIZE) {
			for (int j = 0; j < h; j += BLKSIZE) {
				int avgDiff = 0;
				int prevColor, color, prevR, prevG, prevB, r, g, b;
				for (int x = i; x < w && x < i + BLKSIZE; x += 2) {
					for (int y = j; y < h && y < j + BLKSIZE; y += 2) {
						prevColor = prevImg.getRGB(i, j);
						color = currImg.getRGB(i, j);
						
						prevR = (prevColor & 0x00ff0000) >> 16;
						prevG = (prevColor & 0x0000ff00) >> 8;
						prevB = prevColor & 0x000000ff;
						
						r = (color & 0x00ff0000) >> 16;
						g = (color & 0x0000ff00) >> 8;
						b = color & 0x000000ff;
						
						avgDiff += Math.abs(((prevR + prevG + prevB) / 3) - ((r + g + b) / 3));
					}
				}
				avgDiff /= ((BLKSIZE * BLKSIZE) / (2 * 2));
				
				if (avgDiff > threshold) {
					FRAME.setAlwaysOnTop(true);
					FRAME.setAlwaysOnTop(false);
					BOXES.add(new Rectangle(i, j, BLKSIZE, BLKSIZE));
				}
			}
		}
		
		return BOXES;
	}

	public void keyPressed(KeyEvent ke) {
		switch (ke.getKeyCode()) {
		case KeyEvent.VK_W:
		case KeyEvent.VK_UP:
			w = true;
			break;
		case KeyEvent.VK_A:
		case KeyEvent.VK_LEFT:
			a = true;
			break;
		case KeyEvent.VK_S:
		case KeyEvent.VK_DOWN:
			s = true;
			break;
		case KeyEvent.VK_D:
		case KeyEvent.VK_RIGHT:
			d = true;
			break;
		case KeyEvent.VK_Q:
			q = true;
			break;
		case KeyEvent.VK_E:
			e = true;
			break;
		case KeyEvent.VK_SHIFT:
			speed = 10;
			break;
		case KeyEvent.VK_R:
			dx = dy = 0;
			zoom = 1.1f;
			setPreferredSize(new Dimension(fx, fy));
			if (FRAME.getLocation().getX() == (D.width - fx) / 2 && FRAME.getLocation().getY() == (D.height - fy) / 2)
				FRAME.setLocation(0, 0);
			else
				FRAME.setLocation((D.width - fx) / 2, (D.height - fy) / 2);
			FRAME.pack();
			break;
		case KeyEvent.VK_F:
			if (!IPW)
				break;
			focus = true;
			PAUSETMR.start();
			try {
				InputStreamReader isr = new InputStreamReader(
						new URL(ADDRESS + "/focus").openConnection().getInputStream());
				isr.close();
			} catch (Exception ex) {}
			break;
		case KeyEvent.VK_T:
			if (!IPW)
				break;
			torch = !torch;
			try {
				InputStreamReader isr = new InputStreamReader(
						new URL(ADDRESS + "/" + (torch ? "enable" : "disable") + "torch").openConnection().getInputStream());
				isr.close();
			} catch (Exception ex) {}
			break;
		case KeyEvent.VK_B:
			blackBackground = !blackBackground;
			break;
		}
	}
	
	public void move(){
		// Zoom
		if(q)
			zoom = zoom / 1.1 > 0.5 ? zoom /= 1.1 : zoom;
		else if(e)
			zoom = zoom * 1.1 < 10 ? zoom *= 1.1 : zoom;

		float delta = Math.max(3, 5 / zoom);
		
		//Vertical
		if(w)
			dy -= delta * speed;
		else if(s)
			dy += delta * speed;
		
		// Horizontal
		if(a)
			dx -= delta * speed;
		else if(d)
			dx += delta * speed;
	}

	public void keyReleased(KeyEvent ke) {
		switch (ke.getKeyCode()) {
		case KeyEvent.VK_W:
		case KeyEvent.VK_UP:
			w = false;
			break;
		case KeyEvent.VK_A:
		case KeyEvent.VK_LEFT:
			a = false;
			break;
		case KeyEvent.VK_S:
		case KeyEvent.VK_DOWN:
			s = false;
			break;
		case KeyEvent.VK_D:
		case KeyEvent.VK_RIGHT:
			d = false;
			break;
		case KeyEvent.VK_Q:
			q = false;
			break;
		case KeyEvent.VK_E:
			e = false;
			break;
		case KeyEvent.VK_SHIFT:
			speed = 1;
			break;
		}
	}

	public void keyTyped(KeyEvent ke) {
	}

	public void paint(Graphics2D g) {
		try {
    		AffineTransform at = new AffineTransform(g.getTransform());
    		at.translate(dx + (fx - currImg.getWidth() * zoom) / 2, dy + (fy - currImg.getHeight() * zoom) / 2);
			at.scale(zoom, zoom);
			g.setTransform(at);
		} catch (Exception ex) {
		}
		
		if (currImg == null)
			return;
		
		g.drawImage(currImg, 0, 0, this);
		
		if (drawMotionArea) {
			g.setColor(new Color(255, 0, 0, 30));
			for(int i = 0; i < BOXES.size(); i++){
				Rectangle r = BOXES.get(i);
				g.fillRect(r.x, r.y, r.width, r.height);
			}
		}

		if (overDelayThreshold) {
			g.setTransform(new AffineTransform());
			g.setColor(Color.RED);
			g.setStroke(new BasicStroke(5));
			g.drawRect(0, 0, getWidth(), getHeight());
			g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
			g.drawString("Warning: last update was " + String.format("%.3f", (delay / 1000.0)) + " seconds ago", 20, 20);
		}
	}

	public void paint(Graphics g) {
		paint((Graphics2D) g);
	}

	public void update(Graphics g) {
		Graphics offgc;
		Image offscreen = null;
		Dimension d = getSize();
		offscreen = createImage(d.width, d.height);
		offgc = offscreen.getGraphics();
		if (blackBackground) {
			offgc.setColor(Color.BLACK);
			offgc.fillRect(0, 0, getWidth(), getHeight());
		} else {
			offgc.setColor(Color.WHITE);
			offgc.fillRect(0, 0, getWidth(), getHeight());
			offgc.setColor(new Color(191, 191, 191));
			for (int i = 0; i < getWidth(); i += 16) {
				for (int j = 0; j < getHeight(); j += 16) {
					offgc.fillRect(i, j, 8, 8);
					offgc.fillRect(i + 8, j + 8, 8, 8);
				}
			}
		}
		offgc.setColor(getForeground());
		paint(offgc);
		g.drawImage(offscreen, 0, 0, this);
	}

	public static void main(String[] args) {
		new IPCam(args);
	}
}