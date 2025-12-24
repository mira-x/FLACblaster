# FLACBlaster architecture and design decisions

FLACblaster is a file-based music player. Its goals are: (1) A responsive, fast, realtime file browser and manager, (2) a complete and customizable music player/service, and (3) metadata reading and writing. It's a spiritual successor to SicMu Neo, which cannot be further developed due to too much tech debt.

In greater detail, the goals for FLACblaster are:
- (1) A modern Jetpack Compose Material Design application
- (1) Real time UI updates when the DB data changes
- (1) A file explorer and manager that can move, rename, copy and replace files, and that can do batch operations like batch renaming or folder packing/unpacking
- (1) A responsive design that works well on all devices and screen sizes
- (1) A slim UI (inspired by SicMu Neo) with a learning curve - This is a tool for power users

- (2) A music player that works in the background and contains common features such as scope-based looping (loop one song, loop one folder or loop the whole library, or stop after this song) and shuffle play
- (2) A music player that supports mono/stereo downmixing
- (2) Support for multiple sources of album art images (inside the file, album.jpg, etc)

- (3) A metadata viewer (for all formats metadata, be it MP3, FLAC, OGG or WAV)
- (3) A manual metadata editor, for single files and for batch operations
- (3) A MusicBrainz editor, that can look up the song's and artist's ID so that further metadata can easily be downloaded

In the backend, we want to split music playing from the files as much as possible. The file explorer in the main screen of the app should only act as a file browser and manager (which moves, renames, deletes files). For this reason, the development timeline for this projects looks like this, in this order:

- [ ] Add the songs database and a fully automatic file scanner
- [ ] Add a tree view for the songs
- [ ] Add a music service
- [ ] ... TODO: Further planning

# Database
The source of truth is the file database (but also the file system. Read on to understand this). High effort should be made to always keep it in sync with the filesystem, using inotify / FileObservers. To ensure synchronization between database and filesystem we (1) watch it and (2) access the filesystem only through the database wrapper class.

Put more elaborately: (1) The filesystem should be scanned on every app start, on every app opening (i.e. the user has opened this app, switched to another app and now switches back), and when the user drags down on the screen to manually re-load, like when reloading a website in Google Chrome. A nice-to-have feature would be an opt-in function that additionally observes the filesystem in real time whenever the app is in the foreground. Furthermore, the app's UI should be very responsive and display live data whenever possible. (2) Also, the database singleton class will have a number of helper functions that both the file manager and the music player part of the app use. These functions provide synchronization on a best-effort basis. For instance, when the user wants to change a song's metadata, we first try to write it to disk, and if that succeeds, we write that back to the database and then display that information from the database. Should a file access denied error occur, we do not write the new metadata to the DB. Unlike SicMu, we do not try to synchronize metadata multiple times over many days, but we only write to the DB what the files on disk really reflect. If that file change did not work, then it will not end up in the DB.

The main table in this DB is 'files', containing all relevant files (folders, MP3s, CUEs, etc). The columns are:

- path (absolute path as string, e.g. /storage/0/emulated/Music/mysong.mp3, folders may or may not have a trailing slash)
- is_folder (boolean)
- last_modified (unix timestamp in millis; Android offers this data for both files and folders, but folders' last modified date only changes when a direct "child" file of that folder is changed, but not when a file in a sub-directory is changed; This property of folders is used for quickly scanning for changes)
- size (in bytes; for calculating the bitrate)
- children (int; Applies only to folders)
- duration (milliseconds; Applies to files (duration of that file) and folders (duration sum of all files in this directory and sub-directories)
- metadata (A JSON key-values pair encoded as string. Note the value*s*. A single key may contain multiple values, e.g. when a song has multiple artists. Metadata keys are case-insensitive; This contains all the metadata that does not come from the file system but is included in the audio container, e.g. Artist, Album, Year, MusicBrainz IDs, etc)

