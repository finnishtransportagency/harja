
# Harja Info (harja-info)

> Väylän kunnossapidon seurannan ja raportoinnin info-sivut

## Install

Install NPM dependencies

```bash
$ npm install
```

## Building project

The project includes a live-reloading static server on port `8080` (you can change the port in the `gulpfile.js` config), which will build, launch, and rebuild the app whenever you change application code. To start the server, run:

```bash
$ npm run serve
```

To build and watch development version of code, run:

```bash
$ npm run watch
```

To build deployment (minified) version of code, run:

```bash
$ npm run build
```

In order to load test data add "?test" parameter to address eg. http://localhost:8080/?test


## Adding notices

To add notice edit following files on data-folder:
- care.json (hoitotiedotteet)
- maintenance.json (ylläpitotiedotteet)
- faq.json  (koulutusvideot)
- waterways.json  (vesiväylät)
- problemsolving-process.json (ongelmanselvitysprosessi)
- roadmap.json (aikataulusuunnitelmat)

New notices can be added anywhere in the list, they will be arranged by date automatically.


## Notice fields:

| Name   | Type             | Mandatory | Comments |
|--------|------------------|-----------|----------|
| date   | text(yyyy-mm-dd) | optional  | Notices are automatically arranged by date. If date field is left out the notice is pushed to the end of list |
| title  | text             | optional  | Default title will be used if left out |
| short  | text             | optional  | |
| body   | text/html        | optional  | Can be left empty but that makes no sense. Remember to escape quotes etc if you put in html like video embed codes. (eg. width="560" becomes width=\"560\") |
| images | array            | optional  | Array of urls pointing to either this project folder or outside source (see examples) |



## Example file:
```
[
	{
		"title": "Embedded video",
		"body": "<iframe width=\"560\" height=\"315\" src=\"https://www.youtube.com/embed/KJPcMLydqQo\" frameborder=\"0\" allowfullscreen></iframe>"
	},
	{
		"date": "2015-03-25",
		"title": "Tiedote #1",
		"short": "Lorem ipsum dolor sit amet",
		"body": "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Maecenas sodales dui id metus semper, non viverra odio mollis. Praesent aliquet a lorem et elementum. Curabitur lectus dui, varius sit amet elementum ut, iaculis nec purus. Praesent mattis gravida purus sed interdum. Nulla tincidunt sed felis scelerisque gravida. Nullam at magna ex. Phasellus leo sapien, viverra ac ante eu, convallis hendrerit libero. Morbi nibh dolor, hendrerit in lorem id, feugiat consequat turpis. Cras in quam non elit dictum porta ut et purus. Fusce suscipit condimentum libero, et posuere nisi posuere et. Curabitur id arcu id dolor euismod aliquam. Sed auctor quis dolor eu congue.",
		"images": ["/images/Liikennehankkeiden_vaikutukset.jpg", "/images/Tripla.jpg"]
	},
	{
		"date": "2016-02-03",
		"title": "Tiedote #2",
		"body": "Lorem ipsum dolor sit amet, consectetur adipiscing elit.",
		"images": ["/images/Tripla.jpg"]
	},
	{
		"date": "2016-02-30",
		"title": "Tiedote #3",
		"short": "Lorem ipsum dolor sit amet",
		"body": "Lorem ipsum dolor sit amet, consectetur adipiscing elit.",
		"images": ["/images/Tripla.jpg", "http://www.vayla.fi/documents/20473/251256/vayla_turvalaitehankinnat_2_web.jpg/f0b8b3b4-02de-46ef-b017-62129df769df?t=1473413822133"]
	},
	{
		"date": "2011-01-10",
		"body": "Lorem ipsum dolor sit amet, consectetur adipiscing elit."
	},
	{
		"date": "2011-02-30",
		"body": "Lorem ipsum dolor sit amet, consectetur adipiscing elit.",
		"images": ["http://www.vayla.fi/documents/20473/251256/vayla_turvalaitehankinnat_2_web.jpg/f0b8b3b4-02de-46ef-b017-62129df769df?t=1473413822133"]
	}
]
```


## Adding content on the main category pages

To edit content on the main pages of "Teiden hoito", "Teiden ylläpito", "Vesiväylät" and "Koulutusvideot" edit the "content.json" -file on data-folder


## "Content" -file fields:

| Name     | Type      | Mandatory | Comments |
|----------|-----------|-----------|----------|
| category | text      | mandatory (must be one of: "care", "maintenance" or "faq") | |
| title    | text      | optional  | |
| short    | text      | optional  | |
| body     | text/html | optional  | Can be left empty but that makes no sense. Remember to escape quotes etc if you put in html like video embed codes. (eg. width="560" becomes width=\"560\") |
| images   | array     | optional  | Array of urls pointing to either this project folder or outside source (see examples) |


## Example "Content" -file:

```
[
	{
		"category": "care",
		"title": "Teiden hoito",
		"short": "Lorem ipsum dolor sit amet",
		"body": "Lorem ipsum dolor sit amet, consectetur adipiscing elit.",
		"images": ["/images/hoidon-aikataulu-2016.png"]
	},
	{
		"category": "maintenance",
		"title": "Teiden ylläpito",
		"short": "Lorem ipsum dolor sit amet",
		"body": "Lorem ipsum dolor sit amet, consectetur adipiscing elit.",
		"images": ["/images/yllapidon-aikataulu-2016.png"]
	},
	{
		"category": "faq",
		"title": "Koulutusvideot",
		"short": "Lorem ipsum dolor sit amet",
		"body": "Lorem ipsum dolor sit amet, consectetur adipiscing elit.",
		"images": []
	},
	{
		"category": "waterways",
		"title": "Vesiväylät",
		"short": "",
		"body": "Uutisia Harjan käytöstä vesiväylillä",
		"images": []
	}
]
```
