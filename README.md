# IPCam
## Description
This program allows you to monitor an IP Camera by specifying an address that downloads an immediate frame of the feed. It also provides some features for use with the IP Webcam app for Android.
It also plays a sound and saves the current frame to a log folder when motion is detected.

## Usage
<pre><code>./IPCam.jar [address]</code></pre>
* address - The address of the immediate video frame. If IPW features are enabled, only the IP Address is necessary (i.e. 192.168.1.70). Otherwise, provide the full address (http://192.168.1.70:8080/shot.jpg)

## Keyboard Shortcuts
<pre><code>   A / left arrow - move image left
   S / down arrow - move image down
  D / right arrow - move image right
     W / up arrow - move image up
Shift + direction - speed up
                R - reset window
                Q - zoom out
                E - zoom in
                F - refocus (IPW option only)
                T - torch (IPW option only)
                A - move image left</code></pre>