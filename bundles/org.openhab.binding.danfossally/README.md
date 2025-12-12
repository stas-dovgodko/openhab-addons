# Danfoss Ally Binding

This binding integrates Danfoss Ally™ smart thermostats into openHAB, allowing you to monitor and control your heating system through the Danfoss cloud API.

## Supported Things

This binding supports the following thing types:

| Thing Type | Description |
|------------|-------------|
| `account` | Bridge representing your Danfoss Ally cloud account (OAuth2 connection) |
| `thermostat` | Danfoss Ally radiator thermostat device |

### Supported Devices

The binding currently supports the following Danfoss Ally thermostat models:

- **Danfoss Icon2 RT**

**Note:** Only the Danfoss Icon2 RT thermostat is currently supported. If you have other Danfoss Ally device types that you would like to see supported, please enable INFO logging for `org.openhab.binding.danfossally.internal` and provide the log output to the binding author. This will help identify and add support for additional device types.

To enable INFO logging, add the following to your `log4j2.xml` configuration:

```xml
<Logger level="INFO" name="org.openhab.binding.danfossally.internal"/>
```

Or use the Karaf console:

```
log:set INFO org.openhab.binding.danfossally.internal
```

## Discovery

Once you have configured the bridge with your API credentials, the binding will automatically discover all Danfoss Ally thermostats associated with your account. Discovery runs in the background and will detect new devices as they are added to your Danfoss Ally system.

**Finding Device IDs:**
Device IDs are automatically discovered and assigned when you add devices from the inbox. If you need to manually configure a device or find its ID:

1. **Using Discovery (Recommended):** Configure the bridge and check the inbox (Settings → Things → Inbox). Discovered devices will show their IDs in the thing properties.

2. **Using Logs:** Enable INFO or DEBUG logging for `org.openhab.binding.danfossally.internal` and check the openHAB logs. Device IDs will be logged during the discovery process.

## Bridge Configuration

The `account` bridge requires OAuth2 credentials from the Danfoss Developer Portal.

### Obtaining API Credentials

1. Visit the [Danfoss Developer Portal](https://developer.danfoss.com/)
2. Create an account or log in
3. Register a new application to obtain your Client ID and Client Secret
4. Use these credentials to configure the bridge in openHAB

### Bridge Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `clientId` | text | yes | - | OAuth2 Client ID from Danfoss Developer Portal |
| `clientSecret` | text | yes | - | OAuth2 Client Secret from Danfoss Developer Portal |
| `pollingInterval` | integer | no | 60 | Interval in seconds for refreshing device data (minimum: 10) |

### Bridge Configuration Example

**Text Configuration:**

```java
Bridge danfossally:account:myaccount "Danfoss Ally Account" [ 
    clientId="your-client-id-here",
    clientSecret="your-client-secret-here",
    pollingInterval=60
]
```

**UI Configuration:**

Navigate to Settings → Things → Add Thing → Danfoss Ally Binding → Danfoss Ally Account and enter your credentials.

## Thing Configuration

### Thermostat Thing

Each thermostat requires only the device ID to be configured. This is typically discovered automatically, but can also be obtained from the logs or by running discovery.

#### Thing Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `deviceId` | text | yes | Unique identifier of the Danfoss Ally device (found via discovery or in logs) |

#### Thing Configuration Example

**Text Configuration:**

```java
Bridge danfossally:account:myaccount "Danfoss Ally Account" [ 
    clientId="your-client-id",
    clientSecret="your-client-secret"
] {
    Thing thermostat livingroom "Living Room Thermostat" [ 
        deviceId="bfe54f23b86ecce856y1vy"
    ]
}
```

**UI Configuration:**

After configuring the bridge, discovered thermostats will appear in your inbox. Accept them or manually add a thermostat thing by specifying the device ID (obtained from discovery or logs).

## Channels

### Thermostat Channels

| Channel ID | Item Type | Read/Write | Description |
|------------|-----------|------------|-------------|
| `online` | Contact | R | Device online status (OPEN = online, CLOSED = offline) |
| `sub` | Contact | R | Subscription/API connectivity status (OPEN = connected) |
| `active` | Contact | R | Heating output active status (OPEN = heating) |
| `activeTime` | DateTime | R | Last time the device was active |
| `createTime` | DateTime | R | Device creation timestamp |
| `updateTime` | DateTime | R | Last device update timestamp |
| `tempCurrent` | Number:Temperature | R | Current room temperature |
| `tempSet` | Number:Temperature | R | Reported target temperature from device |
| `setpoint` | Number:Temperature | RW | Effective temperature setpoint (write to change) |
| `delta` | Number:Temperature | R | Temperature difference (setpoint - current) |
| `mode` | String | RW | Operating mode |

### Operating Modes

The `mode` channel supports the following values:

- `manual` - Manual temperature control
- `at_home` - At Home mode (schedule-based)
- `leaving_home` - Away/Leaving Home mode
- `holiday` - Holiday mode
- `pause` - Pause mode

## Full Example

### Thing Configuration

**danfossally.things:**

```java
Bridge danfossally:account:home "My Danfoss Account" [ 
    clientId="nikHgATPlADdhYoobAwYbsH87kRxvERp6dFXOJXqAtFce65y",
    clientSecret="PIJxYx0NsHBNlJuaVZJAujF4a20ZezECCcRPAC9ZfA27xjbMfh8dLlPNFnGpnsy8",
    pollingInterval=60
] {
    Thing thermostat livingroom "Living Room" [ deviceId="bfe54f23b86ecce856y1vy" ]
    Thing thermostat bedroom "Bedroom" [ deviceId="bf4b3890a2fe8614a1vgac" ]
    Thing thermostat office "Office" [ deviceId="bfd3a93bc8049884856kxg" ]
}
```

### Item Configuration

**danfossally.items:**

```java
// Living Room Thermostat
Contact      LivingRoom_Online         "Online [%s]"                     { channel="danfossally:thermostat:home:livingroom:online" }
Contact      LivingRoom_HeatingActive  "Heating [MAP(heating.map):%s]"   { channel="danfossally:thermostat:home:livingroom:active" }
Number:Temperature LivingRoom_Temperature "Temperature [%.1f °C]"        { channel="danfossally:thermostat:home:livingroom:tempCurrent" }
Number:Temperature LivingRoom_Setpoint    "Target [%.1f °C]"             { channel="danfossally:thermostat:home:livingroom:setpoint" }
Number:Temperature LivingRoom_Delta       "Delta [%.1f °C]"              { channel="danfossally:thermostat:home:livingroom:delta" }
String       LivingRoom_Mode          "Mode [%s]"                       { channel="danfossally:thermostat:home:livingroom:mode" }
DateTime     LivingRoom_Updated       "Updated [%1$tY-%1$tm-%1$td %1$tH:%1$tM]" { channel="danfossally:thermostat:home:livingroom:updateTime" }

// Bedroom Thermostat
Contact      Bedroom_Online            "Online [%s]"                     { channel="danfossally:thermostat:home:bedroom:online" }
Number:Temperature Bedroom_Temperature    "Temperature [%.1f °C]"        { channel="danfossally:thermostat:home:bedroom:tempCurrent" }
Number:Temperature Bedroom_Setpoint       "Target [%.1f °C]"             { channel="danfossally:thermostat:home:bedroom:setpoint" }
String       Bedroom_Mode             "Mode [%s]"                       { channel="danfossally:thermostat:home:bedroom:mode" }
```

### Sitemap Configuration

**danfossally.sitemap:**

```perl
sitemap danfossally label="Heating Control" {
    Frame label="Living Room" {
        Text item=LivingRoom_Temperature
        Setpoint item=LivingRoom_Setpoint minValue=15 maxValue=28 step=0.5
        Text item=LivingRoom_Delta
        Selection item=LivingRoom_Mode mappings=[manual="Manual", at_home="At Home", leaving_home="Away", pause="Pause"]
        Text item=LivingRoom_HeatingActive
        Text item=LivingRoom_Updated
    }
    
    Frame label="Bedroom" {
        Text item=Bedroom_Temperature
        Setpoint item=Bedroom_Setpoint minValue=15 maxValue=28 step=0.5
        Selection item=Bedroom_Mode mappings=[manual="Manual", at_home="At Home", leaving_home="Away"]
    }
}
```

### Rule Examples

**danfossally.rules:**

```java
rule "Set Night Temperature"
when
    Time cron "0 0 22 * * ?" // 10 PM
then
    Bedroom_Setpoint.sendCommand(18.0)
    Bedroom_Mode.sendCommand("manual")
end

rule "Morning Warmup"
when
    Time cron "0 0 6 * * ?" // 6 AM
then
    Bedroom_Setpoint.sendCommand(21.0)
    LivingRoom_Setpoint.sendCommand(22.0)
end

rule "Away Mode"
when
    Item PresenceSwitch changed to OFF
then
    LivingRoom_Mode.sendCommand("leaving_home")
    Bedroom_Mode.sendCommand("leaving_home")
end

rule "Alert on Device Offline"
when
    Item LivingRoom_Online changed to CLOSED
then
    sendNotification("email@example.com", "Living Room thermostat is offline")
end
```

### Transform Configuration

**heating.map:**

```properties
OPEN=Active
CLOSED=Inactive
```

## Notes

- The binding uses the official Danfoss Ally cloud API, which requires an internet connection
- Temperature values from the API are in tenths of degrees Celsius and automatically converted by the binding
- The `setpoint` channel represents the effective target temperature based on the current mode
- When changing the `setpoint`, the binding automatically switches the device to manual mode
- OAuth2 tokens are cached and automatically refreshed as needed
- The minimum polling interval is 10 seconds to avoid API rate limiting
- Device IDs can be found through automatic discovery or by checking the openHAB logs

## Troubleshooting

### Bridge Shows Offline

- Verify your Client ID and Client Secret are correct
- Check your internet connection
- Ensure your API credentials have not expired
- Check the openHAB logs for authentication errors

### Thermostat Not Discovered

- Wait up to 60 seconds for the initial discovery scan
- Manually trigger discovery from the UI (Settings → Things → Scan for Things)
- Verify the thermostat is online in the Danfoss Ally mobile app
- Check that the device type is supported (currently only Danfoss Icon2 RT)
- If you have other device types, enable INFO logging to help add support (see below)

### Finding Device IDs

If you need to manually configure devices or find device IDs:

1. **Automatic Discovery (Easiest):** After configuring the bridge, go to Settings → Things → Inbox. Discovered devices will appear with their device IDs visible in the thing properties.

2. **Check Logs:** Enable INFO or DEBUG logging and check `openhab.log`:

   ```
   log:set INFO org.openhab.binding.danfossally.internal
   ```

   Device IDs will be logged during discovery and polling cycles.

### Commands Not Working

- Ensure the thermostat is online (check `online` channel)
- Verify the bridge is ONLINE
- Check openHAB logs for API communication errors
- Some modes may restrict manual temperature changes

### Adding Support for Other Device Types

If you have Danfoss Ally devices that are not automatically discovered or supported, you can help add support by providing device information:

1. Enable INFO logging for the binding:
   
   **Via Karaf console:**

   ```
   log:set INFO org.openhab.binding.danfossally.internal
   ```
   
   **Via log4j2.xml:**

   ```xml
   <Logger level="INFO" name="org.openhab.binding.danfossally.internal"/>
   ```

2. Restart openHAB or wait for the next polling cycle

3. Check the logs for entries showing your device information (look for device_type and other attributes)

4. Share the log output with the binding author via GitHub issues or the openHAB community forum

This information will help identify the device type identifiers and capabilities needed to add support for additional Danfoss Ally devices.

## API Documentation

For more information about the Danfoss Ally API, visit the [Danfoss Developer Portal](https://developer.danfoss.com/).
