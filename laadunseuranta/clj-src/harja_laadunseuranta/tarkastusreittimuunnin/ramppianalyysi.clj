(ns harja-laadunseuranta.tarkastusreittimuunnin.ramppianalyysi
  "Tämä namespace tarjoaa funktion laadunseurannan mobiilityökalulla tehtyjen
   reittimerkintöjen ja niihin liittyvän geometrisoinnin korjaamiseen, mikäli
   geometrisointi on osunut virheellisesti rampille."
  (:require [taoensso.timbre :as log]
            [harja-laadunseuranta.tarkastusreittimuunnin.yhteiset :as yhteiset]
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
  (log/debug "Analysoidaan mahdollisesti vähäpätöinen ramppi indeksissä: "
             ramppi-indeksi "(" (:tr-osoite (nth merkinnat-ramppitiedoilla ramppi-indeksi)) ")")
  (if (= ramppi-indeksi 0)
    merkinnat-ramppitiedoilla ;; Merkinnät alkavat rampilta, ei tehdä mitään.
    (let [rampin-merkinnat (rampin-merkinnat-indeksista merkinnat-ramppitiedoilla ramppi-indeksi)
          ramppia-edeltava-merkinta (nth merkinnat-ramppitiedoilla (dec ramppi-indeksi))
          korjattu-ramppi (if (< (count rampin-merkinnat) n)
                            (yhteiset/projisoi-merkinnat-edelliselle-tielle
                              ramppia-edeltava-merkinta
                              rampin-merkinnat)
                            rampin-merkinnat)]
      (yhteiset/merkinnat-korjatulla-osalla merkinnat-ramppitiedoilla ramppi-indeksi korjattu-ramppi))))

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

(defn- merkinnat-todennakoisesti-ajettu-rampilla?
  "Palauttaa true jos rampin merkinnät selkeästi erkanevat ramppia edeltävästä tiestä niin kauas,
   että merkintöjä voidaan pitää luotettavana ja ajo tapahtui oikeasti rampilla.

   treshold on metrimäärä rampin alkupisteestä, jota käytetään rajana määrittämään luotettava
   rampille siirtyminen."
  [ramppia-edeltava-merkinta rampin-merkinnat threshold]
  (let [tie-ennen-ramppia (get-in ramppia-edeltava-merkinta [:tr-osoite :tie])
        merkinnat-rampilla-yli-tresholdin
        ;; Tutkitaan rampin reittimerkintöjen etäisyys ramppia edeltäneeseen tiehen.
        ;; Pyritään löytämään pisteet, joissa etäisyys edellisestä tiestä ylittää thresholdin niin,
        ;; että myös GPS:n epätarkkuus on huomioitu.
        (filter #(let [lahin-osuma-edelliselle-tielle
                       (yhteiset/laheisten-teiden-lahin-osuma-tielle % tie-ennen-ramppia)]
                   (and lahin-osuma-edelliselle-tielle
                        (>
                          ;; GPS:n epätarkkuudesta johtuen projisio edelliselle tielle voi olla lähempänä
                          ;; kuin suoraan pisteestä laskettu etäisyys. Siispä vähennetään
                          ;; etäisyydestä GPS:n aiheuttama epätarkkuus.
                          ;; Eli jos epätarkkuus on suuri, pisteen tulee olla hyvin kaukana
                          ;; projisioidusta tiestä, jotta uskomme sen ylittävän thresholdin.
                          (max (- (:etaisyys-gps-pisteesta lahin-osuma-edelliselle-tielle)
                                  (:gps-tarkkuus %))
                               0)
                          threshold)))
                rampin-merkinnat)
        ainakin-yksi-varma-piste-ylittaa-thresholdin? (> (count merkinnat-rampilla-yli-tresholdin) 0)]

    (when ainakin-yksi-varma-piste-ylittaa-thresholdin?
      (log/debug "Löytyi pisteitä, jotka sijaitsevat riittävän kaukana ramppia edeltävästä tiestä."))

    ainakin-yksi-varma-piste-ylittaa-thresholdin?))

(defn- korjaa-rampilla-ajo
  [merkinnat-ramppitiedoilla ramppi-indeksi threshold]
  (log/debug "Analysoidaan mahdollisesti virheellinen rampilla ajo indeksissä: "
             ramppi-indeksi "(" (:tr-osoite (nth merkinnat-ramppitiedoilla ramppi-indeksi)) ")")
  (if (= ramppi-indeksi 0)
    merkinnat-ramppitiedoilla ;; Merkinnät alkavat rampilta, ei tehdä mitään.
    (let [rampin-merkinnat (rampin-merkinnat-indeksista merkinnat-ramppitiedoilla ramppi-indeksi)
          ramppia-edeltava-merkinta (nth merkinnat-ramppitiedoilla (dec ramppi-indeksi))
          korjattu-ramppi (if (merkinnat-todennakoisesti-ajettu-rampilla? ramppia-edeltava-merkinta
                                                                          rampin-merkinnat
                                                                          threshold)
                            rampin-merkinnat
                            (yhteiset/projisoi-merkinnat-edelliselle-tielle
                              ramppia-edeltava-merkinta
                              rampin-merkinnat))]
      (yhteiset/merkinnat-korjatulla-osalla merkinnat-ramppitiedoilla ramppi-indeksi korjattu-ramppi))))

(defn- korjaa-rampilla-ajot
  "Ottaa reittimerkinnät ja analysoi kaikki rampille siirtymiset.
   Mikäli pisteet erkanevat rampilla riittävän kauas (threshold) edellisestä tiestä, tulkitaan
   ajon käyneen rampilla. Mikäli kuitenkaan selkeää erkanemista ei löydy, merkinnät projisoidaan
   takaisin ramppia edeltävälle tielle.

   Palauttaa kaikki merkinnät, joissa erkanevat ramppiosat on korjattu."
  [merkinnat threshold]
  (let [merkinnat-ramppitiedoilla (lisaa-merkintoihin-ramppitiedot merkinnat)
        alkavien-ramppien-indeksit (maarittele-alkavien-ramppien-indeksit merkinnat-ramppitiedoilla)
        ;; Käydään jokainen rampille siirtymä erikseen läpi ja korjataan tarvittaessa.
        korjatut-rampit (reduce (fn [edellinen-tulos seuraava-indeksi]
                                  (korjaa-rampilla-ajo edellinen-tulos seuraava-indeksi threshold))
                                merkinnat-ramppitiedoilla
                                alkavien-ramppien-indeksit)]
    korjatut-rampit))

(defn- projisoi-virheelliset-rampit-uudelleen
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
        (korjaa-rampilla-ajot m 40)))

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