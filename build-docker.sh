#!/bin/bash
VERSION=$(./mvnw help:evaluate -Dexpression=project.version -q -DforceStdout)
echo "Project Version: $VERSION"
./say.sh "Building: $VERSION"
if [ "$#" -ge 1 ]; then
	dependencies=( )
	projects=( $@ )
else
	dependencies=( qwandaq serviceq kogito-common )
	projects=( gadaq bridge fyodor dropkick lauchy messages shleemy )
fi

# iterate dependencies
for dependency in "${dependencies[@]}"
do
    echo "Building $dependency"

	# perform clean install
	./mvnw clean install -DskipTests=true -Dcheckstyle.skip -Dstyle.color=always -pl :$dependency

	# exit if build failed
	if [[ "$?" -ne 0 ]] ; then
		echo "Build failed"
		exit 1;
	fi
done

# iterate projects

for project in "${projects[@]}"
do
    echo "Building $project"

	# copy each project with the latest docker
	cp -f docker/* $project/src/main/docker/ 
	cp -f docker/* kogitoq/$project/src/main/docker/ 
	# perform clean install with docker build
	./mvnw clean install -Dquarkus.container-image.build=true -DskipTests=true -Dcheckstyle.skip -Dstyle.color=always -pl :$project

	# exit if build failed
	if [[ "$?" -ne 0 ]] ; then
		echo "Build failed"
		exit 1;
	fi

	# tag the docker container
    echo "Tagging $project"
	docker tag gennyproject/${project}:${VERSION} gennyproject/${project}:latest
done
./say.sh "finished building"
