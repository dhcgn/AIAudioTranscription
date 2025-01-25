This Android App has the propose of helping with audio transcription.

## Developer Background

- Developer is not familiar with Android development
- Developer is not familiar with Java or Kotlin, but with C# and Go

## Use Case

Precondition: API Key for transcription service is stored

1. User selects or share a media file to transcribe
2. App sends the media file to a transcription service (e.g. OpenAI API to model whisper-1)
3. App displays the transcription result

## Main Features

- Pick a media file to transcribe
- Share a media file to transcribe

### Open Features

- [ ] Separate Settings/Configuration View
- [ ] History View with used parameters and source hint
- [ ] Make OpenAI API Key testable for working with the app (test access to model whisper-1)
- [ ] Allow to select from storage or share video files which are then converted to audio files for transcription
- [ ] Add custom App Icon
- [ ] Make text in transcription selectable with "copy all to clipboard" 


## Technical Details

- using **encrypted** storage for API Key
- re-encode media file with **ffmpeg** to opus with focus a low bitrate but good enough quality for transcription
  - `ffmpeg -i audio.mp3 -vn -map_metadata -1 -ac 1 -c:a libopus -b:a 12k -application voip audio.ogg` 
- Source code is available on GitHub, so no sensitive information is allowed to be hardcoded
- Test working OpenAI Key with: `curl https://api.openai.com/v1/models   -H "Authorization: Bearer $OPENAI_API_KEY" | jq '.data[] | select(.id == "whisper-1")'` 

### OpenAI API for transcription

Create transcription
post
 
https://api.openai.com/v1/audio/transcriptions
Transcribes audio into the input language.

Request body
file
file

Required
The audio file object (not file name) to transcribe, in one of these formats: flac, mp3, mp4, mpeg, mpga, m4a, ogg, wav, or webm.

model
string

Required
ID of the model to use. Only whisper-1 (which is powered by our open source Whisper V2 model) is currently available.

language
string

Optional
The language of the input audio. Supplying the input language in ISO-639-1 (e.g. en or de) format will improve accuracy and latency.

prompt
string

Optional
An optional text to guide the model's style or continue a previous audio segment. The prompt should match the audio language.

response_format
string

Optional
Defaults to json
The format of the output, in one of these options: json, text, srt, verbose_json, or vtt.

temperature
number

Optional
Defaults to 0
The sampling temperature, between 0 and 1. Higher values like 0.8 will make the output more random, while lower values like 0.2 will make it more focused and deterministic. If set to 0, the model will use log probability to automatically increase the temperature until certain thresholds are hit.

timestamp_granularities[]
array

Optional
Defaults to segment
The timestamp granularities to populate for this transcription. response_format must be set verbose_json to use timestamp granularities. Either or both of these options are supported: word, or segment. Note: There is no additional latency for segment timestamps, but generating word timestamps incurs additional latency.

#### Sample Curl Command

```bash
curl --request POST \
  --url https://api.openai.com/v1/audio/transcriptions \
  --header "Authorization: Bearer $OPENAI_API_KEY" \
  --header 'Content-Type: multipart/form-data' \
  --form file=@/path/to/file/audio.mp3 \
  --form model=whisper-1
```

#### OpenAPI Specification for OpenAI API for transcription

```yaml
  /audio/transcriptions:
    post:
      operationId: createTranscription
      tags:
        - Audio
      summary: Transcribes audio into the input language.
      requestBody:
        required: true
        content:
          multipart/form-data:
            schema:
              $ref: "#/components/schemas/CreateTranscriptionRequest"
      responses:
        "200":
          description: OK
          content:
            application/json:
              schema:
                oneOf:
                  - $ref: "#/components/schemas/CreateTranscriptionResponseJson"
                  - $ref: "#/components/schemas/CreateTranscriptionResponseVerboseJson"
      x-oaiMeta:
        name: Create transcription
        group: audio
        returns: The [transcription object](/docs/api-reference/audio/json-object) or a
          [verbose transcription
          object](/docs/api-reference/audio/verbose-json-object).
        examples:
          - title: Default
            request:
              curl: |
                curl https://api.openai.com/v1/audio/transcriptions \
                  -H "Authorization: Bearer $OPENAI_API_KEY" \
                  -H "Content-Type: multipart/form-data" \
                  -F file="@/path/to/file/audio.mp3" \
                  -F model="whisper-1"
              python: |
                from openai import OpenAI
                client = OpenAI()

                audio_file = open("speech.mp3", "rb")
                transcript = client.audio.transcriptions.create(
                  model="whisper-1",
                  file=audio_file
                )
              node: >
                import fs from "fs";

                import OpenAI from "openai";


                const openai = new OpenAI();


                async function main() {
                  const transcription = await openai.audio.transcriptions.create({
                    file: fs.createReadStream("audio.mp3"),
                    model: "whisper-1",
                  });

                  console.log(transcription.text);
                }

                main();
            response: >
              {
                "text": "Imagine the wildest idea that you've ever had, and you're curious about how it might scale to something that's a 100, a 1,000 times bigger. This is a place where you can get to do that."
              }
          - title: Word timestamps
            request:
              curl: |
                curl https://api.openai.com/v1/audio/transcriptions \
                  -H "Authorization: Bearer $OPENAI_API_KEY" \
                  -H "Content-Type: multipart/form-data" \
                  -F file="@/path/to/file/audio.mp3" \
                  -F "timestamp_granularities[]=word" \
                  -F model="whisper-1" \
                  -F response_format="verbose_json"
              python: |
                from openai import OpenAI
                client = OpenAI()

                audio_file = open("speech.mp3", "rb")
                transcript = client.audio.transcriptions.create(
                  file=audio_file,
                  model="whisper-1",
                  response_format="verbose_json",
                  timestamp_granularities=["word"]
                )

                print(transcript.words)
              node: >
                import fs from "fs";

                import OpenAI from "openai";


                const openai = new OpenAI();


                async function main() {
                  const transcription = await openai.audio.transcriptions.create({
                    file: fs.createReadStream("audio.mp3"),
                    model: "whisper-1",
                    response_format: "verbose_json",
                    timestamp_granularities: ["word"]
                  });

                  console.log(transcription.text);
                }

                main();
            response: >
              {
                "task": "transcribe",
                "language": "english",
                "duration": 8.470000267028809,
                "text": "The beach was a popular spot on a hot summer day. People were swimming in the ocean, building sandcastles, and playing beach volleyball.",
                "words": [
                  {
                    "word": "The",
                    "start": 0.0,
                    "end": 0.23999999463558197
                  },
                  ...
                  {
                    "word": "volleyball",
                    "start": 7.400000095367432,
                    "end": 7.900000095367432
                  }
                ]
              }
          - title: Segment timestamps
            request:
              curl: |
                curl https://api.openai.com/v1/audio/transcriptions \
                  -H "Authorization: Bearer $OPENAI_API_KEY" \
                  -H "Content-Type: multipart/form-data" \
                  -F file="@/path/to/file/audio.mp3" \
                  -F "timestamp_granularities[]=segment" \
                  -F model="whisper-1" \
                  -F response_format="verbose_json"
              python: |
                from openai import OpenAI
                client = OpenAI()

                audio_file = open("speech.mp3", "rb")
                transcript = client.audio.transcriptions.create(
                  file=audio_file,
                  model="whisper-1",
                  response_format="verbose_json",
                  timestamp_granularities=["segment"]
                )

                print(transcript.words)
              node: >
                import fs from "fs";

                import OpenAI from "openai";


                const openai = new OpenAI();


                async function main() {
                  const transcription = await openai.audio.transcriptions.create({
                    file: fs.createReadStream("audio.mp3"),
                    model: "whisper-1",
                    response_format: "verbose_json",
                    timestamp_granularities: ["segment"]
                  });

                  console.log(transcription.text);
                }

                main();
            response: >
              {
                "task": "transcribe",
                "language": "english",
                "duration": 8.470000267028809,
                "text": "The beach was a popular spot on a hot summer day. People were swimming in the ocean, building sandcastles, and playing beach volleyball.",
                "segments": [
                  {
                    "id": 0,
                    "seek": 0,
                    "start": 0.0,
                    "end": 3.319999933242798,
                    "text": " The beach was a popular spot on a hot summer day.",
                    "tokens": [
                      50364, 440, 7534, 390, 257, 3743, 4008, 322, 257, 2368, 4266, 786, 13, 50530
                    ],
                    "temperature": 0.0,
                    "avg_logprob": -0.2860786020755768,
                    "compression_ratio": 1.2363636493682861,
                    "no_speech_prob": 0.00985979475080967
                  },
                  ...
                ]
              }
```