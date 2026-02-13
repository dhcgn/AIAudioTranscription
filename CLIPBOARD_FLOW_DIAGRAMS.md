# Clipboard Size Limitation - Flow Diagrams

## User Flow Diagram

### Small Text Flow (≤ 20,000 characters)
```
┌─────────────────────┐
│   User clicks       │
│  "Copy" button      │
└──────────┬──────────┘
           │
           v
┌─────────────────────┐
│  ClipboardHelper    │
│  checks text.length │
└──────────┬──────────┘
           │
           v
     ≤ 20,000 chars?
           │
           v YES
┌─────────────────────┐
│  Copy to clipboard  │
│  via ClipboardMgr   │
└──────────┬──────────┘
           │
           v
┌─────────────────────┐
│  Toast: "Text       │
│  copied to          │
│  clipboard"         │
└──────────┬──────────┘
           │
           v
      ✅ DONE
```

### Large Text Flow (> 20,000 characters)
```
┌─────────────────────┐
│   User clicks       │
│  "Copy" button      │
└──────────┬──────────┘
           │
           v
┌─────────────────────┐
│  ClipboardHelper    │
│  checks text.length │
└──────────┬──────────┘
           │
           v
     > 20,000 chars?
           │
           v YES
┌─────────────────────┐
│  Store text in      │
│  pendingTextForSave │
└──────────┬──────────┘
           │
           v
┌─────────────────────┐
│  Toast: "Text too   │
│  large (X chars).   │
│  Opening file save  │
│  dialog..."         │
└──────────┬──────────┘
           │
           v
┌─────────────────────┐
│  Launch SAF intent: │
│  ACTION_CREATE_     │
│  DOCUMENT           │
└──────────┬──────────┘
           │
           v
┌─────────────────────┐
│  System shows file  │
│  save dialog        │
└──────────┬──────────┘
           │
      ┌────┴────┐
      │         │
  User saves  User cancels
      │         │
      v         v
┌───────────┐ ┌───────────┐
│  Write    │ │  Clear    │
│  text to  │ │  pending  │
│  URI      │ │  text     │
└─────┬─────┘ └─────┬─────┘
      │             │
      v             v
┌───────────┐   ✅ DONE
│  Toast:   │
│  "Text    │
│  saved    │
│  success" │
└─────┬─────┘
      │
      v
  ✅ DONE
```

## Architecture Diagram

### Component Interaction
```
┌─────────────────────────────────────────────────────┐
│                   MainActivity                      │
│  ┌────────────────────────────────────────────┐   │
│  │  Composable: MainContent                   │   │
│  │  ┌──────────────────────────────────────┐  │   │
│  │  │  IconButton("Copy to Clipboard")     │  │   │
│  │  │  onClick: onCopyToClipboard()        │  │   │
│  │  └────────────────┬─────────────────────┘  │   │
│  └───────────────────┼────────────────────────┘   │
│                      │                            │
│                      v                            │
│  ┌─────────────────────────────────────────────┐  │
│  │  handleCopyToClipboard(text)                │  │
│  │  ├─> ClipboardHelper.handleTextCopy()       │  │
│  │  ├─> fileSaveLauncher (if needed)           │  │
│  │  └─> pendingTextForSave = text              │  │
│  └─────────────────────────────────────────────┘  │
│                                                    │
│  ┌─────────────────────────────────────────────┐  │
│  │  fileSaveLauncher:                          │  │
│  │  ActivityResultLauncher                     │  │
│  │  ├─> Receives URI from SAF                  │  │
│  │  └─> Calls ClipboardHelper.writeTextToUri() │  │
│  └─────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────┘

                           │
                           │ uses
                           v

┌─────────────────────────────────────────────────────┐
│              ClipboardHelper (Utility)              │
│  ┌────────────────────────────────────────────┐   │
│  │  handleTextCopy(context, text, ...)       │   │
│  │  ├─> if (text.length <= 20_000)           │   │
│  │  │    copyToClipboard()                    │   │
│  │  └─> else                                  │   │
│  │       initiateFileSave()                   │   │
│  └────────────────────────────────────────────┘   │
│                                                    │
│  ┌────────────────────────────────────────────┐   │
│  │  writeTextToUri(context, uri, text)        │   │
│  │  ├─> contentResolver.openOutputStream()    │   │
│  │  └─> outputStream.write(text.toByteArray())│   │
│  └────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────┘

                           │
                           │ uses
                           v

┌─────────────────────────────────────────────────────┐
│               Android System APIs                   │
│  ┌────────────────────────────────────────────┐   │
│  │  ClipboardManager                          │   │
│  │  ├─> setPrimaryClip()                      │   │
│  │  └─> ClipData.newPlainText()               │   │
│  └────────────────────────────────────────────┘   │
│                                                    │
│  ┌────────────────────────────────────────────┐   │
│  │  Storage Access Framework (SAF)            │   │
│  │  ├─> Intent.ACTION_CREATE_DOCUMENT         │   │
│  │  ├─> ContentResolver.openOutputStream()    │   │
│  │  └─> User file picker dialog               │   │
│  └────────────────────────────────────────────┘   │
│                                                    │
│  ┌────────────────────────────────────────────┐   │
│  │  Toast                                      │   │
│  │  └─> User feedback messages                │   │
│  └────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────┘
```

## HistoryActivity Integration

```
┌─────────────────────────────────────────────────────┐
│                 HistoryActivity                     │
│  ┌────────────────────────────────────────────┐   │
│  │  Composable: TranscriptionHistoryItem      │   │
│  │  ┌──────────────────────────────────────┐  │   │
│  │  │  DropdownMenu                        │  │   │
│  │  │  ├─> "Copy"                          │  │   │
│  │  │  │   onClick: onCopyToClipboard(     │  │   │
│  │  │  │     entry.text,                   │  │   │
│  │  │  │     "transcription_{timestamp}"   │  │   │
│  │  │  │   )                                │  │   │
│  │  │  │                                    │  │   │
│  │  │  └─> "Copy with Details"             │  │   │
│  │  │      onClick: onCopyToClipboard(     │  │   │
│  │  │        detailedText,                 │  │   │
│  │  │        "transcription_details_{ts}"  │  │   │
│  │  │      )                                │  │   │
│  │  └──────────────────────────────────────┘  │   │
│  └────────────────┬───────────────────────────┘   │
│                   │                               │
│                   v                               │
│  ┌─────────────────────────────────────────────┐  │
│  │  handleCopyToClipboard(text, fileName)      │  │
│  │  └─> Same flow as MainActivity              │  │
│  └─────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────┘
```

## Data Flow Diagram

### Clipboard Copy (Small Text)
```
User Action
    │
    v
Activity Method
    │
    v
ClipboardHelper
    │
    ├─> Check Size (≤ 20K)
    │
    ├─> ClipboardManager.setPrimaryClip()
    │
    └─> Toast.show("Copied")
```

### File Save (Large Text)
```
User Action
    │
    v
Activity Method
    │
    v
ClipboardHelper
    │
    ├─> Check Size (> 20K)
    │
    ├─> Store in Activity.pendingTextForSave
    │
    ├─> Toast.show("Too large...")
    │
    └─> Launch SAF Intent
            │
            v
        System File Picker
            │
            v
        User Selects Location
            │
            v
        Activity.onActivityResult
            │
            v
        ClipboardHelper.writeTextToUri()
            │
            ├─> ContentResolver.openOutputStream()
            │
            ├─> Write bytes
            │
            ├─> Clear pendingTextForSave
            │
            └─> Toast.show("Saved")
```

## State Management

### Activity State Variables
```
MainActivity / HistoryActivity
┌──────────────────────────────┐
│  pendingTextForSave: String? │  ← Temporary storage
│                              │    during file save flow
│  ┌────────────────────────┐ │
│  │ null: No save in       │ │
│  │       progress         │ │
│  │                        │ │
│  │ String: Save in        │ │
│  │         progress, will │ │
│  │         be written to  │ │
│  │         URI            │ │
│  └────────────────────────┘ │
└──────────────────────────────┘
```

### Lifecycle
```
1. Initial State: pendingTextForSave = null

2. Large Text Copy Initiated:
   pendingTextForSave = text
   
3. SAF Dialog Shown:
   pendingTextForSave = text (retained)
   
4a. User Saves:
    Write pendingTextForSave to URI
    pendingTextForSave = null
    
4b. User Cancels:
    pendingTextForSave = null
```

## Error Handling Flow

```
ClipboardHelper.handleTextCopy()
    │
    ├─> if (fileSaveLauncher == null)
    │       └─> Toast.show("File save not configured")
    │
    └─> if (size > threshold)
            └─> try {
                    initiateFileSave()
                } catch (e) {
                    Toast.show("Error: ${e.message}")
                }

ClipboardHelper.writeTextToUri()
    │
    └─> try {
            openOutputStream()
            write()
            Toast.show("Success")
            return true
        } catch (e) {
            Toast.show("Failed: ${e.message}")
            return false
        }
```

---

## Legend

- `┌─┐` - Component/Process box
- `│ │` - Container boundaries
- `├─┤` - Sub-component
- `─>` - Data/control flow
- `v` - Flow direction
- `✅` - Successful completion
- `⚠️` - Warning/attention needed
