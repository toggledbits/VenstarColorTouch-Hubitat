# Changelog for Venstar ColorTouch Thermostat Hubitat Driver

## 23022

* Provide complete declarations of all attributes and commands in header.
* Detect and report thermostat reported operating state 3 (equipment delay).
* Add `lastUpdateEpoch` attribute (Epoch timestamp to match `lastUpdate`).
* Fix construction of request parameters so that their values are URL-encoded (request keys are not expected to include characters that would require escaping, so are not included in this change).
* Merge pull request from arktronic for T3900 (use HTTP POST for passing parameters).

## 22208

* Change method argument to runIn*(), other devs have reported that use of non-string (i.e. method reference) can lead to inconsistent results, although another post refutes this. But the docs do say use a string. Ref: https://community.hubitat.com/t/latest-release-possible-runin-issues-see-example/81060 and https://community.hubitat.com/t/runin-not-scheduling-event/35005/7
* Tighten handling of HTTP response exceptions for more clear messages when they occur (e.g. differentiate HTTP errors vs response content parse errors more clearly).
kk
## 22138

* Initialize capability added for parity with function (but doesn't do much at this point).

## 22104

* The T59x0 apparently reports humidity in odd fields (not consistent with API of later 7000/8000 series). Add a preference for thermostat model and use that to help unfork this inconsistency.

## 22101

* Fix repeating message in log when thermostat does not report "activestage".
* Correct handling for polling interval=0
* Refresh command works even if polling is disabled (i.e. you can turn polling off but still update the device "manually").

## 21334

* Trim input strings for setThermostatMode and setThermostatFanMode; the Hubitat dashboard seems to add spaces to some values.

## 21270

* Add config to optionally force device units (default: use hub's configured unit)
* Improve error handling for certain exceptions during request
* Temperature unit default for T2000
* May support T5800/5900, but I can't test since I don't have one.

## 21268

* Clean up HTTP Digest authentication; add state variable indicator for which is used (debug assist);

## 21265

* Initial release/publication under MIT License.
