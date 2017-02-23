(ns harja-laadunseuranta.tarkastusreittimuunnin.virheelliset-tiet
  "Projisoi tien pisteet edelliselle tielle, jos tielle on osunut vain pieni määrä
  pisteitä ja ne kaikki ovat lähellä edellistä tietä. Tarkoituksena korjata
  tilanteet, joissa muutama yksittäinen piste osuu eri tielle esim. siltojen
  ja risteysten kohdalla"
  (:require [taoensso.timbre :as log]
            [harja.math :as math]
            [harja-laadunseuranta.tarkastusreittimuunnin.yhteiset :as yhteiset]))

(defn- tien-merkinnat-indeksista
  "Palauttaa indeksistä eteenpäin ne merkinnät, jotka ovat osa samaa tietä ajoa"
  [merkinnat tie-indeksi]
  (let [tien-ensimmainen-merkinta (nth merkinnat tie-indeksi)
        indeksin-jalkeiset-merkinnat (last (split-at tie-indeksi merkinnat))
        ;; Kerää tien merkintöjä eteenpäin niin kauan kunnes ei olla enää samalla tiellä
        tien-merkinnat (reduce (fn [merkinnat seuraava-merkinta]
                                 (if (= (get-in seuraava-merkinta [:tr-osoite :tie])
                                        (get-in tien-ensimmainen-merkinta [:tr-osoite :tie]))
                                   (conj merkinnat seuraava-merkinta)
                                   (reduced merkinnat)))
                               []
                               indeksin-jalkeiset-merkinnat)]
    tien-merkinnat))

(defn- tieosien-vaihtumisen-indeksit
  "Palauttaa indeksit merkintöihin, joissa siirrytään uudelle tielle."
  [merkinnat]
  (let [tulos (reduce (fn [tulos seuraava]
                        (let [edellinen-tie (get-in tulos [:edellinen-piste :tr-osoite :tie])
                              nykyinen-tie (get-in seuraava [:tr-osoite :tie])]
                          (assoc tulos :edellinen-piste seuraava
                                       :kasiteltava-indeksi (inc (:kasiteltava-indeksi tulos))
                                       :teiden-alut (if (not= edellinen-tie nykyinen-tie)
                                                      (conj (:teiden-alut tulos) (:kasiteltava-indeksi tulos))
                                                      (:teiden-alut tulos)))))
                      {:teiden-alut []
                       :edellinen-piste nil
                       :kasiteltava-indeksi 0}
                      merkinnat)]
    (:teiden-alut tulos)))

(defn- tien-merkinnat-todennakoisesti-virheelliset? [edellinen-merkinta tien-merkinnat
                                                     pisteet-threshold laheisyys-threshold]
  (let [edellinen-tie (get-in edellinen-merkinta [:tr-osoite :tie])
        merkinnat-alittavat-thresholdin? (< (count tien-merkinnat) pisteet-threshold)
        merkintojen-etaisyys-edeltavaan-tiehen (keep
                                                 #(:etaisyys-gps-pisteesta
                                                    (yhteiset/laheisten-teiden-lahin-osuma-tielle
                                                      % edellinen-tie))
                                                 tien-merkinnat)
        merkinnat-lahella-edellista-tieta (when-not (empty? merkintojen-etaisyys-edeltavaan-tiehen)
                                            (every? #(< % laheisyys-threshold)
                                                    merkintojen-etaisyys-edeltavaan-tiehen))
        todennakoisesti-virheellinen-projisio? (boolean (and merkinnat-alittavat-thresholdin?
                                                             merkinnat-lahella-edellista-tieta))]
    todennakoisesti-virheellinen-projisio?))

(defn- korjaa-virheellinen-tie [merkinnat tie-indeksi pisteet-threshold laheisyys-threshold]
  (if (= tie-indeksi 0) ;; Ensimmäinen tie, ei tarvi käsittelyä
    merkinnat
    (let [edellinen-merkinta (nth merkinnat (dec tie-indeksi))
          tien-merkinnat (tien-merkinnat-indeksista merkinnat tie-indeksi)
          korjattu-tie (if (tien-merkinnat-todennakoisesti-virheelliset? edellinen-merkinta
                                                                         tien-merkinnat
                                                                         pisteet-threshold
                                                                         laheisyys-threshold)
                         (yhteiset/projisoi-merkinnat-edelliselle-tielle
                           edellinen-merkinta
                           tien-merkinnat)
                         tien-merkinnat)]
      (yhteiset/merkinnat-korjatulla-osalla merkinnat tie-indeksi korjattu-tie))))

(defn- projisoi-virheelliset-tiet-uudelleen
  "Ottaa reittimerkinnät ja analysoi teiden vaihtumiset.
   Mikäli löytyy tieosa, jolla on vähemmän pisteitä kuin annettu pisteet-threshold ja
   niiden etäisyys edellisestä tiestä on pienempi kuin annettu laheisyys-threshold,
   merkinnät projisoidaan takaisin edeltävälle tielle.

   Palauttaa kaikki merkinnät, joissa virheelliset tieosat on korjattu."
  [merkinnat pisteet-threshold laheisyys-threshold]
  (let [teiden-alkujen-indeksit (tieosien-vaihtumisen-indeksit merkinnat)
        korjatut-tiet (reduce (fn [edellinen-tulos seuraava-indeksi]
                                (korjaa-virheellinen-tie edellinen-tulos
                                                         seuraava-indeksi
                                                         pisteet-threshold
                                                         laheisyys-threshold))
                              merkinnat
                              teiden-alkujen-indeksit)]
    korjatut-tiet))

(defn korjaa-virheelliset-tiet
  "Projisoi tien pisteet edelliselle tielle, jos tielle on osunut vain pieni määrä
  pisteitä ja ne kaikki ovat lähellä edellistä tietä. Tarkoituksena korjata
  tilanteet, joissa muutama yksittäinen piste osuu eri tielle esim. siltojen
  ja risteysten kohdalla"
  [merkinnat]
  (log/debug "Korjataan tarkastusajon virheelliset tieosoitteet. Merkintöjä: " (count merkinnat))
  (let [korjatut-merkinnat (projisoi-virheelliset-tiet-uudelleen merkinnat 5 8)]
    korjatut-merkinnat))