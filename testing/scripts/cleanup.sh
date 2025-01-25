curl https://api.openai.com/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $OPENAI_API_KEY" \
  -d '{
  "model": "gpt-4o-mini",
  "messages": [
    {
      "role": "user",
      "content": [
        {
          "type": "text",
          "text": "This is a transcript of a voice message, help to enhanced the readability but be very careful not to alter information. Keep the original language of the message.\n\nMessage:\n\n{{message}}"
        }
      ]
    }
  ],
  "response_format": {
    "type": "text"
  },
  "temperature": 1,
  "max_completion_tokens": 2048,
  "top_p": 1,
  "frequency_penalty": 0,
  "presence_penalty": 0
}' | jq '.choices[0].message.content'