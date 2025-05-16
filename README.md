# Sorma<sup>2</sup> - Simple ORM(Android)<sup>2</sup>

It is based on the wonderful https://github.com/maskarade/Android-Orma by [FUJI Goro](https://github.com/gfx)
<br>
<br>
This is a rewrite in pure java, to generate most stuff you need and then add it to your project
either as a ```.jar file``` or as ```java source```.<br>
You still need to add ```sqlite-jdbc-3.49.1.0.jar``` to your project to use it.<br>
<br><br>
~~sadly [sqlite-jdbc](https://github.com/xerial/sqlite-jdbc) project decided that it needs ```slf4j-api jar``` (for no good reason).~~
this was solved by: https://github.com/xerial/sqlite-jdbc/pull/1178
<br><br>
see: https://github.com/xerial/sqlite-jdbc/issues/1094<br>
<br>

[![build](https://github.com/zoff99/iocipher_pack/actions/workflows/ci.yml/badge.svg)](https://github.com/zoff99/iocipher_pack/actions/workflows/ci.yml)


# Usage

create one file for each database table that you need.
<br>
create file for db table `Person` as `./gen/_sorm_Person.java`
<br>(don't worry it is not really Java, we just use the syntax here)
```Java
@Table
public class Person
{
    @PrimaryKey(autoincrement = true)
    public long id;
    @Column
    public String name;
    @Column
    public String address;
    @Column
    public int social_number;
}
```

now create the Java sources with the Java SORMA2 Generator. <b>you need at least java 17</b>.<br>
```bash
java -classpath ".:sqlite-jdbc-3.49.1.0.jar:sorma2.jar" \
  com/zoffcc/applications/sorm/Generator "gen"
```

your project is now ready to start.<br>
enter the generator directory:
```bash
cd ./gen/
ls -al
```

now move or copy all *.java files from the generator directory into your Android or Java project source tree
```bash
cd gen/
# remove class files
find . -name '*.class'|xargs rm -v
cp -av ./com /home/user/your/project/source/tree/
```

in your Java project you will need the `sqlite jdbc jar` and in your
Android project you will need `com.github.zoff99:pkgs_zoffccAndroidJDBC` from [jitpack.io](https://jitpack.io/#zoff99/pkgs_zoffccAndroidJDBC)



Android Example App:
------------------------

<img src="https://github.com/zoff99/sorma2/releases/download/nightly/android_screen01_21.png" height="300"></a><img src="https://github.com/zoff99/sorma2/releases/download/nightly/android_screen01_29.png" height="300"></a><img src="https://github.com/zoff99/sorma2/releases/download/nightly/android_screen01_33.png" height="300"></a><img src="https://github.com/zoff99/sorma2/releases/download/nightly/android_screen01_35.png" height="300"></a>
<br>

<br>
Any use of this project's code by GitHub Copilot, past or present, is done
without our permission.  We do not consent to GitHub's use of this project's
code in Copilot.
<br>
No part of this work may be used or reproduced in any manner for the purpose of training artificial intelligence technologies or systems.

