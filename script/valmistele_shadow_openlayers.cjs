/*
 * Tarvitaan niin kauan kuin OpenLayers tulee Mavenin cljsjs-pakettina. Shadow
 * ei indeksoi cljsjs/-alkuisia JavaScript-resursseja, mutta Harjan karttakoodi
 * käyttää paketin Closure-nimiavaruuksia, kuten ol.proj ja ol.Map.
 *
 * Tästä voi luopua, kun OpenLayers on siirretty Shadowin tukemaan resurssi- tai
 * npm-malliin, kaikki tuotanto- ja testikäännökset käyttävät uutta mallia ja
 * puhdas CI-ajo onnistuu ilman valmistelua. Poista silloin tämä tiedosto,
 * package.jsonin valmistelukutsu ja vanha cljsjs/openlayers-riippuvuus, jos
 * mikään muu käännös ei enää tarvitse sitä.
 */

const childProcess = require("child_process");
const fs = require("fs");
const os = require("os");
const path = require("path");

const juurikansio = path.resolve(__dirname, "..");
const luokkatie = childProcess.execFileSync(
  "lein",
  ["with-profile", "+shadow-cljs-testit", "classpath"],
  { cwd: juurikansio, encoding: "utf8" }
);
const openlayersJari = luokkatie
  .split(path.delimiter)
  .map((polku) => polku.trim())
  .find(
    (polku) =>
      polku.includes(`${path.sep}cljsjs${path.sep}openlayers${path.sep}`) &&
      /^openlayers-.*\.jar$/.test(path.basename(polku))
  );

if (!openlayersJari) {
  throw new Error("OpenLayers-jaria ei löytynyt Leiningenin classpathista.");
}

const valiaikainenKansio = fs.mkdtempSync(
  path.join(os.tmpdir(), "harja-openlayers-")
);
const kohdekansio = path.join(juurikansio, "dev-resources", "tmp");
const resurssit = ["ol", "ol.ext"];

try {
  fs.mkdirSync(kohdekansio, { recursive: true });
  childProcess.execFileSync(
    "jar",
    [
      "xf",
      openlayersJari,
      "cljsjs/openlayers/development/ol",
      "cljsjs/openlayers/development/ol.ext",
    ],
    { cwd: valiaikainenKansio, stdio: "inherit" }
  );

  const lahdekansio = path.join(
    valiaikainenKansio,
    "cljsjs",
    "openlayers",
    "development"
  );

  for (const resurssi of resurssit) {
    const lahde = path.join(lahdekansio, resurssi);
    const kohde = path.join(kohdekansio, resurssi);
    fs.rmSync(kohde, { recursive: true, force: true });
    fs.cpSync(lahde, kohde, { recursive: true });
  }
} finally {
  fs.rmSync(valiaikainenKansio, { recursive: true, force: true });
}