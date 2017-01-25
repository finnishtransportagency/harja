(ns harja-laadunseuranta.tarkastusreittimuunnin.ramppianalyysi
  "Tämä namespace tarjoaa funktion laadunseurannan mobiilityökalulla tehtyjen
   reittimerkintöjen ja niihin liittyvän geometrisoinnin korjaamiseen, mikäli
   geometrisointi on osunut virheellisesti rampille."
  (:require [taoensso.timbre :as log]
            [harja.domain.tierekisteri :as tr-domain]))

(defn lisaa-merkintoihin-ramppitiedot
  "Lisää merkintöihin tiedon siitä, onko piste projisoitu rampille."
  [merkinnat]
  (mapv #(do
           (assoc % :piste-rampilla? (tr-domain/tie-rampilla? (get-in % [:tr-osoite :tie]))))
        merkinnat))

(defn maarittele-alkavien-ramppien-indeksit
  "Palauttaa indeksit merkintöihin, joissa siirrytään ei-ramppitieltä rampille.
   Indeksiä seuraavat merkinnät ovat rampilla ajettuja pisteitä
   (paitsi jos rampilla ajoa on vain yksi piste)."
  [merkinnat]
  (let [tulos (reduce (fn [tulos seuraava]
                        (if
                          ;; Edellinen piste ei ollut rampilla, mutta seuraava on
                          ;; --> Otetaan indeksi talteen ja jatketaan reitin tutkimista...
                          (and (not (-> tulos :edellinen-piste :piste-rampilla?))
                               (:piste-rampilla? seuraava))
                          (assoc tulos :edellinen-piste seuraava
                                       :kasiteltava-indeksi (inc (:kasiteltava-indeksi tulos))
                                       :ramppien-alut
                                       (conj (:ramppien-alut tulos) (:kasiteltava-indeksi tulos)))
                          ;; ...muuten vain jatketaan reitin tutkimista
                          (assoc tulos :edellinen-piste seuraava
                                       :kasiteltava-indeksi (inc (:kasiteltava-indeksi tulos)))))
                      {:ramppien-alut []
                       :edellinen-piste nil
                       :kasiteltava-indeksi 0}
                      merkinnat)]
    (:ramppien-alut tulos)))

(defn- laheisten-pisteiden-lahin-osuma-tielle
  "Etsii merkinnän läheisten teiden tiedoista annetun tienumeron projisoinnit ja palauttaa lähimmän."
  [merkinta tie]
  (let [projisoitavaa-tieta-vastaavat-osoitteet (filter #(= (:tie %) tie)
                                                        (:laheiset-tr-osoitteet merkinta))
        lahin-vastaava-osoite (first (sort-by :etaisyys-gps-pisteesta projisoitavaa-tieta-vastaavat-osoitteet))]
    lahin-vastaava-osoite))

(defn- projisoi-merkinta-oikealle-tielle
  "Projisoi yksittäisen merkinnän annetulle tielle"
  [merkinta projisoitava-tie]
  (if-let [lahin-vastaava-projisio (laheisten-pisteiden-lahin-osuma-tielle merkinta projisoitava-tie)]
    (do
      (log/debug "Projisoidaan ramppimerkintä tielle: " projisoitava-tie)
      (-> merkinta
          (assoc-in [:tr-osoite :tie] (:tie lahin-vastaava-projisio))
          (assoc-in [:tr-osoite :aosa] (:aosa lahin-vastaava-projisio))
          (assoc-in [:tr-osoite :aet] (:aet lahin-vastaava-projisio))
          (assoc-in [:tr-osoite :losa] (:losa lahin-vastaava-projisio))
          (assoc-in [:tr-osoite :let] (:let lahin-vastaava-projisio))))
    (do
      (log/debug (str "Ei voitu projisoida ramppimerkintää tielle: " projisoitava-tie))
      ;; Poistetaan merkinnältä projisoitu osoite. Tarkastusreittimuunnin olettaa merkinnän olevan
      ;; osa samaa tietä niin kauan kunnes oikea osoite löytyy.
      ;; Merkintöjä ei kuitenkaan sovi poistaa, sillä muuten saatetaan menettää havaintoja / mittauksia.
      (dissoc merkinta :tr-osoite))))

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

(defn- merkinnat-korjatulla-rampilla
  "Korvaa indeksistä eteenpäin löytyvät merkinnät annetuilla ramppimerkinnöillä"
  [kaikki-merkinnat ramppi-indeksi ramppimerkinnat]
  (let [merkinnat-ennen-ramppia (take ramppi-indeksi kaikki-merkinnat)
        merkinnat-rampin-jalkeen (drop (+ ramppi-indeksi (count ramppimerkinnat)) kaikki-merkinnat)]
    (vec (concat
           merkinnat-ennen-ramppia
           ramppimerkinnat
           merkinnat-rampin-jalkeen))))

(defn- korjaa-vahapatoinen-ramppi
  [merkinnat-ramppitiedoilla ramppi-indeksi n]
  (log/debug "Analysoidaan mahdollisesti vähäpätöinen ramppi indeksissä: "
             ramppi-indeksi "(" (:tr-osoite (nth merkinnat-ramppitiedoilla ramppi-indeksi)) ")")
  (if (= ramppi-indeksi 0)
    merkinnat-ramppitiedoilla ;; Merkinnät alkavat rampilta, ei tehdä mitään.
    (let [rampin-merkinnat (rampin-merkinnat-indeksista merkinnat-ramppitiedoilla ramppi-indeksi)
          korjattu-ramppi (if (< (count rampin-merkinnat) n)
                            (projisoi-ramppi-oikealle-tielle (nth merkinnat-ramppitiedoilla
                                                                  (dec ramppi-indeksi))
                                                             rampin-merkinnat)
                            rampin-merkinnat)]
      (merkinnat-korjatulla-rampilla merkinnat-ramppitiedoilla ramppi-indeksi korjattu-ramppi))))

(defn- korjaa-vahapatoiset-rampit
  "Ottaa reittimerkinnät ja analysoi kaikki rampille siirtymiset.
   Mikäli rampilla ajo sisältää vähemmän kuin N pistettä, projisoi rampin takaisin edeltävälle tielle.
   Muussa tapauksessa ei tee rampille mitään.

   Palauttaa kaikki merkinnät, vähäpätöiset ramppiosat on korjattu."
  [merkinnat n]
  (let [merkinnat-ramppitiedoilla (lisaa-merkintoihin-ramppitiedot merkinnat)
        alkavien-ramppien-indeksit (maarittele-alkavien-ramppien-indeksit merkinnat-ramppitiedoilla)
        ;; Käydään jokainen rampille siirtymä erikseen läpi ja korjataan tarvittaessa.
        korjatut-rampit (reduce (fn [edellinen-tulos seuraava-indeksi]
                                  (korjaa-vahapatoinen-ramppi edellinen-tulos seuraava-indeksi n))
                                merkinnat-ramppitiedoilla
                                alkavien-ramppien-indeksit)]
    korjatut-rampit))

(defn- merkinnat-erkanevat-rampilla-kauas?
  "Palauttaa true jos rampin merkinnät erkanavat ramppia edeltävästä tiestä niin kauas,
   että merkintöjä voidaan pitää luotettavana ja ajo tapahtui oikeasti rampilla."
  [ramppia-edeltava-merkinta rampin-merkinnat treshold]
  (let [edellisen-merkinnan-tie (get-in ramppia-edeltava-merkinta [:tr-osoite :tie])]
    (doseq [merkinta rampin-merkinnat]
      (let [lahin-osuma-edellisella-tiella (laheisten-pisteiden-lahin-osuma-tielle merkinta edellisen-merkinnan-tie)]
        (log/debug "Lähin edellisellä tiellä (" edellisen-merkinnan-tie "): " (pr-str lahin-osuma-edellisella-tiella)))))

  ;; TODO KESKEN, palauta nyt vain false
  false)

(defn- korjaa-rampilla-ajo
  [merkinnat-ramppitiedoilla ramppi-indeksi treshold]
  (log/debug "Analysoidaan mahdollisesti virheellinen rampilla ajo indeksissä: "
             ramppi-indeksi "(" (:tr-osoite (nth merkinnat-ramppitiedoilla ramppi-indeksi)) ")")
  (if (= ramppi-indeksi 0)
    merkinnat-ramppitiedoilla ;; Merkinnät alkavat rampilta, ei tehdä mitään.
    (let [rampin-merkinnat (rampin-merkinnat-indeksista merkinnat-ramppitiedoilla ramppi-indeksi)
          korjattu-ramppi (let [ramppia-edeltava-merkinta (nth merkinnat-ramppitiedoilla
                                                               (dec ramppi-indeksi))]
                            (if-not (merkinnat-erkanevat-rampilla-kauas? ramppia-edeltava-merkinta
                                                                         rampin-merkinnat
                                                                         treshold)
                              (projisoi-ramppi-oikealle-tielle ramppia-edeltava-merkinta
                                                               rampin-merkinnat)
                              rampin-merkinnat))]
      (merkinnat-korjatulla-rampilla merkinnat-ramppitiedoilla ramppi-indeksi korjattu-ramppi))))

(defn- korjaa-rampilla-ajot
  "Ottaa reittimerkinnät ja analysoi kaikki rampille siirtymiset.
   Mikäli pisteet erkanevat rampilla riittävän kauas (treshold) edellisestä tiestä, tulkitaan
   ajon käyneen rampilla. Mikäli kuitenkaan selkeää erkanemista ei löydy, merkinnät projisoidaan
   takaisin ramppia edeltävälle tielle.

   Palauttaa kaikki merkinnät, joissa erkanevat ramppiosat on korjattu."
  [merkinnat treshold]
  (let [merkinnat-ramppitiedoilla (lisaa-merkintoihin-ramppitiedot merkinnat)
        alkavien-ramppien-indeksit (maarittele-alkavien-ramppien-indeksit merkinnat-ramppitiedoilla)
        ;; Käydään jokainen rampille siirtymä erikseen läpi ja korjataan tarvittaessa.
        korjatut-rampit (reduce (fn [edellinen-tulos seuraava-indeksi]
                                  (korjaa-rampilla-ajo edellinen-tulos seuraava-indeksi treshold))
                                merkinnat-ramppitiedoilla
                                alkavien-ramppien-indeksit)]
    korjatut-rampit))

(defn- projisoi-virheelliset-rampit-uudelleen
  "Projisoi virheelliset rampit seuraavasti:
  - Jos rampilla ajoa on erittäin pieni osuus, projisoi rampin takaisin tielle, josta rampille ajettiin"
  [merkinnat]
  (log/debug "Projisoidaan virheelliset rampit uudelleen")
  (log/debug "Rampeille siirtymisiä havaittu: "
             (count (-> merkinnat
                        (lisaa-merkintoihin-ramppitiedot)
                        (maarittele-alkavien-ramppien-indeksit))) "kpl.")
  (as-> merkinnat m
        ;; Vain muutama piste rampilla -> projisoi uudelleen
        (korjaa-vahapatoiset-rampit m 5)
        ;; Pisteet erkanevat rampille, mutta eivät liian kauemmas -> projisoi uudelleen
        (korjaa-rampilla-ajot m 20)))

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
  (let [korjatut-merkinnat (projisoi-virheelliset-rampit-uudelleen merkinnat)]
    (mapv #(dissoc % :piste-rampilla?) korjatut-merkinnat)))