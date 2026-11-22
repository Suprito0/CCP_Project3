.PHONY: run build compile test clean git check-env

# Load environment variables
ifneq (,$(wildcard .env))
include .env
export OPENAI_API_KEY
endif

run:
	mvn spring-boot:run

build:
	mvn package

compile:
	mvn compile

test:
	mvn test

clean:
	mvn clean

git:
	git add .
	git commit -m "Final Build"
	git push