import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pag/pag.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const channel = MethodChannel('flutter_pag_plugin');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;

  tearDown(() {
    messenger.setMockMethodCallHandler(channel, null);
  });

  testWidgets('releases a texture returned after the widget is disposed',
      (tester) async {
    final initialization = Completer<Map<String, Object>>();
    final releasedTextureIds = <int>[];

    messenger.setMockMethodCallHandler(channel, (call) async {
      switch (call.method) {
        case 'initPag':
          return initialization.future;
        case 'release':
          releasedTextureIds.add(
            (call.arguments as Map<Object?, Object?>)['textureId']! as int,
          );
          return null;
      }
      return null;
    });

    await tester.pumpWidget(
      MaterialApp(home: PAGView.asset('test.pag')),
    );
    await tester.pumpWidget(const MaterialApp(home: SizedBox()));

    initialization.complete({
      'textureId': 41,
      'width': 100.0,
      'height': 100.0,
    });
    await tester.pump();

    expect(releasedTextureIds, [41]);
  });

  testWidgets('release is idempotent', (tester) async {
    final key = GlobalKey<PAGViewState>();
    final releasedTextureIds = <int>[];

    messenger.setMockMethodCallHandler(channel, (call) async {
      switch (call.method) {
        case 'initPag':
          return <String, Object>{
            'textureId': 42,
            'width': 100.0,
            'height': 100.0,
          };
        case 'release':
          releasedTextureIds.add(
            (call.arguments as Map<Object?, Object?>)['textureId']! as int,
          );
          return null;
      }
      return null;
    });

    await tester.pumpWidget(
      MaterialApp(home: PAGView.asset('test.pag', key: key)),
    );
    await tester.pump();

    await key.currentState!.release();
    await key.currentState!.release();
    await tester.pumpWidget(const MaterialApp(home: SizedBox()));

    expect(releasedTextureIds, [42]);
  });
}
