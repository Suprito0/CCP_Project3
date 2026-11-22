.PHONY: run

run:
	@set -a; . ./.env; set +a; mvn spring-boot:run

build:
	mvn clean package

compile:
	mvn clean compile

test:
	mvn test

clean:
	mvn clean

git:
	git add .
	git commit -m "Final Build"
	git push
