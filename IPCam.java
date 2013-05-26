/*
TODO: optimize cpu usage
*/

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
	private long maxDelay, lastImageTime, delay, waitTime;
	
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
		HashMap<String, String> options = new HashMap<String, String>();
		options.put("address", "");
		options.put("skip", "false");
		options.put("ipw", "false");
		options.put("w", "0");
		options.put("d", "-1");
		options.put("m", "false");
		options.put("s", "false");
		options.put("t", "0");

		File settingsFile = new File("IPCam.ini");
		if (settingsFile.exists()) {
			try {
				Scanner settings = new Scanner(settingsFile);
				while (settings.hasNextLine()) {
					String s = settings.nextLine();
					if (s.startsWith("address:"))
						options.put("address", s.substring(8).trim());
					else if (s.startsWith("delay_warning_threshold:"))
						options.put("d", s.substring(24).trim());
					else if (s.startsWith("wait_time:"))
						options.put("w", s.substring(10).trim());
					else if (s.startsWith("IPW_features:"))
						options.put("ipw", s.substring(13).trim());
					else if (s.startsWith("motion:")) {
						if (s.substring(7).trim().equals("motion")) {
							options.put("m", "true");
						} else if (s.substring(7).trim().equals("display")) {
							options.put("m", "true");
							options.put("s", "true");
						}
					}else if (s.startsWith("motion_detect_threshold:"))
						options.put("t", s.substring(24).trim());
				}
			} catch(FileNotFoundException fex) {
			}
		} else {
			try {
				System.err.println("Creating default IPCam.ini file...");
				FileWriter writer = new FileWriter(settingsFile);
				writer.write("# address - The address of the immediate video frame. If IPW features are not disabled, only the IP Address is necessary (i.e. 192.168.1.70). Otherwise, provide the full address (http://192.168.1.70:8080/shot.jpg\n"
						   + "# delay_warning_threshold - The time, in milliseconds, between capturing frames before showing a warning.\n"
						   + "# wait_time: The time, in milliseconds, to wait before capturing the next frame.\n"
						   + "# disable_IPW_features - \"IPW\" enables features specific to the IP Webcam Android app. Anything else turns this feature off.\n"
						   + "# motion (optional) - \"motion\" to enable motion detection. \"display\" to highlight areas where motion was detected in red. Anything else turns this feature off.\n"
						   + "# motion_detect_threshold - 32 works well for me. Play around with this number to get the desired sensitivity.\n"
						   + "\n"
						   + "address: 192.168.1.83\n"
						   + "delay_warning_threshold: 3500\n"
						   + "wait_time: 0\n"
						   + "IPW_features: true\n"
						   + "motion: motion\n"
						   + "motion_detect_threshold: 32\n");
				writer.close();
				System.err.println("IPCam.ini file created.");
			} catch (Exception ex) {
			}
		}

		if (!options.get("skip").equals("true")) {
			boolean fixArgs = true;
			while (fixArgs) {
				fixArgs = false;
				JTextField addressTextField = new JTextField(options.get("address"));
				JTextField ipwTextField = new JTextField(options.get("ipw"));
				JTextField wTextField = new JTextField(options.get("w"));
				JTextField dTextField = new JTextField(options.get("d"));
				JTextField mTextField = new JTextField(options.get("m"));
				JTextField sTextField = new JTextField(options.get("s"));
				JTextField tTextField = new JTextField(options.get("t"));
				final JComponent[] inputs = new JComponent[] {
					new JLabel("Address:"), addressTextField,
					new JLabel("IPW Features (true/false):"), ipwTextField,
					new JLabel("Minimum Delay (milliseconds):"), wTextField,
					new JLabel("Delay Warning Threshold (milliseconds):"), dTextField,
					new JLabel("Motion Detection Enabled (true/false):"), mTextField,
					new JLabel("Motion Detection Display (true/false):"), sTextField,
					new JLabel("Motion Threshold:"), tTextField
				};
				int n = JOptionPane.showConfirmDialog(null, inputs, "IPCam Options", JOptionPane.OK_CANCEL_OPTION);
				if (n != JOptionPane.OK_OPTION) {
					System.exit(0);
				}
				options.put("address", addressTextField.getText());
				options.put("ipw", ipwTextField.getText());
				options.put("w", wTextField.getText());
				options.put("d", dTextField.getText());
				options.put("m", mTextField.getText());
				options.put("s", sTextField.getText());
				options.put("t", tTextField.getText());

				try {
					if (!options.get("address").startsWith("http"))
						options.put("address", "http://" + options.get("address"));

					IPW = options.get("ipw").equals("true");

					if (IPW && options.get("address").indexOf("192.168.1.") > 0 && !options.get("address").endsWith(":8080/shot.jpg"))
						options.put("address", options.get("address") + ":8080/shot.jpg");

					if (options.get("m").equals("false") && options.get("s").equals("true")) {
						options.put("s", "false");
						fixArgs = true;
					}

					detectMotion = options.get("m").equals("true");
					drawMotionArea = options.get("s").equals("true");

					waitTime = Long.parseLong(options.get("w"));
					maxDelay = Long.parseLong(options.get("d"));

					threshold = Integer.parseInt(options.get("t"));
				} catch (Exception ex) {
					fixArgs = true;
				}
			}
		}

		ADDRESS = options.get("address");
		
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
					if (delay < waitTime) {
						try {
							Thread.sleep(waitTime - delay);
						} catch (Exception ex) {
						}
						continue;
					}
					InputStream in;
					try {
						/* *
						URLConnection con = (new URL(ADDRESS)).openConnection();
						con.setReadTimeout(Math.max(3000, (int)maxDelay));
						currImg = ImageIO.read(con.getInputStream());
						/*/
						currImg = ImageIO.read(new URL(ADDRESS));
						/* */
						if (!fSet) {
							fx = currImg.getWidth();
							fy = currImg.getHeight();
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

		if (maxDelay >= 0 || waitTime > 0) {
			Runnable r2 = new Runnable() {
				public void run() {
					while (true) {
						delay = System.currentTimeMillis() - lastImageTime;
						if (maxDelay >= 0)
							overDelayThreshold = delay > maxDelay;
						try {
							Thread.sleep(1);
						} catch (Exception ex) {
						}
					}
				}
			};
			Thread tr2 = new Thread(r2);
			tr2.start();
		}
		
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