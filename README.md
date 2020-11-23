# Hubitat-sinope-GT125
Sinopé GT125 direct access for Hubitat platform

# What can I do with this driver?
This driver let you control these sinope thermostats:
thermostat TH1120RF 3000W and 4000W
thermostat TH1300RF 3600W floor, TH1500RF double pole thermostat
thermostat TH1400RF low voltage

# Installation
You will need to add two drivers (parent and child) in the hubitat interface. You will also need the ID that's on the back of the GT125 hub. Start by pressing the "Add Virtual Device" on the hubitat devices page and select the parent driver (Sinope Neviweb Hub). After that set the IP, Port (you can leave the default value) and the ID of your sinope hub on the respective fields in the parent driver. After that press the "Get APIKey" button and press the "Web" button on the GT125 hub. You will need to do this just once and the API key will be saved in the parent driver. The next step is to add your thermostats. Do this by pressing the "Add Thermostat" button and pressing the two buttons on the thermostat itself at the same time. That will create the child thermostat and you can set the name you want for it in its settings. Keep doing this process until you add all the thermostats. I left the configuration for the default pool interval at 10 minutes. I have seven thermostats and this is the setting that worked best for me. When I tried shorter times, my network would be overflowed with messages.

# Note
I did this on my free time and for sure there are bugs so use it on your own risk. Some of the features done by claudegel are not yet supported, such as setting the outdoor temperature. For now this settings is coming directly from sinope.

# Credits
This was all possible due to the work done by https://github.com/claudegel/sinope-gt125 to support HA.
And of course, the Hubitat dev community!
