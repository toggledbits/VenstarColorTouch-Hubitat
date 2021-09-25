# Venstar ColorTouch Thermostat Driver for Hubitat Elevation

<small>By [toggledbits](https://www.toggledbits.com/)</small>

This driver for Hubitat Elevation works with [Venstar ColorTouch Thermostats](https://venstar.com/thermostats/colortouch/) that have Wi-Fi support and local API. It uses the local API only, and does not use the Skyport cloud to access the thermostat. The following thermostats are known to work:

* T7850
* T7900

I suspect that the commercial versions of these models (the T8850 and T8900) will also work.

This is a new, completely rewritten driver offered under the very liberal MIT license. The previous maintainer "withdrew" his version of the driver &mdash; a questionable concept legally, but regardless, I'm happy to start over and license a new work under terms more favorable to the Hubitat community. For clarity, as this driver is now released to the public as Open Source under the MIT License, under U.S. law (the governing law for this author), the "performance" has been started and thus the license and your right to use this driver can never be withdrawn. Enjoy!

Other features of this new version include:

* Support for standard capabilities: Thermostat, TemperatureMeasurement, RelativeHumidityMeasurement, PresenceSensor;
* Extended (driver-specific) support for humidification/dehumidification (T7900/8900);
* Control of effective state of thermostat's program schedule;
* Control of the selection of *Home* or *Away* (e.g. vacation) settings selection;
* HTTPS protocol with optional HTTP user authentication for improved security on your LAN (HTTP Basic and Digest authentication supported).

Future:

* Child devices for additional sensors;
* Retrieval/storage/availability of thermostat's collected runtime stats.

## About the Author

Before diving in, I'll say that I'm relatively new to Hubitat and this is my first driver for the platform. I am and have been a prolific developer of plugins for the [Vera](https://community.ezlo.com/) hub (I'm known as rigpapa in that community and have 14 plugins widely used), and I am the author of [Multi-hub Reactor](https://reactor.toggledbits.com/docs/), a cross-hub rules engine that serves as an alternative to Node-Red and the rules engines of Vera, Home Assistant, Hubitat, and eZLO hubs. So while I have some experience in HA, my work here in the Hubitat community is relatively new, and I'd appreciate any guidance as I learn the platform and groovy.

## Installation

### Configuring Your Venstar ColorTouch Thermostat

**IMPORTANT!** Be sure to perform these configuration steps before installing the driver. It will make your life easier.

The following must be configured on your thermostat:

1. Go to the "Wi-Fi Status" page (under the "Wi-Fi" menu), and confirm that you are connected to the network and have good signal strength--if not, configure or troubleshoot that before continuing (see your thermostat's documentation);
1. Under the "Wi-Fi" menu, turn on the "Local API";
1. Under the "Wi-Fi" menu, the "API Protocol" may be either HTTP or HTTPS, but use HTTP for now;
1. Assign your thermostat a DHCP-reserved IP address at your DHCP server, or a static IP address in the thermostat interface.
1. Power-cycle the thermostat.

Once you have completed these steps, confirm that your thermostat's API is reachable by opening the thermostat in a browser. For example, if your thermostat's assigned IP (from step 2) is 192.168.9.100, then you would open `http://192.168.9.100/`. You should get a short JSON response containing the string "api_ver" (it does not matter what version of the API your thermostat uses).

### Installation from GitHub ###

1. Log in to your hub.
2. Go to the *Drivers Code* page.
3. If *Venstar ColorTouch Thermostat* is listed there, click on that entry to go into its code. Otherwise, click the *New Driver* button.
4. Click the *Import* button.
5. Copy-paste this URL:

        https://raw.githubusercontent.com/toggledbits/VenstarColorTouch-Hubitat/main/drivers/VenstarColorTouch.groovy

6. Click the *Import* button. Your driver will be installed/updated.

You can then create a device for the thermostat using the driver.

## Configuration

Configuration should be pretty straight-forward. You need to supply the IP address of the thermostat and select the API protocol (HTTP or HTTPS), at a minimum, and of course, these need to match the configuration of the thermostat itself.

If you are using HTTPS, the thermostat will allow you to configure an authentication username and password. If you choose this option, you will need to supply that username and password to the driver in the fields indicated. Otherwise, leave these fields blank. See *Improving Security* below.

The *Polling Interval* determines how often the driver queries the thermostat for new data. The Venstar local API does not "notify" the driver/hub of changes, so the driver must poll. This creates a delay in response to manual changes at the thermostat up to the length of the polling interval. The protocol is lightweight, so a polling interval of 60 seconds (the default) should present no significant load to either the hub or thermostat. Intervals of less than 15 seconds are not recommended, however.

## Operation

The driver provides the following standard Hubitat capabilities:

* Thermostat
* TemperatureMeasurement
* RelativeHumidityMeasurement (not used with T7850/8850)
* PresenceSensor
* Refresh
* Sensor
* Actuator

The following driver-specific attributes are defined:

* `name` &mdash; [string] the name provided by the thermostat; this is informational only and is not otherwise used by the driver. It may assist you during setup in differentiating one of many thermostats in your home.
* `online` &mdash; [boolean] the current communications state of the thermostat; if the driver cannot communicate with the thermostat, this attribute will be *false*.
* `program` &mdash; [enum `running`, `stopped`, `unknown`] state of the thermostat's programmed schedule.
* `schedulePeriod` &mdash; [enum `morning`, `day`, `evening`, `night`, `n/a`, `unknown`] the current effective period of the thermostat's programmed schedule; will be `n/a` if the program is stopped.
* `thermostatFanOperatingState` &mdash; [enum `off`, `on`] operating state of the fan/blower.
* `humdificationSetpoint` and `dehumidificationSetpoint` &mdash; [number] for the T7900/8900 models that support humidity, these are the setpoints for humidification and dehumdification, respectively. The availability of these values may be further conditioned by the configuration of the thermostat.
* `override` &mdash; [off/on/unknown] used on commercial models only to indicate a forced temporary change in occupancy status.
* `lastupdate` &mdash; [date] date of when the last successful update from the thermostat was received.

The following driver-specific commands are defined:

* `home` &mdash; changes the thermostat's Home/Away program mode to "home".
* `away` &mdash; changes the thermostat's Home/Away program mode to "away".
* `setHumidicationSetpoint` and `setDehumidificationSetpoint` &mdash; for models T7900/8900 that are configured for (de)humidification control, this commands take a single (integer 0-99) parameter to modify the setpoint.
* `setProgram` &mdash; sets the thermostat's program state; the single parameter may be `run` to make the thermostat's programmed schedule effective, or `stop` to stop the programmed schedule (indefinitely).
* `programRun` and `programStop` &mdash; these are parameter-less aliases for `setProgram "run"` and `setProgram "stop"`, respectively.
* `setPollingInterval` &mdash; take a single (integer >= 0) parameter to *temporarily* override the polling interval of the thermostat; units are seconds. Setting 0 disables polling. The polling interval returns to the configured state when the hub is rebooted.

## Improving Security -- Basic Auth and HTTPS ##

With respect to security, there are basically three generations of Venstar ColorTouch thermostats: those that support no security whatsoever (very outdated firmware), those that support HTTP Basic Authentication and HTTPS (not the latest firmware, but improved), and those that support HTTP Digest Authentication and HTTPS (not perfect, but better than Basic). Users concerned with vulnerability of their IoT devices will want to upgrade their thermostats to the latest firmware and use HTTP Digest Authentication.

1. In the thermostat's Wi-Fi menu, set the API Protocol to HTTPS; some versions of firmware will require you to (temporarily) disable the local API to change these settings.
1. Set the HTTP Authentication username and password.
1. Power-cycle the thermostat or restart it using the Installer menu.
1. Open the device in the Hubitat UI.
1. Set the *Local API Protocol* to `https` and provide the username and password you configured at the thermostat in the device configuration fields *Auth User* and *Auth Password*
1. When you click the *Save Preferences* button, the driver will restart and requery the thermostat, so you should observe the `lastUpdate` field change (or if you miss the change, it should be seconds from the current time). You can also check the logs for any errors from the driver. Always try rebooting the thermostat if you have problems after modifying its authentication configuration. And of course, make sure to double-check your username and password configuration at both the thermostat and driver; they must agree in every respect.

> NOTE: Although the thermostat refers to these in its UI as "Basic Auth", they could use either Basic or Digest authentication as determined by the thermostat's firmware. The driver will use whichever the thermostat supports/offers.

## Reporting Bugs/Enhancement Requests ##

Support questions, bug reports and enhancement requests are welcome! There are two ways to share them:

1. I have a strong preference for the use of the ["Issues" section](https://github.com/toggledbits/VenstarColorTouch-Hubitat/issues) of the Github repository for reporting of (actual or suspected) bugs.
1. I'll be happy to answer questions directed to me (@toggledbits) in posts or PMs on the [Hubitat Community Forums](https://community.hubitat.com/).

<hr>

Copyright 2021 Patrick H. Rigney (toggledbits), All Rights Reserved