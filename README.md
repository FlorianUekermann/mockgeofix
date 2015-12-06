building/hacking/...
--------------------

Checkout this repo in Android studio, wait for gradle sync to finish
(you might need to install missing android-21 target) and click "build" :)

Just click "Run" and choose a running device (requires "USB debugging" enabled)..  

Alternatively, you can use "Build/Build APK" to build an unsigned APK so you can copy and 
install this APK on your phone (requires allowed "Unknown sources").


helper scripts
-------------------
`helper_scripts/run_sim.py` loads an GPX file and replays it sending "geo fix" commands. 
This script also support optional live visualization of the simulated route in a web browser.

### Examples

1. run MockGeoFix on your phone and click "start"
2. run `helper_scripts/run_sim.py -i <your_phone_ip> -g your_gpx_file.gpx` on your computer

with web visualization ...

1. run MockGeoFix on your phone
2. run `helper_scripts/run_sim.py -i <your_phone_ip> -g your_gpx_file.gpx -I 127.0.0.1 -P 8080 -S 20`
  - NOTE: -S 20 will simulate 20km/h speed
3. open http://127.0.0.1:8080 in your web browser

`helper_scripts/run_sim.py --help` for more info

NOTE: You can use http://www.gpsvisualizer.com/convert_input to convert directions from google
maps to GPX (just copy the URL from google maps to the 'Or provide the URL of a file on the Web: '
field in gpsvisualizer converter).
