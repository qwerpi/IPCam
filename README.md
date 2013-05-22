# IPCam
## Description
This program allows you to monitor an IP Camera by specifying an address that downloads an immediate frame of the feed. It also provides some features for use with the IP Webcam app for Android.
It also plays a sound and saves the current frame to a log folder when motion is detected.

## Usage
<pre><code>./IPCam.jar [address] [motion_detect_threshold] [delay_warning_threshold] [optional motion] [optional disable_IPW_features]</code></pre>
* address - The address of the immediate video frame. If IPW features are not disabled, only the IP Address is necessary (i.e. 192.168.1.70). Otherwise, provide the full address (http://192.168.1.70:8080/shot.jpg)
* motion_detect_threshold - 32 works well for me. Play around with this number to get the desired sensitivity.
* delay_warning_threshold - The time, in milliseconds, between capturing frames before showing a warning.
* motion (optional) - "motion" to highlight areas where motion was detected in red. Anything else turns this feature off.
* disable_IPW_features - "IPW" enables features specific to the IP Webcam Android app. Anything else turns this feature off.

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