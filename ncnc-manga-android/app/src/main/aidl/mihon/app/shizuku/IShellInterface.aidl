// This file is the definition of the interface, but it is NOT compiled by the build:
// `android.buildFeatures.aidl` is disabled in app/build.gradle.kts, because aidl.exe writes its
// dependency file with the system code page and the build fails on non-ASCII project paths.
//
// The generated interface is checked in at:
//   app/src/main/java/mihon/app/shizuku/IShellInterface.java
//
// When the interface changes, update this file, regenerate the Java file with aidl, and replace
// that file's body. Keep the transaction ids stable, they are part of the wire protocol with the
// service running under Shizuku.
package mihon.app.shizuku;

interface IShellInterface {
    void install(in AssetFileDescriptor apk, in IntentSender intentSender) = 1;

    void destroy() = 16777114;
}
