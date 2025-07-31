Download nodejs v14.21.3 (latest compatible with Windows7)
    https://nodejs.org/download/release/v14.21.3/
    https://nodejs.org/download/release/v14.21.3/node-v14.21.3-win-x64.zip
unpack and put it on PATH

node -v
npm -v

mkdir \my-proxy
cd \my-proxy
npm init -y

# npm install express
# npm install playwright

npm install express@4
npm install playwright-core@1.32.3

node -p "require('playwright-core').chromium.launch"
   prints ==> [AsyncFunction: launch]
   
=======================================================================================================
   
copy file browser-proxy.js   
   
node browser-proxy.js
    prints ==> Proxy listening at http://localhost:3000
	
=======================================================================================================

Linux/Cygwin:
curl -X POST http://localhost:3000/fetch  -H "Content-Type: application/json" -d '{"url": "https://www.google.com"}'
Windows cmd:
curl -X POST http://localhost:3000/fetch -H "Content-Type: application/json" -d "{\"url\":\"https://www.google.com\"}"

Linux/Cygwin:
curl -X POST http://localhost:3000/fetch -H "Content-Type: application/json" -d '{"url":"https://www.livejournal.com","headers":{"User-Agent":"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36"}}'
Windows cmd:
curl -X POST http://localhost:3000/fetch -H "Content-Type: application/json" -d "{\"url\":\"https://www.livejournal.com\",\"headers\":{\"User-Agent\":\"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36\"}}"

=======================================================================================================

No redirect interception

=======================================================================================================

   [Java Client]
         |
         |  (JSON HTTP POST)
         v
    [Node.js proxy server]
         |
         |  (uses Playwright to control Chromium)
         v
    [Headless Chromium browser]
         |
         |  (makes real HTTPS request using real TLS stack)
         v
    [LiveJournal server]
	
=======================================================================================================

"C:\Program Files\Google\Chrome\Application\chrome.exe" --user-data-dir="C:\ProxyChromeProfile"

- login LJ
- save cookies
- disable JavaScript
- set about:blank homepage
