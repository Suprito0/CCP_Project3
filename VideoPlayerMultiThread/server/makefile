.PHONY: run

ifeq ($(OS),Windows_NT)
run:
	@powershell -NoProfile -Command "Get-Content .env | ForEach-Object { if ($$_ -match '^\s*([A-Za-z_]\w*)=(.*)$$') { [Environment]::SetEnvironmentVariable($$matches[1], $$matches[2].Trim(), 'Process') } }; mvn spring-boot:run; exit $$LASTEXITCODE"
else
run:
	@set -a; . ./.env; set +a; mvn spring-boot:run
endif

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
