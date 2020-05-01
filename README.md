## Installation:

**Step 1.**

install with npm: [Check in NPM](https://www.npmjs.com/package/react-native-sunmi-inner-printer)

```bash
npm install react-native-sunmi-inner-printer --save
```

or you may need to install via the clone address directly:

```bash 
npm install https://github.com/januslo/react-native-sunmi-inner-printer.git --save
```

**Step 2:**

Links this plugin to your project.

```bash
react-native link react-native-sunmi-inner-printer
```

or you may need to link manually 
* modify settings.gradle

```javascript 
include ':react-native-sunmi-inner-printer'
project(':react-native-sunmi-inner-printer').projectDir = new File(rootProject.projectDir, '../node_modules/react-native-sunmi-inner-printer/android')
```

* modify  app/build.gradle,add dependenceie：

```javascript
compile project(':react-native-sunmi-inner-printer')
```

* adds package references to  MainPackage.java 

```java

import com.sunmi.innerprinter.SunmiInnerPrinterPackage;
...

 @Override
    protected List<ReactPackage> getPackages() {
      return Arrays.<ReactPackage>asList(
          new MainReactPackage(),
            new SunmiInnerPrinterPackage()
      );
    }

```

**Step 3:**

refer in the javascript:
```javascript
import SunmiInnerPrinter from 'react-native-sunmi-inner-printer';

```

## Usage & Demo:
See examples folder of the source code that you can find a simple example of printing receipt.
// TODO
