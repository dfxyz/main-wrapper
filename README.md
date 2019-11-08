main-wrapper
================================================================================
A main function wrapper to make it easier to start or stop JVM-based program
as daemon.

Usage
--------------------------------------------------------------------------------
0. Acquire main-wrapper; If you use gradle or maven, you can use 
[JitPack](https://jitpack.io/) to depend on this repository:
    ```gradle
    repositories {
        // ...
        maven { url 'https://jitpack.io' }
    }
    dependencies {
        // ...
        implementation 'com.github.dfxyz:main-wrapper:[TAG]'
    }
    ```
1. Create a class (e.g. `MainWrapper`) which extends 
`dfxyz.mainwrapper.AbstractMainWrapper`:
    * Call `new MainWrapper().invoke(args)` in the main function
    * Override `MainWrapper#processUuidFilename()` with any name you like
    (a PID-like file will be created with it)
    * Override `MainWrapper#mainFunction(String[])` with the actual main logic

    For example:
    ```java
    public class MainWrapper extends dfxyz.mainwrapper.AbstractMainWrapper {
    
        public static void main(final String[] args) {
            new MainWrapper().invoke(args);
        }
    
        @Override
        protected String processUuidFilename() {
            return "application.process.uuid";
        }
    
        @Override
        protected void mainFunction(final String[] args) {
            // place the actual main logic
        }
    }
    ```
2. Run Java with `MainWrapper` as the main class, then pass one of 
the following arguments:
    * `run [arg ...]`: run your program in foreground; the real arguments
    can be passed after "run"
    * `start [arg ...]`: run your program in background (if not started yet);
    the real arguments can be passed after "start"
    * `restart [arg ...]`: restart your program in background (if it's running,
    stop it first); the real arguments can be passed after "restart"
    * `stop`: stop your program running in background
    * `status`: check if your program is running in background
