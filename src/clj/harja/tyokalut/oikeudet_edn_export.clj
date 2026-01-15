(ns harja.tyokalut.oikeudet-edn-export
  "Generoi EDN-kopiot Excel-tiedostosta dokumentaatiota ja versionhallintaa varten.
  
  HUOM: EDN-tiedostot ovat VAIN KOPIOITA dokumentaatiota varten!
  Varsinainen oikeuksien määrittely tapahtuu edelleen Excelistä compile-aikana
  harja.domain.oikeudet.makrot namespacessa.
  
  Tämä namespace hyödyntää makrot.clj:n funktioita välttääkseen koodin duplikaatiota.
  
  Ajetaan myös automaattisesti kehitysympäristössä Harjan käynnistyessä
  (kehitysmoodi-komponentissa), jos resources/roolit.xlsx on muuttunut.
  
  Manuaalinen ajo:
    lein run -m harja.tyokalut.oikeudet-edn-export"
  (:require [clojure.pprint :as pprint]
            [harja.domain.oikeudet.makrot :as makrot]))


(defn- lue-oikeudet-excelista
  "Kutsuu harja.domain.oikeudet.makrot/lue-oikeudet funktiota.
  Tämä on private funktio makroissa, joten käytämme var-dereferenssiä."
  []
  (#'makrot/lue-oikeudet))

(defn- jarjesta-roolit
  "Järjestää roolit aakkosjärjestykseen nimen mukaan."
  [roolit]
  (vec (sort-by :nimi roolit)))

(defn- jarjesta-oikeudet
  "Järjestää oikeudet ja roolien-oikeudet deterministiseen järjestykseen.
  Käyttää sorted-map ja sorted-set varmistaakseen että sama Excel tuottaa aina saman EDN:n."
  [oikeudet]
  (->> oikeudet
    (map (fn [oikeus]
           (update oikeus
             :roolien-oikeudet
             (fn [roolien-oikeudet]
               (into (sorted-map)
                 (map (fn [[rooli oikeudet-set]]
                        [rooli (into (sorted-set) oikeudet-set)]))
                 roolien-oikeudet)))))
    (sort-by (juxt :osio :nakyma))
    vec))

;;; EDN-generointifunktiot ;;;

(defn generoi-roolit-edn!
  "Generoi resources/roolit.edn tiedoston Excelistä.
  
  HUOM: Tämä on VAIN KOPIO dokumentaatiota ja versionhallintaa varten!
  Roolit järjestetään aakkosjärjestykseen nimen mukaan."
  []
  (try
    (let [{:keys [roolimappaus]} (lue-oikeudet-excelista)
          roolit (jarjesta-roolit roolimappaus)]
      (spit "resources/roolit.edn"
        (with-out-str
          (println ";;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;")
          (println ";;; HUOM: Tämä tiedosto on AUTOMAATTISESTI GENEROITU KOPIO!")
          (println ";;; Lähde: resources/roolit.xlsx")
          (println ";;;")
          (println ";;; ÄLÄ MUOKKAA TÄTÄ TIEDOSTOA SUORAAN!")
          (println ";;; Muokkaa sen sijaan resources/roolit.xlsx Excel-tiedostoa.")
          (println ";;;")
          (println ";;; AUTOMAATTINEN GENEROINTI:")
          (println ";;;   - Kehitysympäristössä: Generoidaan automaattisesti käynnistyksessä")
          (println ";;;     jos resources/roolit.xlsx on muuttunut (aikaleima-tarkistus)")
          (println ";;;   - Manuaalinen generointi: lein run -m harja.tyokalut.oikeudet-edn-export")
          (println ";;;")
          (println ";;; MUISTA COMMITOIDA:")
          (println ";;;   git add resources/roolit.xlsx resources/roolit.edn resources/oikeudet.edn")
          (println ";;;   git commit -m \"Päivitä oikeudet\"")
          (println ";;;")
          (println ";;; GITHUB ACTIONS:")
          (println ";;;   - CI validoi että EDN:t ovat ajan tasalla Excelin kanssa")
          (println ";;;   - Jos validointi failaa, käynnistä Harja uudelleen paikallisesti")
          (println ";;;     (generoi EDN:t automaattisesti) tai aja manuaalinen komento")
          (println ";;;")
          (println ";;; Tämä tiedosto on tarkoitettu:")
          (println ";;;   - Dokumentaatioksi (helppo lukea tekstieditorilla)")
          (println ";;;   - Versionhallinnan helpottamiseksi (Git-diffit näkyvät)")
          (println ";;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;")
          (println)
          (pprint/pprint roolit)))
      (println "✅ Generoitu resources/roolit.edn (KOPIO)")
      (println (str "   " (count roolit) " roolia aakkosjärjestyksessä"))
      true)
    (catch Exception e
      (println "❌ Virhe generoitaessa roolit.edn:" (.getMessage e))
      (.printStackTrace e)
      false)))

(defn generoi-oikeudet-edn!
  "Generoi resources/oikeudet.edn tiedoston Excelistä.
  
  HUOM: Tämä on VAIN KOPIO dokumentaatiota ja versionhallintaa varten!
  Oikeudet ja roolien-oikeudet järjestetään aakkosjärjestykseen."
  []
  (try
    (let [{:keys [oikeudet]} (lue-oikeudet-excelista)
          oikeudet-jarjestetty (jarjesta-oikeudet oikeudet)]
      (spit "resources/oikeudet.edn"
        (with-out-str
          (println ";;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;")
          (println ";;; HUOM: Tämä tiedosto on AUTOMAATTISESTI GENEROITU KOPIO!")
          (println ";;; Lähde: resources/roolit.xlsx")
          (println ";;;")
          (println ";;; ÄLÄ MUOKKAA TÄTÄ TIEDOSTOA SUORAAN!")
          (println ";;; Muokkaa sen sijaan resources/roolit.xlsx Excel-tiedostoa.")
          (println ";;;")
          (println ";;; AUTOMAATTINEN GENEROINTI:")
          (println ";;;   - Kehitysympäristössä: Generoidaan automaattisesti käynnistyksessä")
          (println ";;;     jos resources/roolit.xlsx on muuttunut (aikaleima-tarkistus)")
          (println ";;;   - Manuaalinen generointi: lein run -m harja.tyokalut.oikeudet-edn-export")
          (println ";;;")
          (println ";;; MUISTA COMMITOIDA:")
          (println ";;;   git add resources/roolit.xlsx resources/roolit.edn resources/oikeudet.edn")
          (println ";;;   git commit -m \"Päivitä oikeudet\"")
          (println ";;;")
          (println ";;; GITHUB ACTIONS:")
          (println ";;;   - CI validoi että EDN:t ovat ajan tasalla Excelin kanssa")
          (println ";;;   - Jos validointi failaa, käynnistä Harja uudelleen paikallisesti")
          (println ";;;     (generoi EDN:t automaattisesti) tai aja manuaalinen komento")
          (println ";;;")
          (println ";;; Tämä tiedosto on tarkoitettu:")
          (println ";;;   - Dokumentaatioksi (helppo lukea tekstieditorilla)")
          (println ";;;   - Versionhallinnan helpottamiseksi (Git-diffit näkyvät)")
          (println ";;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;")
          (println)
          (println ";;; Oikeusmatriisi: osio -> näkymä -> rooli -> oikeudet")
          (println ";;;")
          (println ";;; Oikeuskoodit:")
          (println ";;;   R  = lukuoikeus omiin urakoihin")
          (println ";;;   R* = lukuoikeus kaikkiin urakoihin")
          (println ";;;   W  = kirjoitusoikeus omiin urakoihin")
          (println ";;;   W* = kirjoitusoikeus kaikkiin urakoihin")
          (println ";;;   W+ = kirjoitusoikeus omiin urakoihin (urakoitsija)")
          (println ";;;")
          (println ";;; Roolien-oikeudet on järjestetty aakkosjärjestykseen")
          (println ";;; varmistaakseen että generointi on deterministinen.")
          (println ";;; (Sama Excel -> aina sama EDN, ei turhia Git-diffejä)")
          (println)
          (pprint/pprint oikeudet-jarjestetty)))
      (println "✅ Generoitu resources/oikeudet.edn (KOPIO)")
      (println (str "   " (count oikeudet-jarjestetty) " oikeusmäärittelyä"))
      (println "   Roolien-oikeudet aakkosjärjestyksessä (deterministinen)")
      true)
    (catch Exception e
      (println "❌ Virhe generoitaessa oikeudet.edn:" (.getMessage e))
      (.printStackTrace e)
      false)))

(defn generoi-kaikki!
  "Generoi molemmat EDN-kopiot Excelistä.
  
  Tämä ajetaan myös automaattisesti kehitysympäristössä Harjan käynnistyessä
  (kehitysmoodi-komponentissa), jos resources/roolit.xlsx on muuttunut.
  
  Manuaalinen ajo:
    lein run -m harja.tyokalut.oikeudet-edn-export"
  []
  (println "═══════════════════════════════════════════════════════════════════════════════")
  (println "  Generoidaan EDN-KOPIOT Excelistä")
  (println "═══════════════════════════════════════════════════════════════════════════════")
  (println)
  (println "Lähde:  resources/roolit.xlsx")
  (println "Kohde:  resources/roolit.edn (KOPIO - ÄLÄ MUOKKAA)")
  (println "        resources/oikeudet.edn (KOPIO - ÄLÄ MUOKKAA)")
  (println)
  (println)
  (let [roolit-ok? (generoi-roolit-edn!)
        _ (println)
        oikeudet-ok? (generoi-oikeudet-edn!)]
    (println)
    (println "═══════════════════════════════════════════════════════════════════════════════")
    (if (and roolit-ok? oikeudet-ok?)
      (do
        (println "✨ VALMIS! Molemmat EDN-kopiot generoitu onnistuneesti.")
        (println)
        (println "MUISTUTUS:")
        (println "  - EDN-tiedostot ovat VAIN KOPIOITA dokumentaatiota varten")
        (println "  - Varsinainen oikeuksien määrittely tapahtuu Excelistä compile-aikana")
        (println "  - Commitoi molemmat: git add resources/roolit.xlsx resources/*.edn"))
      (do
        (println "⚠️  VIRHE! Generointi epäonnistui.")
        (println "   Katso virheviestit yllä.")))
    (println "═══════════════════════════════════════════════════════════════════════════════")
    (and roolit-ok? oikeudet-ok?)))

;;; Komentonrivi ;;;

(defn -main
  "Generoi EDN-kopiot Excelistä.
  
  Käyttö:
    lein run -m harja.tyokalut.oikeudet-edn-export"
  [& _args]
  (let [success? (generoi-kaikki!)]
    (System/exit (if success? 0 1))))