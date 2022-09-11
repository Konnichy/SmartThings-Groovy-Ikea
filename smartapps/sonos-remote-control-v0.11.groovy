/**
 *  Copyright 2019 Juha Tanskanen
 *  Copyright 2021 Konnichy
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Remote audio volume control
 *
 *  Version Author              Note
 *  0.9     Juha Tanskanen      Initial release
 *  0.10    Juha Tanskanen      Updated to match changes in SYMFONISK device handler
 *  0.11    Juha Tanskanen      Support for multiple Sonos players (Master/Slaves concept)
 *  0.12    Konnichy            Support for non-Sonos devices, including those only supporting volume up/volume down commands
 *
 */

definition(
    name: "Remote audio volume control",
    namespace: "smartthings",
    author: "Juha Tanskanen, Konnichy",
    description: "Control your audio system with Ikea SYMFONISK Sound remote",
    category: "SmartThings Internal",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience%402x.png"
)

preferences {
    section("Select your devices") {
        input "buttonDevice", "capability.button", title: "Control", description: "The device which controls the media (play/pause/next/previous)", multiple: false, required: true
        input "levelDevice", "capability.switchLevel", title: "Volume control", description: "The device which controls the volume", multiple: false, required: true
        input "audioDevice", "capability.audioVolume", title: "Controlled device", description: "The audio device whose volume is controlled", multiple: false, required: true
        input "audioDeviceSlaves", "capability.audioVolume", title: "Controlled slaves", description: "Slave audio devices (if any, e.g. Sonos slaves)", multiple: true, required: false
        input "directVolumeSupported", "bool", title: "Direct volume value supported?", description: "Disable this parameter if the audio device only supports volume up and volume down commands", defaultValue: true, required: true
    }
}

def installed() {
    log.debug "Installed with settings: ${settings}"

    initialize()
}

def updated() {
    log.debug "Updated with settings: ${settings}"

    unsubscribe()
    initialize()
}

def initialize() {
    subscribe(buttonDevice, "button", buttonEvent)
    subscribe(levelDevice, "level", buttonEvent)
    subscribe(levelDevice, "levelChange", buttonEvent)
}

def buttonEvent(evt){
    def device = evt.name
    def value = evt.value
    log.debug "buttonEvent: $evt.name = $evt.value ($evt.data)"
    log.debug "command: $device, value: $value"

    def recentEvents = buttonDevice.eventsSince(new Date(now() - 2000)).findAll{it.value == evt.value && it.data == evt.data}
    log.debug "Found ${recentEvents.size()?:0} events in past 2 seconds"

    if(recentEvents.size <= 1){
        handleCommand(device, value)
    } else {
        log.debug "Found recent button press events for $device with value $value"
    }
}

def handleCommand(command, value) {
    if (command == "button") {
        log.debug "Handle $value"
        switch (value) {
            case "pushed":
                log.debug "Button clicked - Play/Pause"
                def currentStatus = audioDevice.currentValue("playbackStatus")
                log.debug "Current status: $currentStatus"
                if (currentStatus == "playing") {
                    audioDevice.pause()
                    audioDeviceSlaves*.pause()
                } else {
                    audioDevice.play()
                    audioDevice*.play()
                }
                break
            case "pushed_2x":
                log.debug "Button clicked twice - Next Track"
                audioDevice.nextTrack()
                audioDevice*.nextTrack()
                break
            case "pushed_3x":
                log.debug "Button clicked treble - Previous Track"
                audioDevice.previousTrack()
                audioDevice*.previousTrack()
                break
        }
    } else if (command == "levelChange" && $value != 0) {
        log.debug "Handling level change $value"
        def change = Integer.parseInt(value)
        // Convert the controller's volume change detected to a number of clicks
        def repeat = Math.round(Math.abs(change) / 5)
        if (change > 0) {
            audioDevice.volumeUp(repeat)
        } else {
            audioDevice.volumeDown(repeat)
        }
    } else {
        log.debug "command=$command"
        if (!directVolumeSupported) {
            log.debug "Ignored (the audio device doesn't support direct volume values)"
            return
        }
        Integer currentVolume = audioDevice.currentValue("volume")
        Integer change = value.toInteger() - currentVolume
        Integer newVolume = currentVolume + change

        // This is a workaround to prevent accidental "too big volume change" if the device
        // was controlled through some other device
        if (Math.abs(change) > 20) {
            if (Math.abs(change) > 50) {
                change /= 4
            } else if (Math.abs(change) > 25) {
                change /= 2
            }
            newVolume = currentVolume + change
            levelDevice.setLevel(newVolume)
        }

        log.debug "Set volume $currentVolume -> $newVolume"
        audioDevice.setVolume(newVolume)
        audioDeviceSlaves*.setVolume(newVolume)
    }
}
