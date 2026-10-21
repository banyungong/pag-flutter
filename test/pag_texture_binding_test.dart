import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pag/pag.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('flutter_pag_plugin');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;

  tearDown(() => messenger.setMockMethodCallHandler(channel, null));

  test('all constructors enable the binding guard by default', () {
    final views = [
      PAGView.asset('test.pag'),
      PAGView.filePath('/test.pag'),
      PAGView.network('https://example.com/test.pag'),
      // ignore: deprecated_member_use_from_same_package
      PAGView.bytes(Uint8List(0)),
    ];
    for (final view in views) {
      expect(view.restoreTextureBinding, isTrue);
    }
  });

  for (final enabled in [true, false]) {
    testWidgets(
        'sends restoreTextureBinding=$enabled without changing playback',
        (tester) async {
      Map<Object?, Object?>? initialization;
      messenger.setMockMethodCallHandler(channel, (call) async {
        if (call.method == 'initPag') {
          initialization = call.arguments as Map<Object?, Object?>;
          return {'textureId': 10, 'width': 100.0, 'height': 100.0};
        }
        return null;
      });

      await tester.pumpWidget(MaterialApp(
          home: PAGView.asset(
        'test.pag',
        restoreTextureBinding: enabled,
        autoPlay: true,
        repeatCount: PAGView.REPEAT_COUNT_LOOP,
      )));
      await tester.pump();

      expect(initialization!['restoreTextureBinding'], enabled);
      expect(initialization!['autoPlay'], isTrue);
      expect(initialization!['repeatCount'], PAGView.REPEAT_COUNT_LOOP);
      expect(find.byType(Texture), findsOneWidget);
      await tester.pumpWidget(const SizedBox());
      await tester.pump();
    });
  }
}
