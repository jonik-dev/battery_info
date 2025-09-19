import 'package:flutter/material.dart';
import 'package:battery_info/battery_info_plugin.dart';
import 'package:battery_info/model/android_battery_info.dart';
import 'package:battery_info/enums/charging_status.dart';

void main() {
  runApp(MyApp());
}

class MyApp extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Battery Info plugin example'),
        ),
        body: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              FutureBuilder<AndroidBatteryInfo?>(
                  future: BatteryInfoPlugin().androidBatteryInfo,
                  builder: (context, snapshot) {
                    if (snapshot.hasData) {
                      return Text('Battery Health: ${snapshot.data?.health?.toUpperCase()}');
                    }
                    return CircularProgressIndicator();
                  }),
              SizedBox(
                height: 20,
              ),
              StreamBuilder<AndroidBatteryInfo?>(
                  stream: BatteryInfoPlugin().androidBatteryInfoStream,
                  builder: (context, snapshot) {
                    if (snapshot.hasData) {
                      final remainingbatteryCapacityMilliAh = snapshot.data?.remainingbatteryCapacityMilliAh;
                      final remainingEnergyNWh = snapshot.data?.remainingEnergyNWh;
                      return Column(
                        children: [
                          Text("Voltage: ${(snapshot.data?.voltageMilliV)} mV"),
                          SizedBox(
                            height: 20,
                          ),
                          Text("Charging status: ${(snapshot.data?.chargingStatus.toString().split(".")[1])}"),
                          SizedBox(
                            height: 20,
                          ),
                          Text("Battery Level: ${(snapshot.data?.batteryLevelPercentage)} %"),
                          SizedBox(
                            height: 20,
                          ),
                          if (remainingbatteryCapacityMilliAh != null)
                            Text("Remaining battery Capacity: ${(remainingbatteryCapacityMilliAh / 1000)} mAh"),
                          SizedBox(
                            height: 20,
                          ),
                          Text("Technology: ${(snapshot.data?.technology)} "),
                          SizedBox(
                            height: 20,
                          ),
                          Text("Battery present: ${snapshot.data?.present == true ? "Yes" : "False"} "),
                          SizedBox(
                            height: 20,
                          ),
                          Text("maximumBatteryLevel: ${(snapshot.data?.maximumBatteryLevel)} "),
                          SizedBox(
                            height: 20,
                          ),
                          if (remainingEnergyNWh != null)
                            Text("Remaining energy: ${-(remainingEnergyNWh * 1.0E-9)} Watt-hours,"),
                          SizedBox(
                            height: 20,
                          ),
                          _getChargeTime(snapshot.data!),
                        ],
                      );
                    }
                    return CircularProgressIndicator();
                  })
            ],
          ),
        ),
      ),
    );
  }

  Widget _getChargeTime(AndroidBatteryInfo data) {
    if (data.chargingStatus == ChargingStatus.Charging) {
      final chargeTimeRemainingMs = data.chargeTimeRemainingMs;
      return chargeTimeRemainingMs != null
          ? Text("Charge time remaining: ${(chargeTimeRemainingMs / 1000 / 60).truncate()} minutes")
          : Text("Calculating charge time remaining");
    }
    return Text("Battery is full or not connected to a power source");
  }
}
