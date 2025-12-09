#!/bin/bash
#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#


# Script to extract all test classes using mini clusters

BASEDIR="/home/shuai/xlab/restart_testing/accumulo"
OUTPUT="/home/shuai/xlab/restart_testing/accumulo/mini-accumulo-cluster-test.csv"

# Clear output file
> "$OUTPUT"

# Helper function to extract fully qualified class name
get_fqn() {
    local file=$1
    local package=$(grep -m 1 "^package " "$file" | sed 's/package \(.*\);/\1/')
    local classname=$(grep -m 1 "^public class\|^class\|^public abstract class" "$file" | sed 's/.*class \([^ ]*\).*/\1/')
    echo "${package}.${classname}"
}

echo "Extracting test classes using mini clusters..."

# 1. Direct MiniAccumuloCluster usage (minicluster module tests)
echo "# Processing direct MiniAccumuloCluster usage..."
for file in $(find "$BASEDIR/minicluster/src/test/java" -name "*Test.java" -o -name "*IT.java"); do
    if grep -q "MiniAccumuloCluster\|MiniAccumuloClusterImpl" "$file"; then
        fqn=$(get_fqn "$file")
        # Determine which cluster type
        if grep -q "new MiniAccumuloClusterImpl\|MiniAccumuloClusterImpl cluster" "$file"; then
            echo "MiniAccumuloClusterImpl,$fqn" >> "$OUTPUT"
        else
            echo "MiniAccumuloCluster,$fqn" >> "$OUTPUT"
        fi
    fi
done

# 2. SharedMiniClusterBase extensions (uses MiniAccumuloClusterImpl)
echo "# Processing SharedMiniClusterBase extensions..."
for file in $(grep -rl "extends SharedMiniClusterBase" "$BASEDIR/test" "$BASEDIR/hadoop-mapreduce" --include="*.java"); do
    fqn=$(get_fqn "$file")
    echo "MiniAccumuloClusterImpl,$fqn" >> "$OUTPUT"
done

# 3. ConfigurableMacBase extensions (uses MiniAccumuloClusterImpl)
echo "# Processing ConfigurableMacBase extensions..."
for file in $(grep -rl "extends ConfigurableMacBase" "$BASEDIR/test" "$BASEDIR/hadoop-mapreduce" --include="*.java"); do
    # Skip base classes
    if ! grep -q "^public abstract class" "$file"; then
        fqn=$(get_fqn "$file")
        echo "MiniAccumuloClusterImpl,$fqn" >> "$OUTPUT"
    fi
done

# 4. AccumuloClusterHarness extensions (can use MiniAccumuloClusterImpl when in MINI mode)
echo "# Processing AccumuloClusterHarness extensions..."
for file in $(grep -rl "extends AccumuloClusterHarness" "$BASEDIR/test" "$BASEDIR/hadoop-mapreduce" --include="*.java"); do
    # Skip base classes
    if ! grep -q "^public abstract class" "$file"; then
        fqn=$(get_fqn "$file")
        echo "MiniAccumuloClusterImpl,$fqn" >> "$OUTPUT"
    fi
done

# 5. Extensions of base classes that extend SharedMiniClusterBase
echo "# Processing ComprehensiveITBase extensions..."
for file in $(grep -rl "extends ComprehensiveITBase" "$BASEDIR/test" --include="*.java"); do
    if ! grep -q "^public abstract class" "$file"; then
        fqn=$(get_fqn "$file")
        echo "MiniAccumuloClusterImpl,$fqn" >> "$OUTPUT"
    fi
done

# 6. Extensions of FateITBase and related
echo "# Processing Fate base class extensions..."
for base in "FateITBase" "FateStoreITBase" "FateExecutionOrderITBase" "FateOpsCommandsITBase" "FatePoolsWatcherITBase" "FateStatusEnforcementITBase" "MultipleStoresITBase"; do
    for file in $(grep -rl "extends $base" "$BASEDIR/test" --include="*.java"); do
        if ! grep -q "^public abstract class" "$file"; then
            fqn=$(get_fqn "$file")
            echo "MiniAccumuloClusterImpl,$fqn" >> "$OUTPUT"
        fi
    done
done

# 7. Extensions of other base classes
echo "# Processing other base class extensions..."
for base in "VolumeITBase" "WALSunnyDayITBase" "GarbageCollectorTrashBase" "MergeTabletsITBase" "ExternalCompaction2ITBase" "CompactionITBase"; do
    for file in $(grep -rl "extends $base" "$BASEDIR/test" --include="*.java"); do
        if ! grep -q "^public abstract class" "$file"; then
            fqn=$(get_fqn "$file")
            echo "MiniAccumuloClusterImpl,$fqn" >> "$OUTPUT"
        fi
    done
done

# Sort by cluster name, then by class name
echo "# Sorting results..."
sort -t',' -k1,1 -k2,2 "$OUTPUT" -o "$OUTPUT"

# Remove duplicates
echo "# Removing duplicates..."
sort -u -t',' -k1,1 -k2,2 "$OUTPUT" -o "$OUTPUT"

echo "Done! Results written to $OUTPUT"
wc -l "$OUTPUT"
