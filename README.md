# XposedInstaller-2.6.11


sedInstaller function no activity through modify source code**

<br>

### progress table
<br>

question        | solve func    |       process
---|---|---
hidden app desktop icon         | [add data property for AndroidMainfest.xml ](https://www.jianshu.com/p/0d64bce9fbd2/) | ok
hidden app start activity       | [set android theme is Theme.NoDisplay for AndroidMainfest.xml](https://www.jianshu.com/p/3afcaa959de2)        | ok
add activity class that is called by host app   |       add a new activity class called "CustomActivity"        | ok
complete activate xposedInstaller framework | read source code modify "CustomActivity" class      | ok
complete add "com.example.eric.myapplication" app to XposedInstaller Module and activate it |   read source code modify "CustomActivity" class  | ok
many of Versions XposedInstaller compile suit for all android system    |   official website download project and modify source | `ing`

