# Hubitat-sinope-GT125
Sinopé GT125 direct access for Hubitat platform

# What can I do with this driver?
Here is a list of currently supported devices:

Thermostats

Sinopé TH1120RF-3000 Line voltage thermostat<br>
Sinopé TH1120RF-4000 Line voltage thermostat<br>
Sinopé TH1121RF-3000 Thermostat for public areas<br>
Sinopé TH1121RF-4000 Thermostat for public areas<br>
Sinopé TH1300RF Floor heating thermostat<br>
Sinopé TH1400RF Low voltage thermostat<br>
Sinopé TH1500RF Double-pole thermostat<br>
*Ouellet OTH2750-GT Line voltage thermostat<br>
*Ouellet OTH3600-GA-GT Floor heating thermostat<br>

# Installation
You will need to add two drivers (parent and child) in the hubitat driver page. You can do that easily by using the Hubitat Package Manager. You will also need the ID that's on the back of the GT125 hub. Follow the steps bellow:

1) Start by pressing the "Add Virtual Device" on the hubitat devices page and select the parent driver (Sinope Neviweb Hub). After that set the IP, Port (you can leave the default value) and the ID of your sinope hub on the respective fields in the parent driver. 
2) Press the "Get APIKey" button and press the "Web" button on the GT125 hub. You will need to do this just once and the API key will be saved in the parent driver. 
3) The next step is to add your thermostats. You can check the logs the step-by-step (make sure the log level is at least set as "info"). Do this by pressing the "Add Thermostat" button and pressing the two buttons on the thermostat itself at the same time. That will create the child thermostat and you can set the name you want for it in its settings. Keep doing this process until you add all the thermostats. I left the configuration for the default pool interval at 10 minutes. I have seven thermostats and this is the setting that worked best for me. When I tried shorter times, my network would be overflowed with messages.
4) Change the name of the new thermostat device that was created. It should be under the "Sinope Neviweb Hub" device you created in the first step. Keep doing the third and forth steps to add all your thermostats.

# Note
I did this on my free time (this is my first driver) and for sure there are bugs so use it on your own risk. Some of the features done by claudegel are not yet supported, such as setting the outdoor temperature. For now this settings is coming directly from sinope.

# Credits
This was all possible due to the work done by https://github.com/claudegel/sinope-gt125 to support HA.
And of course, the Hubitat dev community!
