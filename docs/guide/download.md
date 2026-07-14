# Download

TaiXiu 3.0 is currently a pre-release fork. Build the latest revision from source while release validation is in progress.

```bash
git clone https://github.com/Alexteens24/TaiXiu.git
cd TaiXiu
./gradlew clean build
```

The server plugin is generated at:

```text
taixiu-plugin/build/libs/TaiXiu-3.0.0.jar
```

::: tip Reproducible build
The repository includes Gradle Wrapper 9.6.1. You need Java 21, but you do not need to install Gradle globally.
:::

::: warning Production use
Do not deploy solely because CI is green. Complete the [runtime validation checklist](/runtime-test-checklist) against your Paper/Folia build, bridge, and economy provider.
:::
