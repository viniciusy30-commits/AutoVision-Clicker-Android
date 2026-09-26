# AutoVision Clicker

Aplicativo Android em Kotlin que captura a tela com `MediaProjection`, mantém a
captura em um serviço foreground e oferece um Vision Lab para testar detecção de
objetos, cor, forma, template matching e ORB com OpenCV.

## Estrutura

- `app/src/main/java/com/autovision/clicker/capture` — captura contínua da tela.
- `app/src/main/java/com/autovision/clicker/vision` — engines de reconhecimento
  visual e matching.
- `app/src/main/java/com/autovision/clicker/ui` — dashboard e Vision Lab em
  Jetpack Compose.
- `app/src/main/AndroidManifest.xml` — permissões e declaração do serviço de
  captura.

## Executar

Abra `android/autovision-clicker` no Android Studio e execute o módulo `app`.
Também é possível usar o wrapper:

```bash
./gradlew build
```

O primeiro uso da captura pede a autorização do Android para compartilhar a
tela. O Vision Lab funciona de forma independente: carregue uma imagem da
galeria para visualizar os contornos e a confiança dos objetos detectados.

## Observações

- O módulo usa OpenCV `4.11.0` via Maven Central.
- O projeto exige Java 17, compile/target SDK 36 e Android mínimo API 29.
- A automação por acessibilidade e o overlay estão apenas preparados pelos
  pacotes-base do ZIP; esta etapa implementa captura, dashboard e reconhecimento.