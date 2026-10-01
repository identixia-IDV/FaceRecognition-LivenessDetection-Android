# libfacesdk

Identixia Face Recognition Android runtime module.

- Keep `facerecognitionsdk.aar` and `databases/*.xdb` here for local sample builds.
- The sample app applies `install.gradle`, which uses this module when the AAR is already here.
- Customer apps: apply `install.gradle` from tag `v1.0.0` (downloads the Release when needed).
- Android apps must set `packaging { jniLibs { useLegacyPackaging = true } }`.
