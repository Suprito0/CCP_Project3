<!-->Used AI to format it better<!-->
# COMP3011 Assignment 3 - Multithreaded Video Player with Subtitles

This project integrates the Assignment 2 JavaFX multithreaded video player
with the Assignment 1 Spring Boot speech-to-text server.

## Requirements
- Java JDK 25 for the player
- Maven
- A JavaFX-capable desktop
- Spring Boot server at `http://localhost:8080`
- `OPENAI_API_KEY` set **on the server only** for live transcription

## Running
In terminal 1:

    cd server
    make run

In terminal 2, from the project root:

    mvn javafx:run '-Djavafx.args=movie.mp4 -Sn1a'

`-S` starts with subtitles, `S` toggles them, and `-a` enables soundtrack playback.
Cloud transcription is performed by the server, not directly by the player.

## Testing

    mvn clean test
    mvn -f server/pom.xml clean test

## Important limitations
- First-time cloud transcription can arrive later than video playback.
- The server uses an in-memory cache, which resets on restart.
- Transcription accuracy depends on the audio actually sent to Whisper.
- Failed chunks are retried once and then skipped so playback can continue.