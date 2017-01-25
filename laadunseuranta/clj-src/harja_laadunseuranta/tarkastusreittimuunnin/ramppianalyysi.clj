(ns harja-laadunseuranta.tarkastusreittimuunnin.ramppianalyysi
  "Tämä namespace tarjoaa funktion laadunseurannan mobiilityökalulla tehtyjen
   reittimerkintöjen ja niihin liittyvän geometrisoinnin korjaamiseen, mikäli
   geometrisointi on osunut virheellisesti rampille."
  (:require [taoensso.timbre :as log]
            [harja.domain.tierekisteri :as tr-domain]))

(defn maarittele-alkavien-ramppien-indeksit
  "Palauttaa indeksit merkintöihin, joissa siirrytään ei-ramppitieltä rampille.
   Indeksiä seuraavat merkinnät ovat rampilla ajettuja pisteitä
   (paitsi jos rampilla ajoa on vain yksi piste)."
  [merkinnat]
  (let [tulos (reduce (fn [tulos seuraava]
                        (if
                          ;; Edellinen piste ei ollut rampilla, mutta seuraava on
                          ;; --> Otetaan indeksi talteen
                          (and (not (-> tulos :edellinen-piste :piste-rampilla?))
                               (:piste-rampilla? seuraava))
                          ;; Jatketaan...
                          (assoc tulos :edellinen-piste seuraava
                                       :kasiteltava-indeksi (inc (:kasiteltava-indeksi tulos))
                                       :ramppien-alut
                                       (conj (:ramppien-alut tulos) (:kasiteltava-indeksi tulos)))
                          (assoc tulos :edellinen-piste seuraava
                                       :kasiteltava-indeksi (inc (:kasiteltava-indeksi tulos)))))
                      {:ramppien-alut []
                       :edellinen-piste nil
                       :kasiteltava-indeksi 0}
                      merkinnat)]
    (:ramppien-alut tulos)))

(defn- projisoi-merkinta-oikealle-tielle
  "Projisoi yksittäisen merkinnän annetulle tielle"
  [merkinta projisoitava-tie]
  (let [projisoitavaa-tieta-vastaavat-osoitteet (filter #(= (:tie %) projisoitava-tie)
                                                        (:laheiset-tr-osoitteet merkinta))
        lahin-vastaava-osoite (first (sort-by :etaisyys-gps-pisteesta projisoitavaa-tieta-vastaavat-osoitteet))]
    (if lahin-vastaava-osoite
      (do
        (log/debug "Projisoidaan ramppimerkintä tielle: " projisoitava-tie)
        (-> merkinta
            (assoc-in [:tr-osoite :tie] (:tie lahin-vastaava-osoite))
            (assoc-in [:tr-osoite :aosa] (:aosa lahin-vastaava-osoite))
            (assoc-in [:tr-osoite :aet] (:aet lahin-vastaava-osoite))
            (assoc-in [:tr-osoite :losa] (:losa lahin-vastaava-osoite))
            (assoc-in [:tr-osoite :let] (:let lahin-vastaava-osoite))))
      (do
        (log/debug (str "Ei voitu projisoida ramppimerkintää tielle: " projisoitava-tie))
        ;; Poistetaan merkinnältä projisoitu osoite. Tarkastusreittimuunnin olettaa merkinnän olevan
        ;; osa samaa tietä niin kauan kunnes oikea osoite löytyy.
        ;; Merkintöjä ei kuitenkaan sovi poistaa, sillä muuten saatetaan menettää havaintoja / mittauksia.
        (dissoc merkinta :tr-osoite)))))

(defn- projisoi-ramppi-oikealle-tielle
  "Projisoi rampin takaisin ramppia edeltäneelle tielle"
  [ramppia-edeltava-merkina rampin-merkinnat]
  (log/debug "Projisoidaan ramppi takaisin ramppia edeltäneelle tielle.")
  (let [projisoitava-tie (get-in ramppia-edeltava-merkina [:tr-osoite :tie])
        korjatut-merkinnat (mapv #(projisoi-merkinta-oikealle-tielle % projisoitava-tie)
                                 rampin-merkinnat)]
    korjatut-merkinnat))

(defn- rampin-merkinnat-indeksista
  "Palauttaa indeksistä eteenpäin ne merkinnät, jotka ovat osa samaa rampilla ajoa"
  [merkinnat-ramppitiedoilla ramppi-indeksi]
  (let [indeksin-jalkeiset-merkinnat (last (split-at ramppi-indeksi merkinnat-ramppitiedoilla))
        ;; Kerää rampin merkintöjä eteenpäin niin kauan kunnes ei olla enää rampilla
        rampin-merkinnat (reduce (fn [merkinnat seuraava-merkinta]
                                   (if (:piste-rampilla? seuraava-merkinta)
                                     (conj merkinnat seuraava-merkinta)
                                     (reduced merkinnat)))
                                 []
                                 indeksin-jalkeiset-merkinnat)]
    rampin-merkinnat))

(defn- korjaa-vahapatoinen-ramppi
  [merkinnat-ramppitiedoilla ramppi-indeksi n]
  (log/debug "Analysoidaan mahdollisesti vähäpätöinen ramppi indeksissä: " ramppi-indeksi)
  (if (= ramppi-indeksi 0)
    merkinnat-ramppitiedoilla ;; Merkinnät alkavat rampilta, ei tehdä mitään.
    (let [rampin-merkinnat (rampin-merkinnat-indeksista merkinnat-ramppitiedoilla ramppi-indeksi)
          korjattu-ramppi (if (< (count rampin-merkinnat) n)
                            (projisoi-ramppi-oikealle-tielle (nth merkinnat-ramppitiedoilla
                                                                  (dec ramppi-indeksi))
                                                             rampin-merkinnat)
                            rampin-merkinnat)
          _ (log/debug "Vähäpätöinen ramppi havaittu ja korjattu? " (not= rampin-merkinnat korjattu-ramppi))
          osa-ennen-ramppia (take ramppi-indeksi merkinnat-ramppitiedoilla)
          osa-rampin-jalkeen (drop (+ ramppi-indeksi (count korjattu-ramppi)) merkinnat-ramppitiedoilla)]
      (concat
        osa-ennen-ramppia
        korjattu-ramppi
        osa-rampin-jalkeen))))

(defn- korjaa-vahapatoiset-rampit
  "Ottaa reittimerkinnät ramppitiedolla ja analysoi kaikki rampille siirtymiset.
   Mikäli rampilla ajo sisältää vähemmän kuin N pistettä, projisoi rampin takaisin edeltävälle tielle.
   Muussa tapauksessa ei tee rampille mitään.

   Palauttaa kaikki merkinnät, joissa indeksin kuvaava ramppi-osa on korjattu."
  [merkinnat-ramppitiedoilla n]
  (let [alkavien-ramppien-indeksit (maarittele-alkavien-ramppien-indeksit merkinnat-ramppitiedoilla)
        ;; Käydään jokainen rampille siirtymä erikseen läpi ja korjataan tarvittaessa.
        korjatut-vahapatoiset-rampit (reduce (fn [edellinen-tulos seuraava-indeksi]
                                               (korjaa-vahapatoinen-ramppi edellinen-tulos seuraava-indeksi n))
                                             merkinnat-ramppitiedoilla
                                             alkavien-ramppien-indeksit)]
    korjatut-vahapatoiset-rampit))

(defn- projisoi-virheelliset-rampit-uudelleen
  "Projisoi virheelliset rampit seuraavasti:
  - Jos rampilla ajoa on erittäin pieni osuus, projisoi rampin takaisin tielle, josta rampille ajettiin"
  [merkinnat-ramppitiedoilla]
  (log/debug "Projisoidaan virheelliset rampit uudelleen")
  (log/debug "Rampeille siirtymisiä havaittu: "
             (count (maarittele-alkavien-ramppien-indeksit merkinnat-ramppitiedoilla)) "kpl.")
  (korjaa-vahapatoiset-rampit merkinnat-ramppitiedoilla 5))

(defn lisaa-merkintoihin-ramppitiedot
  "Lisää merkintöihin tiedon siitä, onko piste projisoitu rampille."
  [merkinnat]
  (mapv #(do
           (assoc % :piste-rampilla? (tr-domain/tie-rampilla? (get-in % [:tr-osoite :tie]))))
        merkinnat))

(defn korjaa-virheelliset-rampit
  "Ottaa tarkastusreittimerkinnät, jotka on projisoitu tieverkolle ja joilla on myös
   tieto lähimmistä tierekisteriosoitteista. Etsii kohdat, joissa piste sijaitsee virheellisesti rampilla tai on
   virheellisesti prijisoitu rampille. Palauttaa uudet merkinnät, joissa
   tällaiset virheelliset pisteet on projisoitu oikealle tielle.

   Jos yksikään piste ei sijaitse tai projisioidu rampille, palauttaa merkinnät sellaisenaan.

   Taustatarina: Moottoritiellä tarkastuksia tehdessä ramppien alut ovat usein päällekäin
   moottoritien kanssa. Tällä mekanismilla pyritään korjaamaan tilanne, jossa moottoritietä ajettaessa pisteet
   projisoituvat virheellisesti rampille, vaikka ajo tapahtuisikin moottoritiellä. On syytä huomata, että
   GPS:n epätarkkuudesta johtuen tämä funktio pelaa todennäköisyyksillä eikä tulos ole täysin varma."
  [merkinnat]
  (log/debug "Korjataan tarkastusajon virheelliset rampit. Merkintöjä: " (count merkinnat))
  (let [merkinnat-ramppitiedoilla (lisaa-merkintoihin-ramppitiedot merkinnat)
        korjatut-merkinnat (if (some :piste-rampilla? merkinnat-ramppitiedoilla)
                             (projisoi-virheelliset-rampit-uudelleen merkinnat-ramppitiedoilla)
                             merkinnat-ramppitiedoilla)]
    (mapv #(dissoc % :piste-rampilla?) korjatut-merkinnat)))