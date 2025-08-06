#! /bin/bash

_HOME2_=$(dirname $0)
export _HOME2_
_HOME_=$(cd $_HOME2_;pwd)
export _HOME_

basedir="$_HOME_""/../"
cd "$basedir"

r1='https://github.com/zoff99/sqlite-jdbc'
u1='https://jitpack.io/com/github/zoff99/pkgs_zoffccAndroidJDBC/'
u2='/pkgs_zoffccAndroidJDBC-'
u3='.aar'
f1='example_android/app/build.gradle'

tagspec=''
ver=$(git ls-remote --refs --sort='v:refname' --tags "$r1" 2>/dev/null \
    | cut --delimiter='/' --fields=3 2>/dev/null \
    | grep '^jdbcjni' \
    | tail -1 2>/dev/null \
    | sed -e 's#^jdbcjni##')


url="$u1""$ver""$u2""$ver""$u3"

tf='./tmpfile2.aar'

wget "$url" -O "$tf" >/dev/null 2>/dev/null
if [ ! -s "$tf" ]; then
  echo "new version ""$ver"" not yet uploaded to jitpack"
  rm -f "$tf"
  exit 0
fi
rm -f "$tf"

echo "__VERSIONUPDATE__:""$ver"

sed -i -e 's#implementation '"'"'com.github.zoff99:pkgs_zoffccAndroidJDBC:.*#implementation '"'"'com.github.zoff99:pkgs_zoffccAndroidJDBC:'"$ver"''"'"'#' "$f1"

