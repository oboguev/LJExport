# LJExport

Download LiveJournal blog journals and records

---

Git repository has two versions of the dowloader.

Older version is located in LJExport/LJExport and is now obsolete. It requires Firefox 40.1 and runs multiple instances of the browser each with its own login session to download pages in parallel. Another drawback is that a small number of pages are undownladabe, because LJ JavaScript fails to expand collapsed comments in these pages.

Newer version is located in LJExport/LJExport-maven and is described below. This version is self-contained, does not require external browsers and loads all the pages.

Its use requires several stages, as described below.

All programs are intended for execution from Eclipse environment. Programs lack UI and all parameters are entered/edited by directly editing Java source files.

----

### Main

Main downloading program.

Edit file Config.java, principally the following settings:

- **LoginUser** – username of the user to be used for dowmloading. Main log into LJ as this user.
- **Users** – usernames of users to be downloaded. Do not try to use the list of multiple users with Main, as it is easy to miss errors.
- **DownloadRoot** – location of download directory in the local file system.
- **LoadSince** and **LoadTill** – select the range of years/months to be loaded. If set to null, they respectively mean "from the earliest post" and "to the latest post".
- If **ReloadExistingFiles** is left false, posts already existing on local disk will not be re-downloded. This allows to run incremental update downloads, such as for adding newer posts.

Then run `Main`. As with all LJExport programs, it helps to run them in Eclipse debugger. Debug pane of Eclipse allows to monitor program activity through the change in thread names.

`Main` downloads posts with all comments expanded and with linked images orlinked files such as PDF, DOCX, TXT and others. Images and files are archived locally alongside posts HTML pages.

----

### MainLJRossiaOrg

Downloads from lj.rossia.org instead of livejournal.com.

----

### MainDreamwidthOrg

Downloads from dreamwidth.org instead of livejournal.com.
Download capability limited to a single particular style, so it may be curretnly unable to download just any Dreamwidth user.

----

### MainDownloadLinks

Download linked files that were missed in earlier download, perhaps because the server hosting these files was unavailable at the time. List of users to process should be entered in `MainDownloadLinks.java`.

----

### MainMakeMonthlyPages

Makes monthly "tapes" of posts and yearly-monthly blog navigation index. List of users to process should be entered in `MainMakeMonthlyPages.java`.

----

### MainStylesToLocal

Downloads styles resources making blog archive independent from the original server and able to survive the server being changed or decomissioned. List of users to process should be entered in `MainStylesToLocal.java`.

`MainStylesToLocal` can optionally executed in Dry Run mode when it only downloads style filed but does not patch HTML files to change style links from remote to local. The whole chain of style resources is downloaded (including their dependencies and dependencies of dependencies), and links within downloaded copies of resources are changed to redirect from remote resources to a local copy. Resource downloading is incremental: if repository has new HTML files with newer resources, these newer resources will be downloaded, but already downloaded resources will not be re-downloaded.

When executed in Wet Run mode, `MainStylesToLocal` will additionally patch HTML files to redirect style links within HTML files from remote server to a locally archived copy of style resources.

Change to HTML files from remote to local styles performed by `MainStylesToLocal` can be reverted with `MainStylesRevertToRemote` which undoes the changes done by `MainStylesToLocal` in HTML files and restores remote links to style resources in HTML files. Downloaded style resources are left intact.

----

### MainScrapeArchiveOrg

A related program is `MainScrapeArchiveOrg` which scrapes an archive of sites that are no longer available live from their pages stored in archive.org