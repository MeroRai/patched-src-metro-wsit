#!/bin/bash
#
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
#
# Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
#
# The contents of this file are subject to the terms of either the GNU
# General Public License Version 2 only ("GPL") or the Common Development
# and Distribution License("CDDL") (collectively, the "License").  You
# may not use this file except in compliance with the License.  You can
# obtain a copy of the License at
# https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
# or packager/legal/LICENSE.txt.  See the License for the specific
# language governing permissions and limitations under the License.
#
# When distributing the software, include this License Header Notice in each
# file and include the License file at packager/legal/LICENSE.txt.
#
# GPL Classpath Exception:
# Oracle designates this particular file as subject to the "Classpath"
# exception as provided by Oracle in the GPL Version 2 section of the License
# file that accompanied this code.
#
# Modifications:
# If applicable, add the following below the License Header, with the fields
# enclosed by brackets [] replaced by your own identifying information:
# "Portions Copyright [year] [name of copyright owner]"
#
# Contributor(s):
# If you wish your version of this file to be governed by only the CDDL or
# only the GPL Version 2, indicate your decision by adding "[Contributor]
# elects to include this software in this distribution under the [CDDL or GPL
# Version 2] license."  If you don't indicate a single choice of license, a
# recipient has the option to distribute your version of this file under
# either the CDDL, the GPL Version 2 or to extend the choice of license to
# its licensees as provided above.  However, if you add GPL Version 2 code
# and therefore, elected the GPL Version 2 license, then the option applies
# only if the new code is made subject to such option by the copyright
# holder.
#

source common.sh

## GF-3.x
GF_312_URL=http://download.java.net/glassfish/3.1.2.2/release/glassfish-3.1.2.2.zip
METRO_MAJOR_VERSION=2.3

export WORK_DIR=$WORKSPACE

export GF_SVN_ROOT=$WORK_DIR/glassfish
export DTEST_SVN_ROOT=$WORK_DIR/appserv-tests

PROXY_HOST=www-proxy.us.oracle.com
PROXY_PORT=80

#PROXY
set_proxy

export JAVA_MEMORY_PROP="-Xms256m -Xmx768m -XX:PermSize=256m -XX:MaxPermSize=512m"
export ANT_OPTS="$JAVA_MEMORY_PROP $JAVA_PROXY_PROP"
export MAVEN_OPTS="-Dmaven.javadoc.skip=true $JAVA_MEMORY_PROP $JAVA_PROXY_PROP"
#-Dmaven.repo.local=$M2_LOCAL_REPO


#fallback to defaults if needed
if [ -z "$MVN_REPO_URL" ]; then
    MVN_REPO_URL="https://maven.java.net/content/groups/staging"
fi

#fallback to defaults if needed
if [ -z "$GF_URL" ]; then
    GF_URL=$GF_312_URL
fi

pushd $WORK_DIR
if [ ! -z "$GF_URL" ]; then
    _wget $GF_URL
    export GF_ZIP=$WORK_DIR/${GF_URL##*/}
fi

if [ -z "$METRO_REVISION" ]; then
    METRO_BUNDLE="org/glassfish/metro/metro-standalone"
    if [ -z "$METRO_VERSION" ]; then
        LATEST_METRO_BUILD=`curl -s -k $MVN_REPO_URL/$METRO_BUNDLE/maven-metadata.xml | grep "<version>$METRO_MAJOR_VERSION-b[1-9]" | cut -d ">" -f2,2 | cut -d "<" -f1,1 | tail -1 | cut -d "b" -f2,2`
        METRO_VERSION="$METRO_MAJOR_VERSION-b$LATEST_METRO_BUILD"
    fi
    METRO_URL="$MVN_REPO_URL/$METRO_BUNDLE/$METRO_VERSION/metro-standalone-$METRO_VERSION.zip"
    _wget $METRO_URL
    export METRO_ZIP=$WORK_DIR/${METRO_URL##*/}
fi

if [ -z "$CTS_ZIP" ]; then
    _wget $CTS_URL
    export CTS_ZIP=$WORK_DIR/${CTS_URL##*/}
fi
popd

echo "GF_ZIP: $GF_ZIP"
echo "METRO_ZIP: $METRO_ZIP"
echo "CTS_ZIP: $CTS_ZIP"


echo "Preparing workspace:"
#revert all local changes if needed
declare -a svn_roots
svn_roots=( "$DTEST_SVN_ROOT/util" "$DTEST_SVN_ROOT/config" "$DTEST_SVN_ROOT/lib"
 "$DTEST_SVN_ROOT/devtests/webservice" )
if [ -e "$GF_SVN_ROOT/nucleus" ]; then
    svn_roots+="$GF_SVN_ROOT"
else
    svn_roots+="$GF_SVN_ROOT/appserver/tests/quicklook"
fi
for dir in "${svn_roots[@]}"
do
    if [[ ( -e "$dir" ) && ( -d $dir ) ]] ; then
        pushd $dir
        echo "reverting local changes in: $dir"
        for file in `svn status|grep "^ *?"|sed -e 's/^ *? *//'`;
        do
            echo " Removing: $file"
            rm -rf $file
        done
        svn -R revert .
        popd
    fi
done
unset svn_roots

#delete old
pushd $WORK_DIR
for dir in "test_results" ".repository" "wsit"
do
    if [ -e "$dir" ] ; then
        echo "Removing $dir"
        rm -rf $dir
    fi
done

if [ -e "metro.patch" ] ; then
  echo "Removing old patch for GlassFish"
  rm -f metro.patch
fi
popd


export RESULTS_DIR=$WORK_DIR/test_results
export QL_RESULTS_DIR=$RESULTS_DIR/quick_look
export DEVTESTS_RESULTS_DIR=$RESULTS_DIR/devtests
export CTS_RESULTS_DIR=$RESULTS_DIR/cts-smoke

pushd "$( cd -P "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

./quicklook.sh
mkdir -p $QL_RESULTS_DIR
pushd $GF_SVN_ROOT/appserver/tests/quicklook
cp quicklook_summary.txt *.log *.output $QL_RESULTS_DIR
popd
cp $WORK_DIR/tmp-gf/glassfish3/glassfish/domains/domain1/logs/server.log* $QL_RESULTS_DIR
mv $WORK_DIR/test-quicklook.log.txt $RESULTS_DIR

./devtests.sh
mkdir -p $DEVTESTS_RESULTS_DIR
pushd $DTEST_SVN_ROOT
cp test_results.* $DEVTESTS_RESULTS_DIR
pushd devtests/webservice
cp webservice.output $DEVTESTS_RESULTS_DIR/webservice.output.txt
cp count.txt $DEVTESTS_RESULTS_DIR
popd
popd
cp $WORK_DIR/tmp-gf/glassfish3/glassfish/domains/domain1/logs/server.log* $DEVTESTS_RESULTS_DIR
mv $WORK_DIR/test-devtests.log.txt $RESULTS_DIR

./cts-smoke.sh
mkdir -p $CTS_RESULTS_DIR
mv $WORK_DIR/test_results-cts/* $CTS_RESULTS_DIR
rm -rf $WORK_DIR/test_results-cts
cp $WORK_DIR/tmp-gf/glassfish3/glassfish/domains/domain1/logs/server.log* $CTS_RESULTS_DIR
mv $WORK_DIR/test-cts-smoke.log.txt $RESULTS_DIR

popd

export ALL=$RESULTS_DIR/test-summary.txt

rm -f $ALL || true
touch $ALL
if [ "`grep -E '.*Failures: 0.*' $QL_RESULTS_DIR/quicklook_summary.txt`" ]; then
    echo "QuickLook tests: OK" >> $ALL
else
    echo "QuickLook tests: `awk '/,/ { print $6 }' $QL_RESULTS_DIR/quicklook_summary.txt | cut -d ',' -f 1` failure(s)" >> $ALL
fi
if [ "`grep 'BUILD FAILURE' $RESULTS_DIR/test-quicklook.log.txt`" ]; then
    echo "QuickLook tests: build failure" >> $ALL
fi

if [ "`grep -E 'FAILED=( )+0' $DEVTESTS_RESULTS_DIR/count.txt`" ]; then
    echo "devtests tests: OK" >> $ALL
else
    echo "devtests tests: `awk '/FAILED=( )+/ { print $2 }' $DEVTESTS_RESULTS_DIR/count.txt` failure(s)" >> $ALL
fi
if [ "`grep 'BUILD FAILED' $RESULTS_DIR/test-devtests.log.txt`" ]; then
    echo "devtests tests: build failure" >> $ALL
fi

if [ ! "`grep 'Failed.' $CTS_RESULTS_DIR/summary.txt`" ]; then
    echo "CTS-smoke tests: OK" >> $ALL
else
    echo "CTS-smoke tests: `grep -c 'Failed.' $CTS_RESULTS_DIR/summary.txt` failure(s)" >> $ALL
fi
if [ "`grep 'BUILD FAILED' $RESULTS_DIR/test-cts-smoke.log.txt`" ]; then
    echo "CTS-smoke tests: build failure" >> $ALL
fi

cat $ALL